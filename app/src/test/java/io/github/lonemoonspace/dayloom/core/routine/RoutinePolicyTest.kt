package io.github.lonemoonspace.dayloom.core.routine

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutinePolicyTest {

    private val zone = ZoneId.of("Europe/Oslo")
    private val routine = Routine(
        toWork = DailyWindow(7 * 60, 9 * 60),
        backHome = DailyWindow(16 * 60, 17 * 60 + 30),
    )

    /** 2026-10-05 is a Monday. / 2026-10-05 是周一。 */
    private fun t(day: Int, h: Int, mi: Int = 0) = ZonedDateTime.of(2026, 10, day, h, mi, 0, 0, zone)

    @Test
    fun `active window, nothing between windows`() {
        assertEquals(WindowKind.TO_WORK, RoutinePolicy.active(routine, t(5, 7, 30))?.kind)
        assertEquals(WindowKind.BACK_HOME, RoutinePolicy.active(routine, t(5, 16))?.kind)
        assertNull(RoutinePolicy.active(routine, t(5, 9)))
        assertNull(RoutinePolicy.active(routine, t(5, 12)))
    }

    @Test
    fun `weekends have the same windows`() {
        assertEquals(WindowKind.TO_WORK, RoutinePolicy.active(routine, t(10, 7, 30))?.kind)
        assertEquals(WindowKind.BACK_HOME, RoutinePolicy.active(routine, t(11, 17))?.kind)
    }

    @Test
    fun `next returns the window under way, else tomorrow's`() {
        assertEquals(t(5, 7), RoutinePolicy.next(routine, WindowKind.TO_WORK, t(5, 8))?.start)
        assertEquals(t(6, 7), RoutinePolicy.next(routine, WindowKind.TO_WORK, t(5, 9))?.start)
        // Friday evening → Saturday. / 周五晚上 → 周六。
        assertEquals(t(10, 7), RoutinePolicy.next(routine, WindowKind.TO_WORK, t(9, 20))?.start)
    }

    @Test
    fun `a window crossing midnight belongs to the day it starts`() {
        val night = routine.copy(toWork = DailyWindow(22 * 60, 6 * 60))
        // Saturday 03:00 is still Friday's night shift. / 周六 03:00 仍是周五的夜班。
        val occurrence = RoutinePolicy.active(night, t(10, 3))!!
        assertEquals(t(9, 22), occurrence.start)
        assertEquals(t(10, 6), occurrence.end)
        assertNull(RoutinePolicy.active(night, t(10, 12)))
    }

    @Test
    fun `validation rejects empty and out-of-range windows`() {
        assertTrue(RoutinePolicy.isValid(DailyWindow(22 * 60, 6 * 60)))
        assertFalse(RoutinePolicy.isValid(DailyWindow(8 * 60, 8 * 60)))
        assertFalse(RoutinePolicy.isValid(DailyWindow(-1, 60)))
        assertFalse(RoutinePolicy.isValid(DailyWindow(0, 24 * 60)))
        assertNull(RoutinePolicy.next(routine.copy(toWork = DailyWindow(8 * 60, 8 * 60)), WindowKind.TO_WORK, t(5, 6)))
    }
}
