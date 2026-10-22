package io.github.lonemoonspace.dayloom.feature.transit

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.notify.RuleInput
import io.github.lonemoonspace.dayloom.core.refresh.RefreshReport
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.refresh.SourceResult
import io.github.lonemoonspace.dayloom.feature.transit.domain.BoardDeparture
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteMode
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteTrips
import io.github.lonemoonspace.dayloom.feature.transit.domain.DisruptionPolicy
import io.github.lonemoonspace.dayloom.feature.transit.domain.DisruptionRule
import io.github.lonemoonspace.dayloom.feature.transit.domain.FavouriteBoard
import io.github.lonemoonspace.dayloom.feature.transit.domain.LegState
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransferGain
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransferGainKind
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitBrief
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitLeg
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitPolicy
import io.github.lonemoonspace.dayloom.feature.transit.domain.TripOption
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitPolicyTest {

    private val zone = ZoneId.of("Europe/Oslo")

    /** 2026-10-05 is a Monday. / 2026-10-05 是周一。 */
    private fun t(day: Int, h: Int, mi: Int = 0): ZonedDateTime = ZonedDateTime.of(2026, 10, day, h, mi, 0, 0, zone)
    private fun ms(time: ZonedDateTime) = time.toInstant().toEpochMilli()
    private val now = t(5, 7, 30)

    private fun leg(
        line: String = "R1",
        dep: ZonedDateTime = now.plusMinutes(10),
        delay: Long = 0,
        realtime: Boolean = true,
        cancelled: Boolean = false,
        from: String = "Stop A",
        to: String = "Stop B",
    ) = TransitLeg(
        mode = "rail", line = line, fromName = from, toName = to,
        aimedDeparture = ms(dep), expectedDeparture = ms(dep.plusMinutes(delay)),
        aimedArrival = ms(dep.plusMinutes(30)), expectedArrival = ms(dep.plusMinutes(30 + delay)),
        realtime = realtime, cancelled = cancelled,
    )

    // Status / 状态

    @Test
    fun `four states, and missing real-time data is never on time`() {
        assertEquals(LegState.ON_TIME, TransitPolicy.legStatus(leg()).state)
        assertEquals(LegState.DELAYED, TransitPolicy.legStatus(leg(delay = 4)).state)
        assertEquals(4, TransitPolicy.legStatus(leg(delay = 4)).delayMinutes)
        assertEquals(LegState.NO_REALTIME, TransitPolicy.legStatus(leg(realtime = false)).state)
        assertEquals(LegState.CANCELLED, TransitPolicy.legStatus(leg(realtime = false, cancelled = true)).state)
        // Running early is not a negative delay. / 提前不算负延误。
        assertEquals(LegState.ON_TIME, TransitPolicy.legStatus(leg(delay = -2)).state)
    }

    @Test
    fun `an option shows its worst leg`() {
        val option = TripOption(listOf(leg(line = "A", realtime = false), leg(line = "B", delay = 3), leg(line = "C", delay = 7)))
        assertEquals("C", TransitPolicy.worst(option)!!.first.line)
        val cancelled = TripOption(listOf(leg(line = "A", delay = 20), leg(line = "B", cancelled = true)))
        assertEquals(LegState.CANCELLED, TransitPolicy.worst(cancelled)!!.second.state)
        assertEquals(LegState.NO_REALTIME, TransitPolicy.worst(TripOption(listOf(leg(), leg(realtime = false))))!!.second.state)
    }

    @Test
    fun `the commute mode switches at noon`() {
        assertEquals(CommuteMode.OUTBOUND, TransitPolicy.commuteMode(t(5, 7, 30)))
        assertEquals(CommuteMode.OUTBOUND, TransitPolicy.commuteMode(t(5, 11, 59)))
        assertEquals(CommuteMode.INBOUND, TransitPolicy.commuteMode(t(5, 12)))
        assertEquals("weekends too", CommuteMode.INBOUND, TransitPolicy.commuteMode(t(10, 17)))
    }

    @Test
    fun `alerts only in the daytime and for trips leaving within 45 minutes`() {
        assertTrue(TransitPolicy.isAlertable(option(leg(dep = now.plusMinutes(45))), now))
        assertFalse(TransitPolicy.isAlertable(option(leg(dep = now.plusMinutes(46))), now))
        assertFalse("already left", TransitPolicy.isAlertable(option(leg(dep = now.minusMinutes(1))), now))
        val late = t(5, 22, 5)
        assertFalse(TransitPolicy.isAlertable(option(leg(dep = late.plusMinutes(5))), late))
        val early = t(5, 5, 50)
        assertFalse(TransitPolicy.isAlertable(option(leg(dep = early.plusMinutes(5))), early))
    }

    @Test
    fun `visible options drop departed ones and keep the limit`() {
        val gone = TripOption(listOf(leg(dep = now.minusMinutes(1))))
        val soon = TripOption(listOf(leg(dep = now.plusMinutes(2))))
        val later = TripOption(listOf(leg(dep = now.plusMinutes(12))))
        val cancelled = TripOption(listOf(leg(dep = now.plusMinutes(5), cancelled = true)))
        assertEquals(listOf(soon, cancelled), TransitPolicy.visibleOptions(listOf(later, gone, cancelled, soon), now, 2))
        assertEquals("a broken limit shows nothing instead of crashing", emptyList<TripOption>(), TransitPolicy.visibleOptions(listOf(soon), now, -1))
    }

    @Test
    fun `a train that passed its time after the last fetch stays until a fetch confirms it left`() {
        val train = TripOption(listOf(leg(dep = now.minusMinutes(2))))
        val fetchedBefore = ms(now.minusMinutes(5))
        val fetchedAfter = ms(now.minusMinutes(1))

        assertEquals(listOf(train), TransitPolicy.visibleOptions(listOf(train), now, 3, fetchedBefore))
        assertTrue(TransitPolicy.awaitingDeparture(listOf(train), fetchedBefore, now))
        // A fetch after its departure time that still lists the old time has nothing newer to say: it is gone.
        // 发车时刻之后的抓取仍是旧时刻，说明没有更新的消息：它已开走。
        assertEquals(emptyList<TripOption>(), TransitPolicy.visibleOptions(listOf(train), now, 3, fetchedAfter))
        assertFalse(TransitPolicy.awaitingDeparture(listOf(train), fetchedAfter, now))
        assertEquals("without a fetch time nothing is kept", emptyList<TripOption>(), TransitPolicy.visibleOptions(listOf(train), now, 3))
    }

    @Test
    fun `an unconfirmed train is kept for at most the grace period`() {
        val old = TripOption(listOf(leg(dep = now.minus(TransitPolicy.UNCONFIRMED_GRACE).minusMinutes(1))))
        val fetched = ms(now.minusHours(2))

        assertEquals(emptyList<TripOption>(), TransitPolicy.visibleOptions(listOf(old), now, 3, fetched))
        assertFalse(TransitPolicy.awaitingDeparture(listOf(old), fetched, now))
    }

    // Transfers / 换乘

    private fun ride(line: String, dep: ZonedDateTime, arr: ZonedDateTime, from: String, to: String, cancelled: Boolean = false) = TransitLeg(
        mode = "rail", line = line, fromName = from, toName = to,
        aimedDeparture = ms(dep), expectedDeparture = ms(dep), aimedArrival = ms(arr), expectedArrival = ms(arr),
        realtime = true, cancelled = cancelled,
    )

    /** A local train from A to C, and a change at B onto a faster train. / A 到 C 的慢车，以及在 B 换更快的车。 */
    private val direct1 = TripOption(listOf(ride("L1", t(5, 7, 40), t(5, 8, 14), "Stop A", "Stop C")))
    private val direct2 = TripOption(listOf(ride("L1", t(5, 8, 10), t(5, 8, 44), "Stop A", "Stop C")))
    private val faster1 = TripOption(
        listOf(ride("L1", t(5, 7, 40), t(5, 7, 49), "Stop A", "Stop B"), ride("R1", t(5, 7, 55), t(5, 8, 6), "Stop B", "Stop C")),
        alternative = true,
    )
    private val slower2 = TripOption(
        listOf(ride("L1", t(5, 8, 10), t(5, 8, 19), "Stop A", "Stop B"), ride("R3", t(5, 8, 35), t(5, 8, 46), "Stop B", "Stop C")),
        alternative = true,
    )

    @Test
    fun `a faster transfer replaces the direct option on the same first train, a slower one is dropped`() {
        val all = listOf(direct1, faster1, direct2, slower2)

        assertEquals(listOf(faster1, direct2), TransitPolicy.visibleOptions(all, now, 5))
        assertEquals(TransferGain(TransferGainKind.ARRIVE_EARLIER, 8), TransitPolicy.transferGain(faster1, all, now))
        assertNull(TransitPolicy.transferGain(direct2, all, now))
    }

    @Test
    fun `a transfer shows the direct option it replaced on its second line`() {
        val all = listOf(direct1, faster1, direct2, slower2)

        assertEquals(direct1, TransitPolicy.foldedDirect(faster1, all))
        assertNull(TransitPolicy.foldedDirect(direct2, all))
        // Leaving home on a different train than any direct option, nothing was folded in. / 和任何直达都不是同一班首车，没有并入的直达。
        val leaveLater = TripOption(listOf(ride("R1", t(5, 7, 45), t(5, 7, 52), "Stop A", "Stop B"), faster1.legs[1]), alternative = true)
        assertNull(TransitPolicy.foldedDirect(leaveLater, listOf(direct1, leaveLater)))
        // A cancelled direct option was never folded in, so it keeps its own struck-through row. / 被取消的直达从未被并入，仍单独一行并划掉。
        val cancelledDirect = direct1.copy(legs = direct1.legs.map { it.copy(cancelled = true) })
        assertNull(TransitPolicy.foldedDirect(faster1, listOf(cancelledDirect, faster1)))
    }

    @Test
    fun `stop names drop the stasjon suffix for the narrow station column`() {
        assertEquals("Asker", TransitPolicy.shortStopName("Asker stasjon"))
        assertEquals("Oslo S", TransitPolicy.shortStopName(" Oslo S "))
        assertEquals("Nationaltheatret", TransitPolicy.shortStopName("Nationaltheatret Stasjon"))
        assertEquals("stasjon", TransitPolicy.shortStopName("stasjon"))
    }

    @Test
    fun `a later train that catches the same connection says how much later the user can leave`() {
        val home = TripOption(listOf(ride("L1", t(5, 16, 21), t(5, 16, 54), "Stop C", "Stop A")))
        val leaveLater = TripOption(
            listOf(ride("R1", t(5, 16, 29), t(5, 16, 39), "Stop C", "Stop B"), ride("L1", t(5, 16, 46), t(5, 16, 54), "Stop B", "Stop A")),
            alternative = true,
        )
        val all = listOf(home, leaveLater)

        assertEquals(all, TransitPolicy.visibleOptions(all, t(5, 16, 0), 5))
        assertEquals(TransferGain(TransferGainKind.LEAVE_LATER, 8), TransitPolicy.transferGain(leaveLater, all, t(5, 16, 0)))
    }

    @Test
    fun `cancelled transfers never show, and a cancelled direct option beats nothing`() {
        val cancelledTransfer = faster1.copy(legs = faster1.legs.mapIndexed { i, l -> if (i == 1) l.copy(cancelled = true) else l })
        assertEquals(listOf(direct1), TransitPolicy.visibleOptions(listOf(direct1, cancelledTransfer), now, 5))

        // A cancelled direct option is no baseline, so a transfer measured only against it has no gain and stays hidden;
        // the cancelled row itself stays, struck through.
        // 被取消的直达不能当比较对象，只能和它比的换乘没有收益、不显示；被取消的那一行照样保留并划掉。
        val cancelledDirect = direct1.copy(legs = direct1.legs.map { it.copy(cancelled = true) })
        assertEquals(listOf(cancelledDirect), TransitPolicy.visibleOptions(listOf(cancelledDirect, faster1), now, 5))
    }

    @Test
    fun `only direct options are replaced, so an ordinary transfer on the same first train keeps its row`() {
        val ordinary = TripOption(listOf(faster1.legs[0], ride("R3", t(5, 8, 0), t(5, 8, 20), "Stop B", "Stop C")))
        val ordinaryCancelled = ordinary.copy(legs = listOf(ordinary.legs[0], ordinary.legs[1].copy(cancelled = true)))

        assertEquals(setOf(faster1, ordinary), TransitPolicy.visibleOptions(listOf(direct1, faster1, ordinary), now, 5).toSet())
        assertEquals(setOf(faster1, ordinaryCancelled), TransitPolicy.visibleOptions(listOf(direct1, faster1, ordinaryCancelled), now, 5).toSet())
    }

    @Test
    fun `the gain is measured only against options that have not left yet`() {
        val gone = TripOption(listOf(ride("L1", t(5, 7, 55), t(5, 8, 28), "Stop A", "Stop C")))
        val other = TripOption(listOf(ride("R1", t(5, 8, 0), t(5, 8, 15), "Stop A", "Stop C")), alternative = true)
        val all = listOf(gone, other)

        assertEquals(TransferGain(TransferGainKind.ARRIVE_EARLIER, 13), TransitPolicy.transferGain(other, all, t(5, 7, 50)))
        assertNull(TransitPolicy.transferGain(other, all, t(5, 7, 58)))
        assertEquals(emptyList<TripOption>(), TransitPolicy.visibleOptions(all, t(5, 7, 58), 5))
    }

    @Test
    fun `an alternative that leaves later and arrives later gains nothing and does not show`() {
        val laterBoth = TripOption(listOf(ride("L1", t(5, 7, 50), t(5, 8, 24), "Stop A", "Stop C")), alternative = true)

        assertNull(TransitPolicy.transferGain(laterBoth, listOf(direct1, laterBoth), now))
        assertEquals(listOf(direct1), TransitPolicy.visibleOptions(listOf(direct1, laterBoth), now, 5))
    }

    @Test
    fun `equal alternatives keep the first, and lists without alternatives pass through`() {
        val copy = faster1.copy(legs = faster1.legs.map { it.copy(line = it.line + "x") })
        assertEquals(listOf(faster1, direct2), TransitPolicy.visibleOptions(listOf(direct1, faster1, copy, direct2), now, 5))
        val plain = listOf(direct1, direct2)
        assertTrue(TransitPolicy.withTransfers(plain, now) === plain)
    }

    // Boards / 发车板

    private fun departure(line: String, front: String, minutes: Long, realtime: Boolean = true) =
        BoardDeparture(line = line, frontText = front, aimed = ms(now.plusMinutes(minutes)), expected = ms(now.plusMinutes(minutes)), realtime = realtime)

    @Test
    fun `boards filter by line and destination, soonest first`() {
        val calls = listOf(
            departure("31", "Town C", 9),
            departure("31", "Town D", 3),
            departure("31", "Town C via Town E", 5),
            departure("37", "Town C", 1),
            departure("31", "Town C", -1),
        )
        val board = FavouriteBoard(id = "board1", lines = listOf("31"), destinations = listOf("Town C"))
        assertEquals(listOf(5L, 9L), TransitPolicy.filterBoard(board, calls, now).map { (it.expected - ms(now)) / 60_000 })
        assertEquals(4, TransitPolicy.filterBoard(FavouriteBoard(id = "board1"), calls, now).size)
    }

    @Test
    fun `board countdown only within the hour`() {
        assertEquals(0, TransitPolicy.minutesUntil(ms(now.plusSeconds(30)), now))
        assertEquals(0, TransitPolicy.minutesUntil(ms(now.minusMinutes(1)), now))
        assertEquals(21, TransitPolicy.minutesUntil(ms(now.plusMinutes(21)), now))
        assertEquals(59, TransitPolicy.minutesUntil(ms(now.plusMinutes(59).plusSeconds(59)), now))
        assertNull(TransitPolicy.minutesUntil(ms(now.plusMinutes(60)), now))
        // A damaged time must not overflow into the countdown. / 损坏的时刻不能溢出成倒计时。
        assertNull(TransitPolicy.minutesUntil(Long.MAX_VALUE, now))
    }

    @Test
    fun `destination matching tolerates station suffixes and via-text but is never a substring match`() {
        assertTrue(TransitPolicy.destinationMatches("Town C stasjon", "Town C"))
        assertTrue(TransitPolicy.destinationMatches("town c via Town E", "Town C"))
        assertTrue(TransitPolicy.destinationMatches("Town C", "Town C stasjon"))
        assertFalse(TransitPolicy.destinationMatches("Town Cx", "Town C"))
        assertFalse(TransitPolicy.destinationMatches("Old Town C", "Town C"))
        assertFalse(TransitPolicy.destinationMatches(null, "Town C"))
    }

    @Test
    fun `filter text and ids`() {
        assertEquals(listOf("31", "L1"), TransitPolicy.parseList(" 31, L1，31 ,, "))
        assertEquals("board2", TransitPolicy.newBoardId(listOf(FavouriteBoard(id = "board1"), FavouriteBoard(id = "board3"))))
    }

    // Disruptions, ported from the original CommuteDisruptionPolicyTest / 异常通知，移植自原项目 CommuteDisruptionPolicyTest

    private fun option(vararg legs: TransitLeg) = TripOption(legs.toList())

    @Test
    fun `outside a window nothing is decided and the fingerprint passes through`() {
        val decision = DisruptionPolicy.evaluate(null, zone, "previous")
        assertTrue(decision.disrupted.isEmpty())
        assertEquals("previous", decision.newFingerprint)
    }

    @Test
    fun `a cancellation notifies once, and the same disruption does not notify again`() {
        val first = DisruptionPolicy.evaluate(option(leg(cancelled = true)), zone, null)
        assertEquals(1, first.disrupted.size)
        val again = DisruptionPolicy.evaluate(option(leg(cancelled = true)), zone, first.newFingerprint)
        assertTrue(again.disrupted.isEmpty())
        assertEquals(first.newFingerprint, again.newFingerprint)
    }

    @Test
    fun `the delay threshold is five minutes, unknown delays never count`() {
        assertTrue(DisruptionPolicy.evaluate(option(leg(delay = 4)), zone, null).disrupted.isEmpty())
        assertEquals(1, DisruptionPolicy.evaluate(option(leg(delay = 5)), zone, null).disrupted.size)
        assertTrue(DisruptionPolicy.evaluate(option(leg(delay = 30, realtime = false)), zone, null).disrupted.isEmpty())
        assertEquals(1, DisruptionPolicy.evaluate(option(leg(realtime = false, cancelled = true)), zone, null).disrupted.size)
    }

    @Test
    fun `drifting within a bucket stays quiet, a worse bucket notifies again`() {
        val first = DisruptionPolicy.evaluate(option(leg(delay = 6)), zone, null)
        assertTrue(DisruptionPolicy.evaluate(option(leg(delay = 8)), zone, first.newFingerprint).disrupted.isEmpty())
        assertEquals(1, DisruptionPolicy.evaluate(option(leg(delay = 15)), zone, first.newFingerprint).disrupted.size)
    }

    @Test
    fun `the next train on the same line, late by as much, is a new disruption`() {
        val first = DisruptionPolicy.evaluate(option(leg(delay = 6)), zone, null)
        assertEquals(1, DisruptionPolicy.evaluate(option(leg(dep = now.plusMinutes(40), delay = 6)), zone, first.newFingerprint).disrupted.size)
    }

    @Test
    fun `clearing resets the fingerprint so a recurrence notifies, and so does the next day`() {
        val first = DisruptionPolicy.evaluate(option(leg(cancelled = true)), zone, null)
        val cleared = DisruptionPolicy.evaluate(option(leg()), zone, first.newFingerprint)
        assertNull(cleared.newFingerprint)
        assertEquals(1, DisruptionPolicy.evaluate(option(leg(cancelled = true)), zone, cleared.newFingerprint).disrupted.size)
        val tomorrow = DisruptionPolicy.evaluate(option(leg(dep = now.plusDays(1), cancelled = true)), zone, first.newFingerprint)
        assertEquals(1, tomorrow.disrupted.size)
    }

    @Test
    fun `any disrupted leg among several counts`() {
        assertEquals(listOf("B"), DisruptionPolicy.evaluate(option(leg(line = "A"), leg(line = "B", delay = 12)), zone, null).disrupted.map { it.line })
    }

    @Test
    fun `the rule only acts on a fresh snapshot of a trip leaving soon in the daytime and sends structured text`() = runTest {
        val id = SourceId("transit.train")
        val fresh = RefreshReport(mapOf(id to SourceResult.Success(Instant.EPOCH)), Instant.EPOCH)
        val failed = RefreshReport(mapOf(id to SourceResult.Failed(io.github.lonemoonspace.dayloom.core.error.AppError.Offline())), Instant.EPOCH)
        var trips = CommuteTrips(CommuteMode.OUTBOUND, outbound = listOf(option(leg(line = "R1", delay = 12))))
        val rule = DisruptionRule(
            name = "train_disruption",
            enabled = { true },
            refreshed = { it.succeeded(id) },
            trips = { trips },
            channelId = "transit.disruption",
            deepLink = "dayloom://transit",
            notificationId = 2_000,
        )
        assertEquals("kept", rule.evaluate(RuleInput(failed, now), "kept").newState)

        val decision = rule.evaluate(RuleInput(fresh, now), null)
        val notification = decision.notifications.single()
        assertEquals(UiText.Res(R.string.transit_notify_to_work), notification.title)
        assertEquals(UiText.Plural(R.plurals.transit_notify_delayed, 12, listOf("R1", "07:40", 12)), notification.body)
        assertTrue(rule.evaluate(RuleInput(fresh, now), decision.newState).notifications.isEmpty())

        // A cancellation added to an already notified delay is the news, so the body names it.
        // 已通知过的延误之外又有一段被取消，取消才是新消息，正文要说它。
        trips = trips.copy(outbound = listOf(option(leg(line = "R1", delay = 12), leg(line = "B2", dep = now.plusMinutes(45), cancelled = true))))
        val worse = rule.evaluate(RuleInput(fresh, now), decision.newState)
        assertEquals(UiText.Res(R.string.transit_notify_cancelled, listOf("B2", "08:15")), worse.notifications.single().body)

        // Further ahead than 45 minutes, or at night, nothing is said; once it comes close it is told once.
        // 超过 45 分钟之后的班次或夜里都不提醒；临近时提醒一次。
        trips = CommuteTrips(CommuteMode.OUTBOUND, outbound = listOf(option(leg(line = "R5", dep = now.plusMinutes(50), cancelled = true))))
        val early = rule.evaluate(RuleInput(fresh, now), null)
        assertTrue(early.notifications.isEmpty())
        assertEquals(1, rule.evaluate(RuleInput(fresh, now.plusMinutes(10)), early.newState).notifications.size)
        val night = t(5, 22, 30)
        trips = CommuteTrips(CommuteMode.INBOUND, inbound = listOf(option(leg(line = "R5", dep = night.plusMinutes(10), cancelled = true))))
        assertTrue(rule.evaluate(RuleInput(fresh, night), null).notifications.isEmpty())

        // Once the delay is over, the alert about it is taken down; with nothing told, nothing is.
        // 延误结束后撤下关于它的通知；没提醒过的就什么都不撤。
        trips = CommuteTrips(CommuteMode.OUTBOUND, outbound = listOf(option(leg(line = "R1"))))
        val over = rule.evaluate(RuleInput(fresh, now), decision.newState)
        assertEquals(listOf(2_000), over.cancel)
        assertNull(over.newState)
        assertTrue(rule.evaluate(RuleInput(fresh, now), null).cancel.isEmpty())
    }

    @Test
    fun `an alert is withdrawn once its trip has left, even when no trip is alertable, but kept while it is still ahead`() = runTest {
        val id = SourceId("transit.train")
        val fresh = RefreshReport(mapOf(id to SourceResult.Success(Instant.EPOCH)), Instant.EPOCH)
        val failed = RefreshReport(mapOf(id to SourceResult.Failed(io.github.lonemoonspace.dayloom.core.error.AppError.Offline())), Instant.EPOCH)
        var trips = CommuteTrips(CommuteMode.OUTBOUND)
        val rule = DisruptionRule(
            name = "train_disruption",
            enabled = { true },
            refreshed = { it.succeeded(id) },
            trips = { trips },
            channelId = "transit.disruption",
            deepLink = "dayloom://transit",
            notificationId = 2_000,
        )

        // Leaves 07:52 instead of 07:40; the next train is more than 45 minutes after it.
        // 计划 07:40、实际 07:52 发车；下一班在它 45 分钟之后。
        val late = option(leg(line = "R1", delay = 12))
        trips = CommuteTrips(CommuteMode.OUTBOUND, outbound = listOf(late, option(leg(line = "R1", dep = t(5, 8, 50)))))
        val told = rule.evaluate(RuleInput(fresh, now), null).newState!!
        val gone = t(5, 7, 55)
        assertTrue("not on a failed refresh", rule.evaluate(RuleInput(failed, gone), told).cancel.isEmpty())
        val left = rule.evaluate(RuleInput(fresh, gone), told)
        assertEquals(listOf(2_000), left.cancel)
        assertNull(left.newState)

        // The last train of the day left: nothing upcoming, withdrawn too. / 末班车开走了，再没有车：同样撤回。
        trips = CommuteTrips(CommuteMode.OUTBOUND, outbound = listOf(late))
        assertEquals(listOf(2_000), rule.evaluate(RuleInput(fresh, gone), told).cancel)
        // With nothing told there is nothing to take down. / 没提醒过就没什么可撤。
        assertTrue(rule.evaluate(RuleInput(fresh, gone), null).cancel.isEmpty())

        // Night falls while a late train is still ahead: the alert stays until it has left.
        // 入夜时那班晚点的车还没开：通知保留，直到它开走。
        val evening = option(leg(line = "R1", dep = t(5, 21, 55), delay = 12))
        trips = CommuteTrips(CommuteMode.OUTBOUND, outbound = listOf(evening, option(leg(line = "R1", dep = t(5, 22, 40)))))
        val toldEvening = rule.evaluate(RuleInput(fresh, t(5, 21, 50)), null).newState!!
        val stillAhead = rule.evaluate(RuleInput(fresh, t(5, 22, 2)), toldEvening)
        assertTrue(stillAhead.cancel.isEmpty())
        assertEquals(toldEvening, stillAhead.newState)
        assertEquals(listOf(2_000), rule.evaluate(RuleInput(fresh, t(5, 22, 15)), toldEvening).cancel)
    }

    // Morning brief / 早间简报

    @Test
    fun `the brief names the next trip to work and its worst leg`() {
        val onTime = CommuteTrips(CommuteMode.OUTBOUND, outbound = listOf(option(leg(line = "R1", dep = t(5, 7, 42)))))
        val onTimeText = UiText.Res(R.string.transit_on_time)
        assertEquals(UiText.Res(R.string.transit_brief, listOf("R1", "07:42", onTimeText)), TransitBrief.line(onTime, now))

        val late = CommuteTrips(
            CommuteMode.OUTBOUND,
            outbound = listOf(option(leg(line = "R1", dep = t(5, 7, 42)), leg(line = "L2", dep = t(5, 8, 20), delay = 6))),
        )
        val lateText = UiText.Res(R.string.transit_line_status, listOf("L2", UiText.Plural(R.plurals.transit_late, 6)))
        assertEquals(UiText.Res(R.string.transit_brief, listOf("R1", "07:42", lateText)), TransitBrief.line(late, now))

        assertNull("nothing left today", TransitBrief.line(CommuteTrips(CommuteMode.OUTBOUND), now))
    }

    @Test
    fun `a brief set for the afternoon reads the way home`() {
        val home = CommuteTrips(CommuteMode.INBOUND, inbound = listOf(option(leg(line = "R1", dep = t(5, 7, 42)))))
        assertEquals(UiText.Res(R.string.transit_brief, listOf("R1", "07:42", UiText.Res(R.string.transit_on_time))), TransitBrief.line(home, now))
    }
}
