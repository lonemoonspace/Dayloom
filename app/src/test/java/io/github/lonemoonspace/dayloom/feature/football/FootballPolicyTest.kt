package io.github.lonemoonspace.dayloom.feature.football

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.notify.RuleInput
import io.github.lonemoonspace.dayloom.core.refresh.RefreshReport
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.refresh.SourceResult
import io.github.lonemoonspace.dayloom.feature.football.domain.AnnouncedMatches
import io.github.lonemoonspace.dayloom.feature.football.domain.FootballBrief
import io.github.lonemoonspace.dayloom.feature.football.domain.FootballNotifyTarget
import io.github.lonemoonspace.dayloom.feature.football.domain.FootballPolicy
import io.github.lonemoonspace.dayloom.feature.football.domain.KickoffRule
import io.github.lonemoonspace.dayloom.feature.football.domain.Match
import io.github.lonemoonspace.dayloom.feature.football.domain.MatchStatus
import io.github.lonemoonspace.dayloom.feature.football.domain.Outcome
import io.github.lonemoonspace.dayloom.feature.football.domain.ResultRule
import io.github.lonemoonspace.dayloom.feature.football.domain.TeamRef
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballPolicyTest {

    private val zone = ZoneId.of("Europe/Oslo")
    private val now = ZonedDateTime.of(2026, 10, 10, 18, 0, 0, 0, zone)
    private val us = TeamRef(id = 1, name = "Team A FC", shortName = "Team A", tla = "TMA")
    private val them = TeamRef(id = 2, name = "Team B FC", shortName = "Team B", tla = "TMB")

    private fun match(
        id: Long,
        kickoff: ZonedDateTime,
        status: MatchStatus,
        home: TeamRef = us,
        away: TeamRef = them,
        goals: Pair<Int, Int>? = null,
        pens: Pair<Int, Int>? = null,
    ) = Match(
        id = id,
        kickoff = kickoff.toInstant().toEpochMilli(),
        status = status,
        competition = "League",
        competitionCode = "PL",
        home = home,
        away = away,
        homeGoals = goals?.first,
        awayGoals = goals?.second,
        homePens = pens?.first,
        awayPens = pens?.second,
    )

    @Test
    fun `the shoot-out is taken out of football-data's full-time score`() {
        // Regular and extra time given: their sum. / 给了常规时间与加时：取两者之和。
        assertEquals(1 to 1, FootballPolicy.scoreOfPlay("PENALTY_SHOOTOUT", 5 to 4, 1 to 1, 0 to 0, 4 to 3))
        // Only full time: subtract the shoot-out. / 只有全场：减去点球大战。
        assertEquals(2 to 2, FootballPolicy.scoreOfPlay("PENALTY_SHOOTOUT", 6 to 5, null, null, 4 to 3))
        assertEquals(2 to 1, FootballPolicy.scoreOfPlay("REGULAR", 2 to 1, 2 to 1, null, null))
        assertEquals(null to null, FootballPolicy.scoreOfPlay(null, null to null, null, null, null))
    }

    @Test
    fun `outcome from the followed team's side, shoot-outs decide draws`() {
        assertEquals(Outcome.WIN, FootballPolicy.outcome(match(1, now, MatchStatus.FINISHED, goals = 2 to 1), us.id))
        assertEquals(Outcome.LOSS, FootballPolicy.outcome(match(1, now, MatchStatus.FINISHED, home = them, away = us, goals = 2 to 1), us.id))
        assertEquals(Outcome.DRAW, FootballPolicy.outcome(match(1, now, MatchStatus.FINISHED, goals = 0 to 0), us.id))
        assertEquals(Outcome.LOSS, FootballPolicy.outcome(match(1, now, MatchStatus.FINISHED, goals = 1 to 1, pens = 3 to 4), us.id))
        assertNull("not finished", FootballPolicy.outcome(match(1, now, MatchStatus.IN_PLAY, goals = 1 to 0), us.id))
    }

    @Test
    fun `cadence follows the schedule`() {
        val far = listOf(match(1, now.plusDays(3), MatchStatus.TIMED))
        assertEquals(Duration.ofHours(3), FootballPolicy.cadence(far, now.toInstant()).interval)
        val soon = listOf(match(1, now.plusMinutes(70), MatchStatus.TIMED))
        assertEquals(Duration.ofMinutes(15), FootballPolicy.cadence(soon, now.toInstant()).interval)
        val live = listOf(match(1, now.minusMinutes(30), MatchStatus.IN_PLAY))
        assertEquals(Duration.ofMinutes(1), FootballPolicy.cadence(live, now.toInstant()).interval)
        // Kick-off passed but the status still says timed: treat as under way. / 已过开球时间但状态仍是待开赛：按进行中处理。
        val lagging = listOf(match(1, now.minusMinutes(10), MatchStatus.TIMED))
        assertEquals(Duration.ofMinutes(1), FootballPolicy.cadence(lagging, now.toInstant()).interval)
        val postponed = listOf(match(1, now.minusMinutes(10), MatchStatus.POSTPONED))
        assertEquals(Duration.ofMinutes(15), FootballPolicy.cadence(postponed, now.toInstant()).interval)
    }

    @Test
    fun `the home card shows live, then soon, then just finished, else nothing`() {
        val finished = match(1, now.minusHours(4), MatchStatus.FINISHED, goals = 1 to 0)
        val tomorrow = match(2, now.plusHours(20), MatchStatus.TIMED)
        val live = match(3, now.minusMinutes(20), MatchStatus.IN_PLAY, goals = 0 to 0)
        assertEquals(3L, FootballPolicy.homeMatch(listOf(finished, tomorrow, live), now.toInstant())?.id)
        assertEquals(2L, FootballPolicy.homeMatch(listOf(finished, tomorrow), now.toInstant())?.id)
        assertEquals(1L, FootballPolicy.homeMatch(listOf(finished), now.toInstant())?.id)
        assertNull(FootballPolicy.homeMatch(listOf(match(4, now.minusDays(2), MatchStatus.FINISHED, goals = 1 to 0)), now.toInstant()))
        assertNull(FootballPolicy.homeMatch(listOf(match(5, now.plusDays(3), MatchStatus.TIMED)), now.toInstant()))
    }

    @Test
    fun `a status stuck at live stops counting after the match window`() {
        val stuck = match(1, now.minusDays(2), MatchStatus.IN_PLAY, goals = 1 to 0)
        assertNull(FootballPolicy.homeMatch(listOf(stuck), now.toInstant()))
        assertEquals(Duration.ofHours(3), FootballPolicy.cadence(listOf(stuck), now.toInstant()).interval)
        assertTrue(FootballPolicy.isLiveNow(match(2, now.minusMinutes(80), MatchStatus.IN_PLAY), now.toInstant()))
    }

    @Test
    fun `fixtures put live first and results are newest first`() {
        val a = match(1, now.plusDays(2), MatchStatus.TIMED)
        val b = match(2, now.minusMinutes(30), MatchStatus.IN_PLAY)
        val c = match(3, now.minusDays(3), MatchStatus.FINISHED, goals = 1 to 1)
        val d = match(4, now.minusDays(10), MatchStatus.FINISHED, goals = 2 to 0)
        assertEquals(listOf(2L, 1L), FootballPolicy.upcoming(listOf(a, b, c, d), now.toInstant()).map { it.id })
        assertEquals(listOf(3L, 4L), FootballPolicy.recent(listOf(a, b, d, c)).map { it.id })
    }

    @Test
    fun `kick-off reminders and results are due once and only while fresh`() {
        val soon = match(1, now.plusMinutes(45), MatchStatus.TIMED)
        assertEquals(1L, FootballPolicy.kickoffDue(listOf(soon), now, emptySet())?.id)
        assertNull("already reminded", FootballPolicy.kickoffDue(listOf(soon), now, setOf(1L)))
        assertNull("too early", FootballPolicy.kickoffDue(listOf(match(2, now.plusHours(2), MatchStatus.TIMED)), now, emptySet()))

        val ended = match(3, now.minusHours(2), MatchStatus.FINISHED, goals = 2 to 0)
        assertEquals(3L, FootballPolicy.resultDue(listOf(ended), now, emptySet())?.id)
        assertNull("last week's result is not news", FootballPolicy.resultDue(listOf(match(4, now.minusDays(7), MatchStatus.FINISHED, goals = 1 to 0)), now, emptySet()))
        assertNull(FootballPolicy.resultDue(listOf(ended), now, setOf(3L)))
    }

    @Test
    fun `remembered ids stay bounded`() {
        val many = (1L..100L).fold(emptySet<Long>()) { acc, id -> FootballPolicy.remember(acc, id) }
        assertEquals(40, many.size)
        assertTrue(100L in many)
    }

    private val target = FootballNotifyTarget("football.matches", "dayloom://football", 3_000)

    @Test
    fun `the result rule names the outcome and the shoot-out`() = runTest {
        val id = SourceId("football.matches")
        val fresh = RefreshReport(mapOf(id to SourceResult.Success(Instant.EPOCH)), Instant.EPOCH)
        val ended = match(3, now.minusHours(2), MatchStatus.FINISHED, home = them, away = us, goals = 1 to 1, pens = 3 to 5)
        val rule = ResultRule({ true }, { it.succeeded(id) }, { listOf(ended) }, { us.id }, target)

        val decision = rule.evaluate(RuleInput(fresh, now), AnnouncedMatches())
        val note = decision.notifications.single()
        assertEquals(UiText.Res(R.string.football_notify_win), note.title)
        val score = UiText.Res(R.string.football_result_line, listOf("Team B", 1, 1, "Team A"))
        assertEquals(UiText.Res(R.string.football_with_penalties, listOf(score, 3, 5)), note.body)
        assertTrue(rule.evaluate(RuleInput(fresh, now), decision.newState).notifications.isEmpty())

        val stale = RefreshReport(emptyMap(), Instant.EPOCH)
        assertTrue("only from a fresh snapshot", rule.evaluate(RuleInput(stale, now), AnnouncedMatches()).notifications.isEmpty())
    }

    @Test
    fun `the kick-off rule sends once per match`() = runTest {
        val soon = match(1, now.plusMinutes(30), MatchStatus.TIMED)
        val rule = KickoffRule({ true }, { listOf(soon) }, target)
        val first = rule.evaluate(RuleInput(RefreshReport(emptyMap(), Instant.EPOCH), now), AnnouncedMatches())
        val fixture = UiText.Res(R.string.football_fixture, listOf("Team A", "Team B"))
        assertEquals(UiText.Res(R.string.football_notify_kickoff_body, listOf(fixture, "18:30", "League")), first.notifications.single().body)
        assertTrue(rule.evaluate(RuleInput(RefreshReport(emptyMap(), Instant.EPOCH), now), first.newState).notifications.isEmpty())
    }

    @Test
    fun `the brief speaks only on a match day`() {
        val tonight = match(1, now.plusHours(3), MatchStatus.TIMED)
        val fixture = UiText.Res(R.string.football_fixture, listOf("Team A", "Team B"))
        assertEquals(UiText.Res(R.string.football_brief, listOf(fixture, "21:00")), FootballBrief.line(listOf(tonight), now))
        assertNull(FootballBrief.line(listOf(match(2, now.plusDays(1), MatchStatus.TIMED)), now))
    }
}
