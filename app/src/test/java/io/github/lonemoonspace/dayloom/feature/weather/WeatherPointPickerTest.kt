package io.github.lonemoonspace.dayloom.feature.weather

import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherPointPicker
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The regression guarded here is `picks the nearest point when the window misses`: taking the first point of a stale or
 * truncated series showed an hours-old temperature as "now" on a normal-looking card.
 * 这里防的回归是 `picks the nearest point when the window misses`：断更或被截断的序列取第一个点，会把几小时前的温度当成
 * 「现在」显示在看起来正常的卡片上。
 */
class WeatherPointPickerTest {

    private val now: ZonedDateTime = ZonedDateTime.of(2026, 9, 22, 8, 0, 0, 0, ZoneId.of("Europe/Oslo"))

    private fun at(hoursFromNow: Long, minutes: Long = 0): ZonedDateTime = now.plusHours(hoursFromNow).plusMinutes(minutes)

    private fun pick(times: List<ZonedDateTime?>): ZonedDateTime? = WeatherPointPicker.pick(times, now) { it }

    @Test
    fun `prefers the point inside the window before now`() {
        assertEquals(at(0, -30), pick(listOf(at(-2), at(-1), at(0, -30), at(1))))
    }

    @Test
    fun `accepts an upcoming point inside the 90 minute window`() {
        assertEquals(at(0, 80), pick(listOf(at(-3), at(0, 80), at(2))))
    }

    @Test
    fun `picks the nearest point when the window misses`() {
        assertEquals(at(4), pick(listOf(at(-6), at(4))))
    }

    @Test
    fun `picks the earlier point when the two candidates are equally far away`() {
        assertEquals(at(-2), pick(listOf(at(-2), at(2))))
    }

    @Test
    fun `falls back to the first item when no time can be parsed`() {
        assertEquals("first", WeatherPointPicker.pick(listOf("first", "second"), now) { null })
    }

    @Test
    fun `skips unparseable times when some other points have one`() {
        val result = WeatherPointPicker.pick(listOf("no-time", "timed"), now) { if (it == "timed") at(0, 10) else null }
        assertEquals("timed", result)
    }

    @Test
    fun `returns null for an empty series`() {
        assertNull(WeatherPointPicker.pick(emptyList<String>(), now) { null })
    }
}
