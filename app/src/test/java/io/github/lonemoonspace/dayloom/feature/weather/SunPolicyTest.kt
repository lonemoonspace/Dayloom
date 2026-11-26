package io.github.lonemoonspace.dayloom.feature.weather

import io.github.lonemoonspace.dayloom.feature.weather.domain.SunDay
import io.github.lonemoonspace.dayloom.feature.weather.domain.SunPolicy
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SunPolicyTest {

    private val oslo = ZoneId.of("Europe/Oslo")

    private fun assertNear(expected: LocalTime, actual: LocalTime, minutes: Long = 2) {
        val diff = Duration.between(expected, actual).abs()
        assertTrue("expected about $expected, got $actual", diff <= Duration.ofMinutes(minutes))
    }

    @Test
    fun `central Oslo in October matches MET within two minutes`() {
        // MET's Sunrise 3.0 API for 59.91 N 10.75 E on 2026-10-20: sunrise 08:08, sunset 17:54.
        // MET Sunrise 3.0 接口给出的北纬 59.91、东经 10.75 在 2026-10-20 的时刻：日出 08:08、日落 17:54。
        val daylight = SunPolicy.daylight(LocalDate.of(2026, 10, 20), 59.91, 10.75, oslo)
        val day = daylight.day as SunDay.Rises
        assertNear(LocalTime.of(8, 8), day.sunrise.toLocalTime())
        assertNear(LocalTime.of(17, 54), day.sunset.toLocalTime())
        assertEquals(oslo, day.sunrise.zone)
        // Days shorten by about five minutes a day in late October. / 十月下旬白天每天缩短约五分钟。
        assertTrue("change ${daylight.changeMinutes}", daylight.changeMinutes!! in -6..-4)
    }

    @Test
    fun `summer time is followed`() {
        val day = SunPolicy.day(LocalDate.of(2026, 6, 21), 59.91, 10.75, oslo) as SunDay.Rises
        // MET: 03:53 and 22:43. / MET 给出 03:53 与 22:43。
        assertNear(LocalTime.of(3, 53), day.sunrise.toLocalTime())
        assertNear(LocalTime.of(22, 43), day.sunset.toLocalTime())
    }

    @Test
    fun `polar night says when the sun returns`() {
        val daylight = SunPolicy.daylight(LocalDate.of(2026, 12, 15), 69.65, 18.96, oslo)
        assertEquals(SunDay.Down, daylight.day)
        assertNotNull(daylight.until)
        val until = daylight.until!!
        assertEquals(Month.JANUARY, until.month)
        assertTrue(SunPolicy.day(until, 69.65, 18.96, oslo) is SunDay.Rises)
        assertEquals(SunDay.Down, SunPolicy.day(until.minusDays(1), 69.65, 18.96, oslo))
    }

    @Test
    fun `midnight sun says when it sets again`() {
        val daylight = SunPolicy.daylight(LocalDate.of(2026, 6, 21), 69.65, 18.96, oslo)
        assertEquals(SunDay.Up, daylight.day)
        assertEquals(Month.JULY, daylight.until!!.month)
    }

    @Test
    fun `the equator has about twelve hours all year`() {
        for (month in listOf(1, 4, 7, 10)) {
            val day = SunPolicy.day(LocalDate.of(2026, month, 15), 0.0, 0.0, ZoneId.of("UTC")) as SunDay.Rises
            assertTrue(day.length in Duration.ofMinutes(12 * 60L)..Duration.ofMinutes(12 * 60L + 10))
        }
    }

    @Test
    fun `no change is given when yesterday had no sunrise`() {
        // The first day after polar night: there is no yesterday to compare with. / 极夜后的第一天：没有昨天可比。
        val first = SunPolicy.daylight(LocalDate.of(2026, 12, 15), 69.65, 18.96, oslo).until!!
        val daylight = SunPolicy.daylight(first, 69.65, 18.96, oslo)
        assertTrue(daylight.day is SunDay.Rises)
        assertEquals(null, daylight.changeMinutes)
    }
}
