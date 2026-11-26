package io.github.lonemoonspace.dayloom.feature.weather.domain

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/** What the sun does on one day at one place. / 某地某天太阳的情况。 */
sealed interface SunDay {
    /** It rises and sets. / 有日出也有日落。 */
    data class Rises(val sunrise: ZonedDateTime, val sunset: ZonedDateTime) : SunDay {
        val length: Duration get() = Duration.between(sunrise, sunset)
    }

    /** Polar night: it stays below the horizon all day. / 极夜：整天都在地平线以下。 */
    data object Down : SunDay

    /** Midnight sun: it stays above the horizon all day. / 极昼：整天都在地平线以上。 */
    data object Up : SunDay
}

/**
 * The daylight line of the weather card. [changeMinutes] is today's length minus yesterday's (negative = shorter), null
 * when either day has no sunrise. [until] is, for polar night, the first day the sun rises again and, for midnight sun, the
 * first day it sets again; null when that is more than half a year away.
 * 天气卡上的日照一行。[changeMinutes] 是今天比昨天长多少分钟（负数 = 短），任一天没有日出日落时为 null。[until] 在极夜时是太阳
 * 重新升起的第一天，极昼时是重新落下的第一天；超过半年时为 null。
 */
data class Daylight(val day: SunDay, val changeMinutes: Int? = null, val until: LocalDate? = null)

/**
 * Sunrise and sunset worked out on the phone (NOAA's solar equations, about a minute from published tables), so they need
 * no network and are there for any place and day. Sunrise is when the top of the sun meets the horizon with average
 * refraction, the usual almanac definition.
 * 在手机上算日出日落（NOAA 的太阳公式，与公开的日出表相差一分钟左右），所以不需要网络，任何地点、任何日期都有。日出指日面上缘
 * 在平均大气折射下与地平线相切的时刻，即通常历书的定义。
 */
object SunPolicy {

    fun daylight(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): Daylight {
        val today = day(date, lat, lon, zone)
        return when (today) {
            is SunDay.Rises -> {
                val yesterday = day(date.minusDays(1), lat, lon, zone) as? SunDay.Rises
                val change = yesterday?.let { minutes(today.length) - minutes(it.length) }
                Daylight(today, change)
            }
            SunDay.Down, SunDay.Up -> Daylight(today, until = firstChange(date, lat, lon, zone, today))
        }
    }

    fun day(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): SunDay {
        // Two passes: the first estimates the times from local solar noon, the second recomputes the sun's position at them.
        // 两遍：第一遍用当地正午的太阳位置估算时刻，第二遍在估出的时刻重新算太阳位置。
        val noon = SOLAR_NOON_MINUTES - 4 * lon
        val first = event(date, lat, lon, noon, rising = true) ?: return polar(date, lat, lon, noon)
        val rise = event(date, lat, lon, first, rising = true) ?: return polar(date, lat, lon, noon)
        val firstSet = event(date, lat, lon, noon, rising = false) ?: return polar(date, lat, lon, noon)
        val set = event(date, lat, lon, firstSet, rising = false) ?: return polar(date, lat, lon, noon)
        return SunDay.Rises(at(date, rise, zone), at(date, set, zone))
    }

    /** Whole minutes, rounded, so the change matches the lengths shown. / 取整到分钟，变化量与显示的时长一致。 */
    fun minutes(length: Duration): Int = (length.seconds / 60.0).roundToInt()

    private fun firstChange(date: LocalDate, lat: Double, lon: Double, zone: ZoneId, kind: SunDay): LocalDate? =
        (1L..MAX_SEARCH_DAYS).asSequence()
            .map { date.plusDays(it) }
            .firstOrNull { day(it, lat, lon, zone) != kind }

    private fun polar(date: LocalDate, lat: Double, lon: Double, noonMinutes: Double): SunDay {
        val decl = Sun.at(date, noonMinutes).declination
        // Above the horizon at noon means up all day; below means down all day. / 正午在地平线以上就是整天都在上面，反之整天在下面。
        val noonAltitude = 90 - abs(lat - Math.toDegrees(decl))
        return if (noonAltitude > HORIZON_ALTITUDE) SunDay.Up else SunDay.Down
    }

    /** Minutes after the UTC midnight of [date], or null when the sun does not cross the horizon. / [date] 的 UTC 零点之后的分钟数；太阳不过地平线时为 null。 */
    private fun event(date: LocalDate, lat: Double, lon: Double, aroundMinutes: Double, rising: Boolean): Double? {
        val sun = Sun.at(date, aroundMinutes)
        val latRad = Math.toRadians(lat)
        val cosHourAngle = (cos(Math.toRadians(90 - HORIZON_ALTITUDE)) / (cos(latRad) * cos(sun.declination))) -
            tan(latRad) * tan(sun.declination)
        if (cosHourAngle !in -1.0..1.0) return null
        val hourAngle = Math.toDegrees(acos(cosHourAngle))
        val noon = SOLAR_NOON_MINUTES - 4 * lon - sun.equationOfTime
        return if (rising) noon - 4 * hourAngle else noon + 4 * hourAngle
    }

    private fun at(date: LocalDate, minutes: Double, zone: ZoneId): ZonedDateTime =
        date.atStartOfDay(ZoneOffset.UTC).plusSeconds((minutes * 60).roundToLong()).withZoneSameInstant(zone)

    /** The sun's declination (radians) and the equation of time (minutes). / 太阳赤纬（弧度）与时差（分钟）。 */
    private data class Sun(val declination: Double, val equationOfTime: Double) {
        companion object {
            fun at(date: LocalDate, minutes: Double): Sun {
                val julianDay = date.toEpochDay() + EPOCH_JULIAN_DAY + minutes / MINUTES_PER_DAY
                val t = (julianDay - J2000) / DAYS_PER_CENTURY
                val meanLongitude = Math.toRadians((280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0))
                val meanAnomaly = Math.toRadians(357.52911 + t * (35999.05029 - 0.0001537 * t))
                val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
                val center = sin(meanAnomaly) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
                    sin(2 * meanAnomaly) * (0.019993 - 0.000101 * t) + sin(3 * meanAnomaly) * 0.000289
                val omega = Math.toRadians(125.04 - 1934.136 * t)
                val apparentLongitude = Math.toRadians(Math.toDegrees(meanLongitude) + center - 0.00569 - 0.00478 * sin(omega))
                val meanObliquity = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
                val obliquity = Math.toRadians(meanObliquity + 0.00256 * cos(omega))
                val declination = asin(sin(obliquity) * sin(apparentLongitude))
                val y = tan(obliquity / 2).let { it * it }
                val equation = y * sin(2 * meanLongitude) - 2 * eccentricity * sin(meanAnomaly) +
                    4 * eccentricity * y * sin(meanAnomaly) * cos(2 * meanLongitude) -
                    0.5 * y * y * sin(4 * meanLongitude) - 1.25 * eccentricity * eccentricity * sin(2 * meanAnomaly)
                return Sun(declination, 4 * Math.toDegrees(equation))
            }
        }
    }

    /** The sun's centre this far below the horizon (radius plus refraction) is sunrise. / 太阳中心低于地平线这么多度（半径加折射）即为日出。 */
    private const val HORIZON_ALTITUDE = -0.833
    private const val SOLAR_NOON_MINUTES = 720.0
    private const val MINUTES_PER_DAY = 1440.0
    private const val EPOCH_JULIAN_DAY = 2440587.5
    private const val J2000 = 2451545.0
    private const val DAYS_PER_CENTURY = 36525.0

    /** Polar night and midnight sun never last more than half a year. / 极夜与极昼都不会超过半年。 */
    private const val MAX_SEARCH_DAYS = 190L
}
