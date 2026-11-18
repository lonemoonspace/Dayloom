package io.github.lonemoonspace.dayloom.feature.weather

import io.github.lonemoonspace.dayloom.feature.weather.domain.DailyForecastBuilder
import io.github.lonemoonspace.dayloom.feature.weather.domain.ForecastPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyForecastBuilderTest {

    private val oslo = ZoneId.of("Europe/Oslo")

    private fun point(time: String, temp: Double, symbol6: String = "", precip6: Double? = null) = ForecastPoint(
        time = Instant.parse(time).toEpochMilli(),
        temperature = temp,
        symbol6h = symbol6,
        precipitation6h = precip6,
    )

    private fun build(points: List<ForecastPoint>, today: LocalDate) = DailyForecastBuilder.build(points, today, oslo)

    @Test
    fun `groups by local date with min max and noon symbol`() {
        // September is UTC+2: UTC 22:00 is 00:00 the next day in Oslo. / 9 月为夏令时（UTC+2）：UTC 22:00 是奥斯陆次日 00:00。
        val series = listOf(
            point("2026-09-21T22:00:00Z", 6.0, "cloudy_night", 0.5),
            point("2026-09-22T04:00:00Z", 4.0, "fog", 0.0),
            point("2026-09-22T10:00:00Z", 12.0, "rain", 2.0),
            point("2026-09-22T16:00:00Z", 9.0, "clearsky_day", 1.0),
        )
        val day = build(series, LocalDate.of(2026, 9, 21)).single()
        assertEquals(LocalDate.of(2026, 9, 22), day.date)
        assertEquals(4.0, day.minTemp, 0.0)
        assertEquals(12.0, day.maxTemp, 0.0)
        assertEquals("rain", day.symbolCode)
        // None of the four starts at UTC 0/6/12/18, so no precipitation counts. / 四个点都不在 UTC 0/6/12/18 时起算，降水不计。
        assertEquals(0.0, day.precipMm, 0.0)
    }

    @Test
    fun `precip sums non overlapping six hour windows`() {
        val series = listOf(
            point("2026-09-22T00:00:00Z", 5.0, "rain", 1.0),
            // Overlaps the previous window, not counted. / 与上一窗口重叠，不计。
            point("2026-09-22T01:00:00Z", 5.0, "rain", 9.0),
            point("2026-09-22T06:00:00Z", 8.0, "rain", 2.0),
            point("2026-09-22T12:00:00Z", 10.0, "cloudy", 0.5),
        )
        assertEquals(3.5, build(series, LocalDate.of(2026, 9, 22)).single().precipMm, 1e-9)
    }

    @Test
    fun `six-hour extremes widen the range beyond the hourly forecast, in the day that holds most of the window`() {
        fun sixHourly(time: String, temp: Double, min: Double, max: Double) = point(time, temp, "cloudy").copy(temperatureMin6h = min, temperatureMax6h = max)
        val series = listOf(
            sixHourly("2026-09-24T00:00:00Z", 6.0, min = 3.5, max = 6.5),
            sixHourly("2026-09-24T06:00:00Z", 5.0, min = 5.0, max = 12.0),
            sixHourly("2026-09-24T12:00:00Z", 12.0, min = 9.0, max = 14.5),
            // UTC 18:00 is 20:00 → 02:00 in Oslo: four of its six hours are on the 24th. / UTC 18:00 在奥斯陆是 20:00 → 02:00：6 小时里有 4 小时在 24 日。
            sixHourly("2026-09-24T18:00:00Z", 9.0, min = 2.0, max = 9.0),
        )
        val day = build(series, LocalDate.of(2026, 9, 24)).single { it.date == LocalDate.of(2026, 9, 24) }
        assertEquals(2.0, day.minTemp, 0.0)
        assertEquals(14.5, day.maxTemp, 0.0)

        // An hourly point's six-hour figures reach into hours its neighbours already cover, so they are left out.
        // 逐小时点的 6 小时数值伸到了相邻的点已经覆盖的时段，所以不用。
        val hourly = series.map { it.copy(symbol1h = "cloudy", precipitation1h = 0.0) }
        val plain = build(hourly, LocalDate.of(2026, 9, 24)).single { it.date == LocalDate.of(2026, 9, 24) }
        assertEquals(5.0, plain.minTemp, 0.0)
        assertEquals(12.0, plain.maxTemp, 0.0)
    }

    @Test
    fun `limits to requested days and skips past days`() {
        val series = (20..30).map { day -> point("2026-09-${day}T10:00:00Z", day.toDouble(), "cloudy") }
        assertEquals(
            (22..26).map { LocalDate.of(2026, 9, it) },
            build(series, LocalDate.of(2026, 9, 22)).map { it.date },
        )
    }

    @Test
    fun `falls back to the one hour symbol and handles an empty series`() {
        val p = ForecastPoint(time = Instant.parse("2026-09-22T10:00:00Z").toEpochMilli(), temperature = 7.0, symbol1h = "lightrain")
        assertEquals("lightrain", build(listOf(p), LocalDate.of(2026, 9, 22)).single().symbolCode)
        assertEquals(emptyList<Any>(), build(emptyList(), LocalDate.of(2026, 9, 22)))
    }

    @Test
    fun `skips days without any temperature`() {
        val p = ForecastPoint(time = Instant.parse("2026-09-22T10:00:00Z").toEpochMilli(), symbol6h = "rain")
        assertEquals(emptyList<Any>(), build(listOf(p), LocalDate.of(2026, 9, 22)))
    }

    @Test
    fun `splits six hour windows that cross midnight across the two days`() {
        // The window from UTC 18:00 is 20:00 → 02:00 in Oslo: 4/6 and 2/6. / UTC 18:00 起的窗口是奥斯陆 20:00 → 次日 02:00：4/6 与 2/6。
        val series = listOf(
            point("2026-09-22T10:00:00Z", 12.0, "rain", 0.0),
            point("2026-09-22T18:00:00Z", 9.0, "rain", 6.0),
            point("2026-09-23T10:00:00Z", 13.0, "rain", 0.0),
        )
        val byDate = build(series, LocalDate.of(2026, 9, 22)).associateBy { it.date }
        assertEquals(4.0, byDate.getValue(LocalDate.of(2026, 9, 22)).precipMm, 1e-9)
        assertEquals(2.0, byDate.getValue(LocalDate.of(2026, 9, 23)).precipMm, 1e-9)
    }

    @Test
    fun `chance of rain is the highest hourly or six-hour chance of the day`() {
        fun hourly(time: String, chance: Double) = point(time, 8.0).copy(symbol1h = "cloudy", precipitation1h = 0.0, precipProbability1h = chance)
        fun sixHourly(time: String, chance: Double) = point(time, 8.0, "rain", 1.0).copy(precipProbability6h = chance)
        val series = listOf(
            // 22 Sep, hourly: the six-hour chance of an hourly point is ignored. / 9 月 22 日，逐小时：逐小时点上的 6 小时概率不计。
            hourly("2026-09-22T06:00:00Z", 12.4).copy(precipProbability6h = 90.0),
            hourly("2026-09-22T07:00:00Z", 41.6),
            // 23 Sep, six-hourly only. / 9 月 23 日，只有 6 小时数据。
            sixHourly("2026-09-23T00:00:00Z", 20.0),
            sixHourly("2026-09-23T06:00:00Z", 63.0),
            sixHourly("2026-09-23T12:00:00Z", 10.0),
            // 24 Sep has a temperature but no chance at all (the compact fallback). / 9 月 24 日有温度但没有任何概率（compact 兜底）。
            point("2026-09-24T10:00:00Z", 9.0, "cloudy"),
        )
        val byDate = build(series, LocalDate.of(2026, 9, 22)).associateBy { it.date }
        assertEquals(42, byDate.getValue(LocalDate.of(2026, 9, 22)).precipChance)
        assertEquals(63, byDate.getValue(LocalDate.of(2026, 9, 23)).precipChance)
        assertEquals(null, byDate.getValue(LocalDate.of(2026, 9, 24)).precipChance)
    }
}
