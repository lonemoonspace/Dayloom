package io.github.lonemoonspace.dayloom.feature.reminders

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.refresh.RefreshReport
import io.github.lonemoonspace.dayloom.core.notify.RuleInput
import io.github.lonemoonspace.dayloom.feature.reminders.domain.ReminderItem
import io.github.lonemoonspace.dayloom.feature.reminders.domain.ReminderPolicy
import io.github.lonemoonspace.dayloom.feature.reminders.domain.ReminderPolicy.Stage
import io.github.lonemoonspace.dayloom.feature.reminders.domain.ReminderPolicy.State
import io.github.lonemoonspace.dayloom.feature.reminders.domain.ReminderRule
import io.github.lonemoonspace.dayloom.feature.reminders.domain.briefLine
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {

    private val now = LocalDateTime.parse("2026-09-15T10:00")

    private fun item(until: String, warnDays: Int = 3, id: String = "item1") = ReminderItem(id = id, name = "Pass", until = until, warnDays = warnDays)

    private fun status(until: String, at: LocalDateTime = now, warnDays: Int = 3) = ReminderPolicy.status(item(until, warnDays), at)

    @Test
    fun `missing or invalid value means not configured, a bare date means 23_59`() {
        assertNull(status(""))
        assertNull(status("not-a-date"))
        assertEquals(LocalDateTime.parse("2026-09-20T23:59"), status("2026-09-20")!!.until)
        assertEquals(LocalDateTime.parse("2026-09-20T23:59"), status("2026-09-20T23:59:59")!!.until)
        assertEquals("2026-09-20T23:59", ReminderPolicy.format(LocalDateTime.parse("2026-09-20T23:59:40")))
    }

    @Test
    fun `an item stays valid through its final minute`() {
        assertEquals(State.EXPIRING, status("2026-09-15T10:00", now.plusSeconds(59))!!.state)
        assertEquals(State.EXPIRED, status("2026-09-15T10:00", now.plusMinutes(1))!!.state)
    }

    @Test
    fun `the warning period is per item, counted in calendar days`() {
        assertEquals(State.ACTIVE, status("2026-09-18T23:59")!!.state)
        assertEquals(State.EXPIRING, status("2026-09-17T23:59")!!.state)
        assertEquals(2L, status("2026-09-17T23:59")!!.daysUntil)
        // A single warning day means only the last day. / 只提前一天即只有截止当天。
        assertEquals(State.ACTIVE, status("2026-09-16T08:00", warnDays = 1)!!.state)
        assertEquals(State.EXPIRING, status("2026-09-15T23:59", warnDays = 1)!!.state)
        assertEquals(State.EXPIRING, status("2026-10-10T23:59", warnDays = 30)!!.state)
    }

    @Test
    fun `statuses are sorted soonest first and attention follows any non-active item`() {
        val later = item("2026-12-01T12:00", id = "item1")
        val soon = item("2026-09-16T12:00", id = "item2")
        assertEquals(listOf("item2", "item1"), ReminderPolicy.statuses(listOf(later, soon), now).map { it.item.id })
        assertTrue(ReminderPolicy.needsAttention(listOf(later, soon), now))
        assertFalse(ReminderPolicy.needsAttention(listOf(later), now))
        assertFalse(ReminderPolicy.needsAttention(listOf(item("garbage")), now))
    }

    @Test
    fun `ids skip used numbers and give a notification slot`() {
        assertEquals("item3", ReminderPolicy.newId(listOf(item("x", id = "item1"), item("x", id = "item2"))))
        assertEquals(7, ReminderPolicy.idNumber("item7"))
        assertEquals(0, ReminderPolicy.idNumber("other"))
    }

    @Test
    fun `reminds once when entering the warning period and once on the last day`() {
        val items = listOf(item("2026-09-17T23:59"))
        val first = ReminderPolicy.evaluate(items, now, ReminderPolicy.NotifiedState())
        assertEquals(listOf(Stage.SOON), first.reminders.map { it.stage })

        val again = ReminderPolicy.evaluate(items, now.plusDays(1), first.newState)
        assertTrue("same stage is not repeated / 同一档不重复提醒", again.reminders.isEmpty())

        val last = ReminderPolicy.evaluate(items, now.plusDays(2), again.newState)
        assertEquals(listOf(Stage.LAST_DAY), last.reminders.map { it.stage })
    }

    @Test
    fun `first check on the last day sends only one reminder`() {
        val items = listOf(item("2026-09-15T23:59"))
        val d = ReminderPolicy.evaluate(items, now, ReminderPolicy.NotifiedState())
        assertEquals(listOf(Stage.LAST_DAY), d.reminders.map { it.stage })
        assertTrue(ReminderPolicy.evaluate(items, now.plusHours(5), d.newState).reminders.isEmpty())
    }

    @Test
    fun `a fresh expiry is reminded once, a long-expired item stays quiet`() {
        val fresh = listOf(item("2026-09-15T09:00"))
        val d = ReminderPolicy.evaluate(fresh, now, ReminderPolicy.NotifiedState())
        assertEquals(listOf(Stage.EXPIRED), d.reminders.map { it.stage })
        assertTrue(ReminderPolicy.evaluate(fresh, now.plusHours(2), d.newState).reminders.isEmpty())

        val old = ReminderPolicy.evaluate(listOf(item("2026-09-10T09:00")), now, ReminderPolicy.NotifiedState())
        assertTrue(old.reminders.isEmpty())
        // Still marked, so it never fires later either. / 仍然记为已处理，以后也不会再发。
        assertEquals(3, old.newState.keys.size)
    }

    @Test
    fun `no reminders at night`() {
        val night = LocalDateTime.parse("2026-09-15T23:00")
        assertTrue(ReminderPolicy.evaluate(listOf(item("2026-09-16T23:59")), night, ReminderPolicy.NotifiedState()).reminders.isEmpty())
    }

    @Test
    fun `renewing with a new expiry re-arms reminders and drops stale keys`() {
        val old = ReminderPolicy.evaluate(listOf(item("2026-09-16T23:59")), now, ReminderPolicy.NotifiedState())
        val renewed = ReminderPolicy.evaluate(listOf(item("2026-10-15T23:59")), now, old.newState)
        assertTrue(renewed.reminders.isEmpty())
        assertTrue(renewed.newState.keys.isEmpty())
    }

    @Test
    fun `the rule builds structured text and a per-item notification id`() = runTest {
        val items = listOf(item("2026-09-17T23:59", id = "item4"), item("2026-09-16T12:00", id = "item2"))
        var enabled = false
        val rule = ReminderRule(
            enabled = { enabled },
            items = { items },
            channelId = "reminders.expiry",
            deepLink = "dayloom://reminders",
            notificationId = { 4_000 + ReminderPolicy.idNumber(it.id) },
        )
        assertFalse(rule.isEnabled())
        enabled = true
        assertTrue(rule.isEnabled())

        val input = RuleInput(RefreshReport(emptyMap(), java.time.Instant.EPOCH), now.atZone(ZoneId.of("Europe/Oslo")))
        val decision = rule.evaluate(input, rule.codec.decode(null))
        val (tomorrow, inTwoDays) = decision.notifications
        assertEquals(4_002, tomorrow.id)
        assertEquals(UiText.Res(R.string.reminders_notify_tomorrow, listOf("Pass")), tomorrow.title)
        assertEquals(UiText.Plural(R.plurals.reminders_notify_days, 2, listOf("Pass", 2)), inTwoDays.title)
        assertEquals(UiText.Res(R.string.reminders_notify_body, listOf("2026-09-17 23:59")), inTwoDays.body)
        assertEquals("reminders.expiry", inTwoDays.channelId)
        // The state survives a round trip through the codec. / 状态经编解码往返后不变。
        assertEquals(decision.newState, rule.codec.decode(rule.codec.encode(decision.newState)))
        assertEquals(ReminderPolicy.NotifiedState(), rule.codec.decode("corrupt"))
    }

    @Test
    fun `the brief names the soonest item and counts the others, leaving long-expired ones out`() {
        val items = listOf(
            item("2026-09-16", id = "item1").copy(name = "Pass"),
            item("2026-09-17", id = "item2").copy(name = "Permit"),
            item("2026-09-14T12:00", id = "item3").copy(name = "Card"),
            item("2026-08-01", id = "item4").copy(name = "Old"),
            item("2026-12-01", id = "item5").copy(name = "Later"),
        )
        val expiredCard = UiText.Res(R.string.reminders_notify_expired, listOf("Card"))
        assertEquals(UiText.Plural(R.plurals.reminders_brief_more, 2, listOf(expiredCard, 2)), briefLine(items, now))
        assertEquals(
            UiText.Res(R.string.reminders_notify_tomorrow, listOf("Pass")),
            briefLine(listOf(items[0], items[4]), now),
        )
        assertNull(briefLine(listOf(items[3], items[4]), now))
    }
}
