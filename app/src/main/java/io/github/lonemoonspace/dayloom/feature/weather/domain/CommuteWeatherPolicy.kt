package io.github.lonemoonspace.dayloom.feature.weather.domain

import io.github.lonemoonspace.dayloom.core.routine.Routine
import io.github.lonemoonspace.dayloom.core.routine.RoutinePolicy
import io.github.lonemoonspace.dayloom.core.routine.WindowKind
import java.time.Instant
import java.time.ZonedDateTime

/** The weather of one daily window. / 某个日常时间窗的天气判断。 */
data class LegWeather(
    val kind: WindowKind,
    /** Window start and end; once under way [start] stays the configured start, only rain is judged from now. / 时间窗起止；已开始时 [start] 仍是设置的起点，只有降水从现在算起。 */
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    /** Temperature in the first hour; null without hourly data. / 第一小时的气温；没有逐小时数据时为 null。 */
    val temperature: Double?,
    val symbolCode: String,
    /** Highest hourly precipitation inside the window, mm. / 时间窗内单小时降水的最大值，毫米。 */
    val maxPrecipMm: Double,
    val umbrella: Boolean,
    /** False when hourly data does not reach the window (old cache, truncated data): show the time, draw no conclusion. / 逐小时数据覆盖不到这段时间窗（旧缓存、数据截断）时为 false：只显示时间，不下结论。 */
    val hasData: Boolean,
)

/** When the rain stops or starts within the next hours. / 接下来十几个小时里雨什么时候停或开始。 */
sealed interface RainChange {
    val at: ZonedDateTime

    data class Stops(override val at: ZonedDateTime) : RainChange
    data class Starts(override val at: ZonedDateTime) : RainChange
}

data class CommuteWeather(val legs: List<LegWeather>, val rainChange: RainChange?)

/**
 * Commute weather: for the to-work and back-home windows take the next occurrence that has not ended and look at every
 * hour's precipitation in it. The umbrella threshold is 0.2 mm/h: below 0.1 MET is mostly drizzle or model noise, from 0.2
 * on you actually get wet.
 * 通勤天气判定：去程、返程各取「下一次还没结束的那段时间窗」，看其中每小时的降水。带伞门槛 0.2 mm/h：MET 低于 0.1 的量
 * 基本是毛毛雨或模型噪声，0.2 起才真会淋湿。
 */
object CommuteWeatherPolicy {
    const val UMBRELLA_MM = 0.2

    /** Rain changes further out say nothing about leaving now. / 再远的雨停雨来对「现在出不出门」没有意义。 */
    private const val RAIN_LOOKAHEAD_HOURS = 12L

    fun evaluate(points: List<ForecastPoint>, routine: Routine, now: ZonedDateTime): CommuteWeather {
        // Only points with a one-hour period: six-hour amounts must not be read as hourly. / 只取有一小时时段的点：6 小时降水量不能当一小时用。
        val hourly = points
            .filter { it.symbol1h.isNotEmpty() || it.precipitation1h != null }
            .map { Instant.ofEpochMilli(it.time).atZone(now.zone) to it }
            .sortedBy { it.first }
        val legs = WindowKind.entries.mapNotNull { leg(it, routine, hourly, now) }.sortedBy { it.start }
        return CommuteWeather(legs = legs, rainChange = rainChange(hourly, now))
    }

    private fun leg(kind: WindowKind, routine: Routine, points: List<Pair<ZonedDateTime, ForecastPoint>>, now: ZonedDateTime): LegWeather? {
        val occurrence = RoutinePolicy.next(routine, kind, now) ?: return null
        // Once under way only the rest counts: rain that already fell is no reason to take an umbrella.
        // 时间窗已经开始时只看剩下的时段：已经下过的雨不该再让人带伞。
        val from = maxOf(occurrence.start, now)
        val inWindow = points.filter { (t, _) -> t.isBefore(occurrence.end) && t.plusHours(1).isAfter(from) }
        val departure = inWindow.firstOrNull()?.second
        val wettest = inWindow.maxByOrNull { it.second.precipMm }?.second
        val maxPrecip = wettest?.precipMm ?: 0.0
        val umbrella = maxPrecip >= UMBRELLA_MM
        return LegWeather(
            kind = kind,
            start = occurrence.start,
            end = occurrence.end,
            temperature = departure?.temperature,
            // With an umbrella the icon follows the wettest hour, otherwise "sunny icon + umbrella" contradicts itself.
            // 要带伞时图标跟着最湿的那一小时走，否则「晴天图标 + 带伞」自相矛盾。
            symbolCode = (if (umbrella) wettest?.symbol1h else departure?.symbol1h).orEmpty(),
            maxPrecipMm = maxPrecip,
            umbrella = umbrella,
            hasData = inWindow.isNotEmpty(),
        )
    }

    private fun rainChange(points: List<Pair<ZonedDateTime, ForecastPoint>>, now: ZonedDateTime): RainChange? {
        val ahead = points.filter { (t, _) -> t.plusHours(1).isAfter(now) && t.isBefore(now.plusHours(RAIN_LOOKAHEAD_HOURS)) }
        val current = ahead.firstOrNull() ?: return null
        val rainingNow = current.second.precipMm >= UMBRELLA_MM
        val change = ahead.drop(1).firstOrNull { (_, p) -> (p.precipMm >= UMBRELLA_MM) != rainingNow } ?: return null
        return if (rainingNow) RainChange.Stops(change.first) else RainChange.Starts(change.first)
    }

    private val ForecastPoint.precipMm: Double get() = precipitation1h ?: 0.0
}
