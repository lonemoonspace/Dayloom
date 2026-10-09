package io.github.lonemoonspace.dayloom.feature.weather

import io.github.lonemoonspace.dayloom.core.routine.DailyWindow
import io.github.lonemoonspace.dayloom.core.routine.Routine
import io.github.lonemoonspace.dayloom.core.routine.WindowKind
import io.github.lonemoonspace.dayloom.feature.weather.domain.CommuteWeatherPolicy
import io.github.lonemoonspace.dayloom.feature.weather.domain.ForecastPoint
import io.github.lonemoonspace.dayloom.feature.weather.domain.RainChange
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommuteWeatherPolicyTest {

    private val zone = ZoneId.of("Europe/Oslo")

    /** To work 07:00–10:00, back home 14:00–16:00, every day. / 去程 07:00–10:00，返程 14:00–16:00，每天。 */
    private val routine = Routine(
        toWork = DailyWindow(7 * 60, 10 * 60),
        backHome = DailyWindow(14 * 60, 16 * 60),
        workingDays = (1..7).toSet(),
    )

    private fun t(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): ZonedDateTime = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone)

    /** Thursday 2026-10-08. / 2026-10-08，周四。 */
    private fun day(h: Int, mi: Int = 0) = t(2026, 10, 8, h, mi)

    /**
     * One point per real hour from [from] (by instant, so DST change days are one hour apart too).
     * 从 [from] 起按真实时长逐小时生成点，夏令时切换日也是每点相隔一小时。
     */
    private fun hourly(from: ZonedDateTime, hours: Int, mm: (ZonedDateTime) -> Double): List<ForecastPoint> =
        (0 until hours).map { i ->
            val at = from.plusHours(i.toLong())
            ForecastPoint(
                time = at.toInstant().toEpochMilli(),
                temperature = 10.0 + i,
                symbol1h = if (mm(at) > 0) "rain" else "cloudy",
                precipitation1h = mm(at),
            )
        }

    @Test
    fun `rain inside the work window asks for an umbrella and a dry return does not`() {
        val points = hourly(day(6), 30) { if (it.hour == 8) 0.5 else 0.0 }

        val (work, ret) = CommuteWeatherPolicy.evaluate(points, routine, day(7, 30)).legs

        assertEquals(WindowKind.TO_WORK, work.kind)
        assertEquals(day(7), work.start)
        assertEquals(day(10), work.end)
        assertTrue(work.umbrella)
        assertEquals("rain", work.symbolCode)
        assertEquals(0.5, work.maxPrecipMm, 1e-9)
        assertEquals(WindowKind.BACK_HOME, ret.kind)
        assertFalse(ret.umbrella)
        assertTrue(ret.hasData)
    }

    @Test
    fun `departure temperature is the first hour of the window`() {
        val points = hourly(day(6), 30) { 0.0 }
        val work = CommuteWeatherPolicy.evaluate(points, routine, day(6, 30)).legs.first()
        // day(6) is 10°, the hour from 07:00 is 11°. / day(6) 是 10°，07:00 那一小时是 11°。
        assertEquals(11.0, work.temperature!!, 1e-9)
    }

    @Test
    fun `windows that already ended today move to tomorrow, sorted by start`() {
        val points = hourly(day(17), 30) { if (it.dayOfMonth == 9 && it.hour == 15) 1.2 else 0.0 }

        val (work, ret) = CommuteWeatherPolicy.evaluate(points, routine, day(17)).legs

        assertEquals(t(2026, 10, 9, 7), work.start)
        assertFalse(work.umbrella)
        assertEquals(t(2026, 10, 9, 14), ret.start)
        assertTrue(ret.umbrella)
    }

    @Test
    fun `non-working days are skipped`() {
        // Friday evening with Monday–Friday working days: the next to-work window is on Monday.
        // 周五晚上、工作日为周一到周五：下一个去程在周一。
        val weekdays = routine.copy(workingDays = (1..5).toSet())
        val work = CommuteWeatherPolicy.evaluate(emptyList(), weekdays, t(2026, 10, 9, 18)).legs.first { it.kind == WindowKind.TO_WORK }
        assertEquals(t(2026, 10, 12, 7), work.start)
        assertFalse(work.hasData)
    }

    @Test
    fun `rain that already fell earlier in a running window does not count`() {
        val points = hourly(day(6), 30) { if (it.hour == 7) 0.8 else 0.0 }

        val work = CommuteWeatherPolicy.evaluate(points, routine, day(8, 10)).legs.first()

        // The window still shows its configured start. / 显示的仍是设置里的时间窗。
        assertEquals(day(7), work.start)
        assertFalse(work.umbrella)
    }

    @Test
    fun `a window crossing midnight still belongs to yesterday in the small hours`() {
        val night = routine.copy(toWork = DailyWindow(22 * 60, 60))
        val points = hourly(day(0), 30) { if (it.hour == 0) 0.3 else 0.0 }

        val work = CommuteWeatherPolicy.evaluate(points, night, day(0, 30)).legs.first { it.kind == WindowKind.TO_WORK }

        assertEquals(t(2026, 10, 7, 22), work.start)
        assertEquals(day(1), work.end)
        assertTrue(work.umbrella)
    }

    @Test
    fun `umbrella threshold is 0_2 mm in an hour`() {
        val light = CommuteWeatherPolicy.evaluate(hourly(day(6), 30) { if (it.hour == 8) 0.1 else 0.0 }, routine, day(6))
        val wet = CommuteWeatherPolicy.evaluate(hourly(day(6), 30) { if (it.hour == 8) 0.2 else 0.0 }, routine, day(6))
        assertFalse(light.legs.first().umbrella)
        assertTrue(wet.legs.first().umbrella)
    }

    @Test
    fun `reports when the current rain stops`() {
        val points = hourly(day(6), 30) { if (it.hour < 9) 0.6 else 0.0 }
        assertEquals(RainChange.Stops(day(9)), CommuteWeatherPolicy.evaluate(points, routine, day(7, 40)).rainChange)
    }

    @Test
    fun `reports when rain starts and stays quiet beyond the lookahead`() {
        val soon = hourly(day(6), 30) { if (it.hour >= 11) 0.6 else 0.0 }
        assertEquals(RainChange.Starts(day(11)), CommuteWeatherPolicy.evaluate(soon, routine, day(7)).rainChange)

        val late = hourly(day(6), 30) { if (it.dayOfMonth == 9) 0.6 else 0.0 }
        assertNull("no hint for rain beyond 12 hours / 12 小时以外的雨不提示", CommuteWeatherPolicy.evaluate(late, routine, day(7)).rainChange)
    }

    @Test
    fun `six-hour points are not read as hourly`() {
        // Beyond the hourly range only next_6_hours exists; its amount must not trigger the umbrella.
        // 超出逐小时范围后只有 next_6_hours；它的降水量不能触发带伞。
        val sixHourly = listOf(ForecastPoint(time = day(6).toInstant().toEpochMilli(), temperature = 8.0, symbol6h = "rain", precipitation6h = 6.0))
        val work = CommuteWeatherPolicy.evaluate(sixHourly, routine, day(5)).legs.first()
        assertFalse(work.hasData)
        assertFalse(work.umbrella)
    }

    @Test
    fun `without hourly data legs keep their times but draw no conclusion`() {
        val result = CommuteWeatherPolicy.evaluate(emptyList(), routine, day(7))
        assertEquals(2, result.legs.size)
        result.legs.forEach {
            assertFalse(it.hasData)
            assertFalse(it.umbrella)
            assertNull(it.temperature)
        }
        assertNull(result.rainChange)
    }

    @Test
    fun `an empty window or a routine that is off gives no leg`() {
        val odd = routine.copy(backHome = DailyWindow(15 * 60, 15 * 60))
        assertEquals(listOf(WindowKind.TO_WORK), CommuteWeatherPolicy.evaluate(emptyList(), odd, day(7)).legs.map { it.kind })
        assertTrue(CommuteWeatherPolicy.evaluate(emptyList(), routine.copy(enabled = false), day(7)).legs.isEmpty())
    }

    /** 2026-03-29 02:00 jumps to 03:00: a 01:00–04:00 window has two real hours. / 2026-03-29 凌晨 02:00 跳到 03:00：01:00–04:00 的时间窗只有两个真实小时。 */
    @Test
    fun `spring forward day measures the window by real hours`() {
        val early = routine.copy(toWork = DailyWindow(60, 4 * 60))
        val points = hourly(t(2026, 3, 29, 0), 8) { if (it.hour == 3) 0.4 else 0.0 }

        val work = CommuteWeatherPolicy.evaluate(points, early, t(2026, 3, 29, 0, 30)).legs.first { it.kind == WindowKind.TO_WORK }

        assertEquals(Duration.ofHours(2), Duration.between(work.start, work.end))
        assertTrue(work.umbrella)
    }

    /** 2026-10-25 03:00 falls back to 02:00: the 02:00 hour happens twice, both inside the window. / 2026-10-25 凌晨 03:00 退回 02:00：02 点出现两次，都在时间窗里。 */
    @Test
    fun `fall back day counts the repeated hour`() {
        val early = routine.copy(toWork = DailyWindow(60, 4 * 60))
        val start = t(2026, 10, 25, 0)
        // Only the second 02:00 (after the change, offset +01:00) is wet. / 只有第二个 02:00（切换后、偏移 +01:00）下雨。
        val points = hourly(start, 8) { if (it.hour == 2 && it.offset.totalSeconds == 3600) 0.4 else 0.0 }

        val work = CommuteWeatherPolicy.evaluate(points, early, start).legs.first { it.kind == WindowKind.TO_WORK }

        assertEquals(Duration.ofHours(4), Duration.between(work.start, work.end))
        assertTrue(work.umbrella)
    }
}
