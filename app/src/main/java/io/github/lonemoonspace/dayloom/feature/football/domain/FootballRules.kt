package io.github.lonemoonspace.dayloom.feature.football.domain

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.notify.AppNotification
import io.github.lonemoonspace.dayloom.core.notify.JsonStateCodec
import io.github.lonemoonspace.dayloom.core.notify.NotificationRule
import io.github.lonemoonspace.dayloom.core.notify.RuleDecision
import io.github.lonemoonspace.dayloom.core.notify.RuleInput
import io.github.lonemoonspace.dayloom.core.notify.StateCodec
import io.github.lonemoonspace.dayloom.core.refresh.RefreshReport
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

/**
 * What a rule has already announced: match ids for results, and for kick-offs the kick-off time each reminder was for, so
 * a match moved to another day is reminded again.
 * 规则已经通知过的内容：结果按比赛 id 记；开赛提醒还记下当时提醒的开球时间，比赛改期后会按新时间再提醒一次。
 */
@Serializable
data class AnnouncedMatches(val ids: Set<Long> = emptySet(), val kickoffs: Map<Long, Long> = emptyMap())

/**
 * Where the football notifications go; both rules share one id, so the final score replaces the kick-off reminder.
 * 足球通知的去向；两条规则共用一个 id，终场比分会替换掉开赛提醒。
 */
data class FootballNotifyTarget(val channelId: String, val deepLink: String, val notificationId: Int)

/** "Real Madrid – Barcelona", home team first as on every fixture list. / 「皇马 – 巴萨」，与所有赛程表一样主队在前。 */
internal fun fixture(match: Match): UiText = UiText.Res(R.string.football_fixture, listOf(match.home.label, match.away.label))

/**
 * Kick-off reminder: once per match, within [FootballPolicy.KICKOFF_LEAD] of the start. Uses the last snapshot even if this
 * round did not refresh it, because the schedule rarely moves in the last hour.
 * 开赛提醒：每场一次，开球前 [FootballPolicy.KICKOFF_LEAD] 内发出。即使本轮没有刷新也用最近的快照，因为赛程很少在最后一小时变动。
 */
class KickoffRule(
    private val enabled: suspend () -> Boolean,
    private val matches: suspend () -> List<Match>?,
    private val target: FootballNotifyTarget,
) : NotificationRule<AnnouncedMatches> {
    override val name = "kickoff"
    override val codec: StateCodec<AnnouncedMatches> = JsonStateCodec(AnnouncedMatches.serializer(), AnnouncedMatches())

    override suspend fun isEnabled(): Boolean = enabled()

    override suspend fun evaluate(input: RuleInput, previous: AnnouncedMatches): RuleDecision<AnnouncedMatches> {
        val match = FootballPolicy.kickoffDue(matches().orEmpty(), input.now, previous.kickoffs) ?: return RuleDecision(previous)
        val at = Instant.ofEpochMilli(match.kickoff).atZone(input.now.zone).format(TIME)
        val body = UiText.Res(R.string.football_notify_kickoff_body, listOf(fixture(match), at, match.competition))
        val notification = AppNotification(target.notificationId, target.channelId, UiText.Res(R.string.football_notify_kickoff), body, target.deepLink)
        return RuleDecision(previous.copy(kickoffs = FootballPolicy.rememberKickoff(previous.kickoffs, match)), listOf(notification))
    }
}

/**
 * Final score: once per match, only from a snapshot refreshed in this round, so a stale "live" score is never announced as
 * final.
 * 终场比分：每场一次，只看本轮刷新成功的快照，过期的「进行中」比分绝不会被当成终场通知。
 */
class ResultRule(
    private val enabled: suspend () -> Boolean,
    private val refreshed: (RefreshReport) -> Boolean,
    private val matches: suspend () -> List<Match>?,
    private val teamId: suspend () -> Int,
    private val target: FootballNotifyTarget,
) : NotificationRule<AnnouncedMatches> {
    override val name = "result"
    override val codec: StateCodec<AnnouncedMatches> = JsonStateCodec(AnnouncedMatches.serializer(), AnnouncedMatches())

    override suspend fun isEnabled(): Boolean = enabled()

    override suspend fun evaluate(input: RuleInput, previous: AnnouncedMatches): RuleDecision<AnnouncedMatches> {
        if (!refreshed(input.report)) return RuleDecision(previous)
        val match = FootballPolicy.resultDue(matches().orEmpty(), input.now, previous.ids) ?: return RuleDecision(previous)
        val title = when (FootballPolicy.outcome(match, teamId())) {
            Outcome.WIN -> R.string.football_notify_win
            Outcome.DRAW -> R.string.football_notify_draw
            Outcome.LOSS -> R.string.football_notify_loss
            null -> R.string.football_notify_result
        }
        val notification = AppNotification(target.notificationId, target.channelId, UiText.Res(title), resultText(match), target.deepLink)
        return RuleDecision(AnnouncedMatches(FootballPolicy.remember(previous.ids, match.id)), listOf(notification))
    }
}

/** "Real Madrid 2–1 Barcelona", with the shoot-out when there was one. / 「皇马 2–1 巴萨」，有点球大战时附上。 */
internal fun resultText(match: Match): UiText {
    val score = UiText.Res(
        R.string.football_result_line,
        listOf(match.home.label, match.homeGoals ?: 0, match.awayGoals ?: 0, match.away.label),
    )
    val homePens = match.homePens
    val awayPens = match.awayPens
    return if (homePens != null && awayPens != null) UiText.Res(R.string.football_with_penalties, listOf(score, homePens, awayPens)) else score
}

/** Numeric time reads the same in every language. / 数字时刻在各语言里写法一致。 */
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
