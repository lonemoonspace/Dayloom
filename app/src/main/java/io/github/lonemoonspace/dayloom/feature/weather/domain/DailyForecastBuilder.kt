package io.github.lonemoonspace.dayloom.feature.weather.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs

/** One day's weather for the forecast strip. / 预报横排里某一天的天气。 */
data class DailyForecast(
    val date: LocalDate,
    val minTemp: Double,
    val maxTemp: Double,
    /** Daytime symbol (the point closest to 12:00). / 白天的符号（最接近 12:00 的点）。 */
    val symbolCode: String,
    /** Total precipitation of the day, mm. / 当天累计降水，毫米。 */
    val precipMm: Double,
)

/**
 * Groups MET's series (hourly for about two days, then every six hours) into days in the app's zone. The series starts at the
 * current hour, so today's low and high only cover the rest of today.
 * 把 MET 时间序列（前两天左右逐小时，之后每 6 小时）按应用时区的日期汇总成逐日预报。序列从当前小时开始，
 * 所以「今天」的最高/最低温只覆盖今天剩余的时间。
 */
object DailyForecastBuilder {
    const val DAYS = 5

    fun build(points: List<ForecastPoint>, today: LocalDate, zone: ZoneId, days: Int = DAYS): List<DailyForecast> {
        val lastDay = today.plusDays(days - 1L)
        val timed = points.map { Instant.ofEpochMilli(it.time).atZone(zone) to it }
        val byDate = timed.filter { (t, _) -> t.toLocalDate() in today..lastDay }.groupBy { (t, _) -> t.toLocalDate() }
        return byDate.toSortedMap().mapNotNull { (date, dayPoints) ->
            val temps = dayPoints.mapNotNull { (_, p) -> p.temperature }
            if (temps.isEmpty()) return@mapNotNull null
            // The six-hour symbol describes a period rather than one hour, so it is preferred.
            // 6 小时符号代表一段时间而非某一小时，所以优先用它。
            val symbol = dayPoints
                .mapNotNull { (t, p) -> p.symbol6h.ifEmpty { p.symbol1h }.takeIf { it.isNotEmpty() }?.let { abs(t.hour * 60 + t.minute - NOON) to it } }
                .minByOrNull { it.first }?.second.orEmpty()
            DailyForecast(
                date = date,
                minTemp = temps.min(),
                maxTemp = temps.max(),
                symbolCode = symbol,
                precipMm = precipitationForDay(timed, date, zone),
            )
        }
    }

    /**
     * Six-hour windows aligned to UTC 0/6/12/18 are split across local days by overlap: the window from UTC 18:00 is 20:00 →
     * 02:00 in Oslo and counts 4/6 and 2/6 towards the two days. Local days are 23 or 25 hours on DST changes, so overlap is
     * measured in instants. Only aligned windows count: the hourly points' next_6_hours overlap each other, aligned ones tile.
     * 「UTC 0/6/12/18 时起算的 6 小时窗口」按与本地日的重叠时长拆分：UTC 18:00 起的窗口在奥斯陆是 20:00 → 次日 02:00，
     * 按 4/6、2/6 分到相邻两天。夏令时切换日本地日是 23/25 小时，所以按 Instant 算重叠。只取对齐的窗口：逐小时点的
     * next_6_hours 互相重叠，对齐的才恰好铺满。
     */
    private fun precipitationForDay(points: List<Pair<ZonedDateTime, ForecastPoint>>, date: LocalDate, zone: ZoneId): Double {
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        return points.sumOf { (t, p) ->
            if (t.withZoneSameInstant(ZoneOffset.UTC).hour % 6 != 0) return@sumOf 0.0
            val amount = p.precipitation6h ?: return@sumOf 0.0
            val overlapMinutes = Duration.between(
                maxOf(t.toInstant(), dayStart),
                minOf(t.plusHours(6).toInstant(), dayEnd),
            ).toMinutes()
            if (overlapMinutes <= 0) 0.0 else amount * overlapMinutes / (6 * 60.0)
        }
    }

    private const val NOON = 12 * 60
}
