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
    fun `a manual flip lasts until the clock next turns the direction`() {
        val flipped = RoutinePolicy.toggleFlip(t(9, 30), 0)
        assertEquals(CommuteDirection.BACK_HOME, RoutinePolicy.direction(t(9, 30), flipped))
        assertEquals(CommuteDirection.BACK_HOME, RoutinePolicy.direction(t(11, 59), flipped))
        // From noon the clock says homewards anyway, and the flip is over. / 中午起时钟本来就指向回家，掉头也到此为止。
        assertEquals(CommuteDirection.BACK_HOME, RoutinePolicy.direction(t(12), flipped))
        assertFalse(RoutinePolicy.isFlipped(t(12), flipped))
        assertEquals(CommuteDirection.TO_WORK, RoutinePolicy.direction(t(9).plusDays(1), flipped))

        val evening = RoutinePolicy.toggleFlip(t(18), 0)
        assertEquals(CommuteDirection.TO_WORK, RoutinePolicy.direction(t(23, 59), evening))
        assertEquals(CommuteDirection.TO_WORK, RoutinePolicy.direction(t(0).plusDays(1), evening))
        assertFalse(RoutinePolicy.isFlipped(t(0).plusDays(1), evening))
    }

    @Test
    fun `a second tap undoes the flip, and one from the future is ignored`() {
        val flipped = RoutinePolicy.toggleFlip(t(9, 30), 0)
        assertEquals(0L, RoutinePolicy.toggleFlip(t(10), flipped))
        // An expired flip counts as none, so the tap flips again. / 过期的掉头等于没有，再点就是重新掉头。
        assertEquals(t(13).toInstant().toEpochMilli(), RoutinePolicy.toggleFlip(t(13), flipped))
        assertFalse(RoutinePolicy.isFlipped(t(9), flipped))
    }

    @Test
    fun `daytime runs from six to ten in the evening`() {
        assertFalse(RoutinePolicy.isDaytime(t(5, 59)))
        assertTrue(RoutinePolicy.isDaytime(t(6)))
        assertTrue(RoutinePolicy.isDaytime(t(21, 59)))
        assertFalse(RoutinePolicy.isDaytime(t(22)))
    }
}
