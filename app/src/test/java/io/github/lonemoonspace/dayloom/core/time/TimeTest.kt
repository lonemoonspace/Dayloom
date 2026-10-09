package io.github.lonemoonspace.dayloom.core.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeTest {

    private val oslo = ZoneId.of("Europe/Oslo")
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun `a valid override wins, anything else follows the device`() {
        assertEquals(shanghai, ZonePolicy.resolve("Asia/Shanghai", oslo))
        assertEquals(shanghai, ZonePolicy.resolve("  Asia/Shanghai ", oslo))
        assertEquals(oslo, ZonePolicy.resolve("", oslo))
        assertEquals("an invalid stored value must not crash or become UTC", oslo, ZonePolicy.resolve("Mars/Base", oslo))
        assertNull(ZonePolicy.parse("not/a zone"))
    }

    @Test
    fun `the system clock reads the zone on every call`() {
        var zone: ZoneId = oslo
        val instant = Instant.parse("2026-10-09T06:00:00Z")
        val clock = SystemAppClock(zoneProvider = { zone }, base = Clock.fixed(instant, ZoneOffset.UTC))

        assertEquals(8, clock.now().hour)
        zone = shanghai
        assertEquals(14, clock.now().hour)
        assertEquals(instant, clock.instant())
    }

    @Test
    fun `offset timestamps are parsed into the given zone`() {
        val parsed = TimeParsing.parseOffset("2026-10-09T08:00:00+02:00", shanghai)
        assertEquals(ZonedDateTime.of(2026, 10, 9, 14, 0, 0, 0, shanghai), parsed)
        assertNull(TimeParsing.parseOffset("garbage", oslo))
        assertNull(TimeParsing.parseOffset(null, oslo))
    }

    @Test
    fun `UTC instants tolerate a missing Z`() {
        val expected = ZonedDateTime.of(2026, 10, 9, 21, 0, 0, 0, oslo)
        assertEquals(expected, TimeParsing.parseInstant("2026-10-09T19:00:00Z", oslo))
        assertEquals(expected, TimeParsing.parseInstant("2026-10-09T19:00:00", oslo))
        assertNull(TimeParsing.parseInstant(" ", oslo))
    }

    @Test
    fun `delay is whole minutes and never negative`() {
        val aimed = ZonedDateTime.of(2026, 10, 9, 8, 0, 0, 0, oslo)
        assertEquals(3, TimeParsing.delayMinutes(aimed, aimed.plusMinutes(3).plusSeconds(40)))
        assertEquals(0, TimeParsing.delayMinutes(aimed, aimed.minusMinutes(1)))
        assertNull(TimeParsing.delayMinutes(null, aimed))
    }

    @Test
    fun `isoOffset can be parsed back`() {
        val time = ZonedDateTime.of(2026, 10, 9, 8, 0, 0, 0, oslo)
        assertEquals(time, TimeParsing.parseOffset(TimeParsing.isoOffset(time), oslo))
    }
}
