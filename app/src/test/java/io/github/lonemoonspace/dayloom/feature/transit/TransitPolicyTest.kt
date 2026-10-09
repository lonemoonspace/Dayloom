package io.github.lonemoonspace.dayloom.feature.transit

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.notify.RuleInput
import io.github.lonemoonspace.dayloom.core.refresh.RefreshReport
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.refresh.SourceResult
import io.github.lonemoonspace.dayloom.core.routine.DailyWindow
import io.github.lonemoonspace.dayloom.core.routine.Routine
import io.github.lonemoonspace.dayloom.feature.transit.domain.BoardDeparture
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteMode
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteTrips
import io.github.lonemoonspace.dayloom.feature.transit.domain.DisruptionPolicy
import io.github.lonemoonspace.dayloom.feature.transit.domain.DisruptionRule
import io.github.lonemoonspace.dayloom.feature.transit.domain.FavouriteBoard
import io.github.lonemoonspace.dayloom.feature.transit.domain.LegState
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
    fun `the commute mode follows the daily windows`() {
        val routine = Routine(toWork = DailyWindow(7 * 60, 9 * 60), backHome = DailyWindow(16 * 60, 18 * 60))
        assertEquals(CommuteMode.OUTBOUND, TransitPolicy.commuteMode(routine, t(5, 7, 30)))
        assertEquals(CommuteMode.INBOUND, TransitPolicy.commuteMode(routine, t(5, 17)))
        assertEquals(CommuteMode.BOTH, TransitPolicy.commuteMode(routine, t(5, 12)))
        assertEquals("weekends too", CommuteMode.OUTBOUND, TransitPolicy.commuteMode(routine, t(10, 7, 30)))
    }

    @Test
    fun `visible options drop departed ones and keep the limit`() {
        val gone = TripOption(listOf(leg(dep = now.minusMinutes(1))))
        val soon = TripOption(listOf(leg(dep = now.plusMinutes(2))))
        val later = TripOption(listOf(leg(dep = now.plusMinutes(12))))
        val cancelled = TripOption(listOf(leg(dep = now.plusMinutes(5), cancelled = true)))
        assertEquals(listOf(soon, cancelled), TransitPolicy.visibleOptions(listOf(later, gone, cancelled, soon), now, 2))
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
    fun `the rule only acts on a fresh snapshot inside a window and sends structured text`() = runTest {
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

        trips = trips.copy(mode = CommuteMode.BOTH)
        assertEquals(worse.newState, rule.evaluate(RuleInput(fresh, now), worse.newState).newState)
        assertTrue(rule.evaluate(RuleInput(fresh, now), null).notifications.isEmpty())
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

        // Train and bus share the module's one line. / 火车与公交合用本模块的一行。
        val bus = CommuteTrips(CommuteMode.OUTBOUND, outbound = listOf(option(leg(line = "31", dep = t(5, 7, 50)))))
        assertEquals(
            UiText.Res(R.string.transit_brief_joined, listOf(TransitBrief.line(onTime, now)!!, TransitBrief.line(bus, now)!!)),
            TransitBrief.lines(listOf(onTime, bus), now),
        )
        assertEquals(TransitBrief.line(bus, now), TransitBrief.lines(listOf(CommuteTrips(CommuteMode.OUTBOUND), bus), now))
        assertNull(TransitBrief.lines(emptyList(), now))
    }
}
