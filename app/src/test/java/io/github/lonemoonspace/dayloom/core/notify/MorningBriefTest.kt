package io.github.lonemoonspace.dayloom.core.notify

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.refresh.RefreshReport
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
    private val seven = 7 * 60

    /** 2026-10-05 is a Monday. / 2026-10-05 是周一。 */
    private fun at(day: Int, h: Int, mi: Int = 0) = ZonedDateTime.of(2026, 10, day, h, mi, 0, 0, zone)
    private val monday = LocalDate.of(2026, 10, 5)

    @Test
    fun `due once a day from the chosen time, weekends included`() {
        assertEquals(monday, MorningBriefPolicy.dueDay(seven, at(5, 7, 10), null))
        assertNull("already sent today", MorningBriefPolicy.dueDay(seven, at(5, 7, 40), monday))
        assertEquals("a new day", monday.plusDays(1), MorningBriefPolicy.dueDay(seven, at(6, 7, 10), monday))
        assertNull("before the time", MorningBriefPolicy.dueDay(seven, at(5, 6, 50), null))
        assertEquals("weekend", LocalDate.of(2026, 10, 10), MorningBriefPolicy.dueDay(seven, at(10, 7, 10), null))
    }

    @Test
    fun `three hours late it is no longer news, and it never spills into the next day`() {
        assertEquals(monday, MorningBriefPolicy.dueDay(seven, at(5, 9, 59), null))
        assertNull(MorningBriefPolicy.dueDay(seven, at(5, 10, 0), null))
        val late = 23 * 60
        assertEquals(monday, MorningBriefPolicy.dueDay(late, at(5, 23, 30), null))
        assertNull("after midnight is the next day, before its own time", MorningBriefPolicy.dueDay(late, at(6, 0, 30), null))
        assertNull("invalid time", MorningBriefPolicy.dueDay(24 * 60, at(5, 7, 10), null))
    }

    private fun input(now: ZonedDateTime) = RuleInput(RefreshReport(emptyMap(), Instant.EPOCH), now)

    private fun rule(vararg lines: BriefContributor, errors: MutableList<Exception> = mutableListOf()) = MorningBriefRule(
        enabled = { true },
        briefMinute = { seven },
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
