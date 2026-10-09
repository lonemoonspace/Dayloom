package io.github.lonemoonspace.dayloom.core.notify

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.refresh.RefreshReport
import io.github.lonemoonspace.dayloom.core.routine.DailyWindow
import io.github.lonemoonspace.dayloom.core.routine.Routine
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningBriefTest {

    private val zone = ZoneId.of("Europe/Oslo")
    private val routine = Routine(toWork = DailyWindow(7 * 60, 9 * 60), backHome = DailyWindow(16 * 60, 18 * 60))

    /** 2026-10-05 is a Monday. / 2026-10-05 是周一。 */
    private fun at(day: Int, h: Int, mi: Int = 0) = ZonedDateTime.of(2026, 10, day, h, mi, 0, 0, zone)
    private val monday = LocalDate.of(2026, 10, 5)

    @Test
    fun `due once per working day inside the to-work window`() {
        assertEquals(monday, MorningBriefPolicy.dueDay(routine, at(5, 7, 10), null))
        assertNull("already sent today", MorningBriefPolicy.dueDay(routine, at(5, 7, 40), monday))
        assertEquals("a new day", monday.plusDays(1), MorningBriefPolicy.dueDay(routine, at(6, 7, 10), monday))
        assertNull("before the window", MorningBriefPolicy.dueDay(routine, at(5, 6, 50), null))
        assertNull("the back-home window does not count", MorningBriefPolicy.dueDay(routine, at(5, 17), null))
        assertNull("weekend", MorningBriefPolicy.dueDay(routine, at(10, 7, 10), null))
        assertNull("no commute", MorningBriefPolicy.dueDay(routine.copy(enabled = false), at(5, 7, 10), null))
    }

    @Test
    fun `a window crossing midnight belongs to the day it starts`() {
        val night = routine.copy(toWork = DailyWindow(23 * 60, 1 * 60))
        assertEquals(monday, MorningBriefPolicy.dueDay(night, at(6, 0, 30), null))
    }

    private fun input(now: ZonedDateTime) = RuleInput(RefreshReport(emptyMap(), Instant.EPOCH), now)

    private fun rule(vararg lines: BriefContributor, errors: MutableList<Exception> = mutableListOf()) = MorningBriefRule(
        enabled = { true },
        routine = { routine },
        contributors = { lines.toList() },
        onError = { errors += it },
    )

    @Test
    fun `lines come in module order, skipping modules with nothing to say`() = runTest {
        val decision = rule(
            BriefContributor { UiText.Raw("weather") },
            BriefContributor { null },
            BriefContributor { UiText.Raw("transit") },
        ).evaluate(input(at(5, 7, 10)), null)

        val notification = decision.notifications.single()
        assertEquals(UiText.Res(R.string.brief_title), notification.title)
        assertEquals(UiText.Lines(listOf(UiText.Raw("weather"), UiText.Raw("transit"))), notification.body)
        assertEquals(CoreNotifications.BRIEF_CHANNEL_ID, notification.channelId)
        assertEquals(CoreNotifications.BRIEF_ID, notification.id)
        assertEquals(monday, decision.newState)
    }

    @Test
    fun `with nothing to say the day is not used up`() = runTest {
        val decision = rule(BriefContributor { null }).evaluate(input(at(5, 7, 10)), null)
        assertTrue(decision.notifications.isEmpty())
        assertNull(decision.newState)
    }

    @Test
    fun `a failing module only loses its own line`() = runTest {
        val errors = mutableListOf<Exception>()
        val decision = rule(
            BriefContributor { throw IOException("broken snapshot") },
            BriefContributor { UiText.Raw("transit") },
            errors = errors,
        ).evaluate(input(at(5, 7, 10)), null)
        assertEquals(UiText.Lines(listOf(UiText.Raw("transit"))), decision.notifications.single().body)
        assertEquals(1, errors.size)
    }

    @Test
    fun `outside the window the state passes through`() = runTest {
        val previous = LocalDate.of(2026, 10, 2)
        val decision = rule(BriefContributor { UiText.Raw("weather") }).evaluate(input(at(5, 12)), previous)
        assertTrue(decision.notifications.isEmpty())
        assertEquals(previous, decision.newState)
    }
}
