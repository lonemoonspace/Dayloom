package io.github.lonemoonspace.dayloom.feature.football.domain

import io.github.lonemoonspace.dayloom.core.refresh.RefreshCadence
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Pure football decisions: what to show, how often to refresh, what a result means for the followed team.
 * 足球的纯判定逻辑：显示什么、多久刷新一次、比赛结果对关注球队意味着什么。
 */
object FootballPolicy {

    /** The free competitions of football-data.org, in the order the picker lists them. / football-data.org 的免费赛事，按选择列表的顺序。 */
    val FREE_COMPETITIONS = listOf("PL", "PD", "BL1", "SA", "FL1", "CL", "DED", "PPL", "ELC", "BSA", "EC", "WC")

    /** Matches fetched around today: four weeks back for the form, five ahead for the fixtures. / 抓取今天前后的比赛：往前四周看状态，往后五周看赛程。 */
    const val PAST_DAYS = 28L
    const val FUTURE_DAYS = 35L

    const val RECENT_SHOWN = 5
    const val UPCOMING_SHOWN = 5

    /** A kick-off reminder goes out within this time before the start. / 开球前这么久以内发出开赛提醒。 */
    val KICKOFF_LEAD: Duration = Duration.ofMinutes(60)

    /** A result older than this is not news any more. / 比这更早结束的比赛不再算新消息。 */
    val RESULT_FRESH: Duration = Duration.ofHours(8)

    /** A match normally ends within this time of kick-off, extra time and pauses included. / 一场比赛通常在开球后这么久内结束（含加时与暂停）。 */
    private val MATCH_SPAN: Duration = Duration.ofHours(3)

    /**
     * Live: every minute (the home screen and the tab check each minute; the free tier allows ten requests a minute).
     * Around a match: every 15 minutes, so the final score and kick-off reminders are not late. Otherwise every three hours.
     * 进行中：每分钟一次（首页与标签页每分钟检查一次；免费档每分钟允许十次请求）。比赛前后：每 15 分钟，终场比分与开赛提醒
     * 不会迟到。其余时间每三小时一次。
     */
    fun cadence(matches: List<Match>, now: Instant): RefreshCadence = when {
        matches.any { it.status.isLive || isUnderway(it, now) } -> RefreshCadence(interval = Duration.ofMinutes(1))
        matches.any { isAround(it, now) } -> RefreshCadence(interval = Duration.ofMinutes(15))
        else -> RefreshCadence(interval = Duration.ofHours(3))
    }

    /** Should have started and not be over yet, even when the status lags behind. / 按时间应已开赛、还没结束，即使状态还没更新。 */
    private fun isUnderway(match: Match, now: Instant): Boolean {
        if (match.status.isFinished || match.status == MatchStatus.POSTPONED || match.status == MatchStatus.CANCELLED) return false
        val kickoff = Instant.ofEpochMilli(match.kickoff)
        return !now.isBefore(kickoff) && now.isBefore(kickoff.plus(MATCH_SPAN))
    }

    private fun isAround(match: Match, now: Instant): Boolean {
        val kickoff = Instant.ofEpochMilli(match.kickoff)
        return now.isAfter(kickoff.minus(KICKOFF_LEAD).minus(Duration.ofMinutes(30))) && now.isBefore(kickoff.plus(MATCH_SPAN).plus(Duration.ofHours(1)))
    }

    fun recent(matches: List<Match>, limit: Int = RECENT_SHOWN): List<Match> =
        matches.filter { it.status.isFinished }.sortedByDescending { it.kickoff }.take(limit)

    /** Not finished yet, live first, then by kick-off; postponed and cancelled ones stay so the user sees why. / 未结束的比赛，进行中的在前，再按开球时间；延期与取消的保留，用户才知道原因。 */
    fun upcoming(matches: List<Match>, now: Instant, limit: Int = UPCOMING_SHOWN): List<Match> =
        matches.filter { !it.status.isFinished && (it.status.isLive || it.kickoff >= now.minus(MATCH_SPAN).toEpochMilli()) }
            .sortedWith(compareByDescending<Match> { it.status.isLive }.thenBy { it.kickoff })
            .take(limit)

    /**
     * The match worth a home card: one being played, else the next one within a day, else one finished in the last twelve
     * hours; null keeps the card out of the way.
     * 值得占一张首页卡片的比赛：正在进行的；否则一天内要开的下一场；否则十二小时内刚结束的；为 null 时卡片不占位置。
     */
    fun homeMatch(matches: List<Match>, now: Instant): Match? {
        matches.firstOrNull { it.status.isLive }?.let { return it }
        val nowMillis = now.toEpochMilli()
        matches.filter { it.status.isUpcoming && it.kickoff >= nowMillis - MATCH_SPAN.toMillis() && it.kickoff <= nowMillis + Duration.ofDays(1).toMillis() }
            .minByOrNull { it.kickoff }?.let { return it }
        return matches.filter { it.status.isFinished && nowMillis - it.kickoff <= Duration.ofHours(12).plus(MATCH_SPAN).toMillis() }
            .maxByOrNull { it.kickoff }
    }

    /** Null for a match that has not been decided. / 尚未分出结果的比赛为 null。 */
    fun outcome(match: Match, teamId: Int): Outcome? {
        if (!match.status.isFinished) return null
        val home = match.homeGoals ?: return null
        val away = match.awayGoals ?: return null
        val ours = if (match.home.id == teamId) home else if (match.away.id == teamId) away else return null
        val theirs = if (match.home.id == teamId) away else home
        if (ours != theirs) return if (ours > theirs) Outcome.WIN else Outcome.LOSS
        // A shoot-out decides a drawn knockout match. / 淘汰赛平局由点球大战决定。
        val pensOurs = if (match.home.id == teamId) match.homePens else match.awayPens
        val pensTheirs = if (match.home.id == teamId) match.awayPens else match.homePens
        if (pensOurs != null && pensTheirs != null && pensOurs != pensTheirs) return if (pensOurs > pensTheirs) Outcome.WIN else Outcome.LOSS
        return Outcome.DRAW
    }

    /** The other team, seen from the followed one. / 从关注球队看的对手。 */
    fun opponent(match: Match, teamId: Int): TeamRef = if (match.home.id == teamId) match.away else match.home

    fun isHome(match: Match, teamId: Int): Boolean = match.home.id == teamId

    /**
     * The match to remind about: not started, kicking off within [KICKOFF_LEAD], not reminded yet.
     * 要提醒的比赛：还没开始、[KICKOFF_LEAD] 内开球、尚未提醒过。
     */
    fun kickoffDue(matches: List<Match>, now: ZonedDateTime, notified: Set<Long>): Match? {
        val nowMillis = now.toInstant().toEpochMilli()
        return matches.filter { it.status.isUpcoming && it.id !in notified }
            .filter { it.kickoff > nowMillis && it.kickoff - nowMillis <= KICKOFF_LEAD.toMillis() }
            .minByOrNull { it.kickoff }
    }

    /**
     * The result to announce: finished, kicked off recently enough to be news, not announced yet. Old results are never
     * announced, so turning the switch on does not replay last week.
     * 要通知的结果：已结束、开球时间近到还算新消息、尚未通知过。旧结果永不通知，打开开关时不会把上周的比赛再报一遍。
     */
    fun resultDue(matches: List<Match>, now: ZonedDateTime, notified: Set<Long>): Match? {
        val nowMillis = now.toInstant().toEpochMilli()
        return matches.filter { it.status.isFinished && it.id !in notified && it.homeGoals != null && it.awayGoals != null }
            .filter { nowMillis - it.kickoff <= MATCH_SPAN.plus(RESULT_FRESH).toMillis() }
            .maxByOrNull { it.kickoff }
    }

    /** Ids remembered by the rules: enough for a season's worth of overlapping fetches, without growing forever. / 规则记住的 id：足够覆盖相互重叠的多次抓取，又不会无限增长。 */
    fun remember(notified: Set<Long>, id: Long): Set<Long> = (notified.sortedDescending().take(MEMORY - 1) + id).toSet()

    /**
     * football-data.org v4 counts a shoot-out into `fullTime`. The score of play is regular plus extra time where given,
     * otherwise full time minus the shoot-out.
     * football-data.org v4 把点球大战计入 `fullTime`。比赛进程中的比分：有常规时间与加时数据时取两者之和，否则用全场减去点球大战。
     */
    fun scoreOfPlay(
        duration: String?,
        fullTime: Pair<Int?, Int?>,
        regularTime: Pair<Int?, Int?>?,
        extraTime: Pair<Int?, Int?>?,
        penalties: Pair<Int?, Int?>?,
    ): Pair<Int?, Int?> {
        if (duration != "PENALTY_SHOOTOUT" || penalties?.first == null || penalties.second == null) return fullTime
        val regular = regularTime
        if (regular?.first != null && regular.second != null) {
            return (regular.first!! + (extraTime?.first ?: 0)) to (regular.second!! + (extraTime?.second ?: 0))
        }
        val home = fullTime.first ?: return fullTime
        val away = fullTime.second ?: return fullTime
        return (home - penalties.first!!) to (away - penalties.second!!)
    }

    private const val MEMORY = 40
}
