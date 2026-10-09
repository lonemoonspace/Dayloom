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
}
