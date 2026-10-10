package io.github.lonemoonspace.dayloom.core.routine

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutinePolicyTest {
    private val zone = ZoneId.of("Europe/Oslo")
    private fun t(hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, 10, hour, minute, 0, 0, zone)

    @Test
    fun `mornings look to work, from noon on homewards`() {
        assertEquals(CommuteDirection.TO_WORK, RoutinePolicy.direction(t(0)))
        assertEquals(CommuteDirection.TO_WORK, RoutinePolicy.direction(t(11, 59)))
        assertEquals(CommuteDirection.BACK_HOME, RoutinePolicy.direction(t(12)))
        assertEquals(CommuteDirection.BACK_HOME, RoutinePolicy.direction(t(23, 59)))
    }

    @Test
    fun `daytime runs from six to ten in the evening`() {
        assertFalse(RoutinePolicy.isDaytime(t(5, 59)))
        assertTrue(RoutinePolicy.isDaytime(t(6)))
        assertTrue(RoutinePolicy.isDaytime(t(21, 59)))
        assertFalse(RoutinePolicy.isDaytime(t(22)))
    }
}
