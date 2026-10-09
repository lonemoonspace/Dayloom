package io.github.lonemoonspace.dayloom.feature.football.domain

import kotlinx.serialization.Serializable

/**
 * A team as football-data.org names it; names are proper nouns from the API and shown as they are.
 * football-data.org 给出的球队；名称是接口给的专有名词，原样显示。
 */
@Serializable
data class TeamRef(
    val id: Int = 0,
    val name: String = "",
    val shortName: String = "",
    /** Three-letter code, e.g. `RMA`; also the fallback when the crest cannot be shown. / 三字母代码，如 `RMA`；队徽显示不出来时也用它。 */
    val tla: String = "",
    val crest: String = "",
) {
    /** The shortest readable name. / 最短的可读名称。 */
    val label: String get() = shortName.ifBlank { name }.ifBlank { tla }
}

/** football-data.org match statuses; anything new falls back to [UNKNOWN]. / football-data.org 的比赛状态；新出现的值退回 [UNKNOWN]。 */
enum class MatchStatus {
    SCHEDULED, TIMED, IN_PLAY, PAUSED, FINISHED, AWARDED, POSTPONED, SUSPENDED, CANCELLED, UNKNOWN;

    val isLive: Boolean get() = this == IN_PLAY || this == PAUSED
    val isFinished: Boolean get() = this == FINISHED || this == AWARDED
    val isUpcoming: Boolean get() = this == SCHEDULED || this == TIMED
}

/**
 * One match of the followed team. Goals are the score of play (regular and extra time) without the shoot-out, which is kept
 * apart in [homePens] / [awayPens].
 * 关注球队的一场比赛。进球数是比赛进程中的比分（常规时间加加时），不含点球大战；点球大战另存在 [homePens] / [awayPens]。
 */
@Serializable
data class Match(
    val id: Long = 0,
    /** Kick-off, epoch millis. / 开球时刻，epoch 毫秒。 */
    val kickoff: Long = 0,
    val status: MatchStatus = MatchStatus.UNKNOWN,
    val competition: String = "",
    val competitionCode: String = "",
    val matchday: Int? = null,
    val home: TeamRef = TeamRef(),
    val away: TeamRef = TeamRef(),
    val homeGoals: Int? = null,
    val awayGoals: Int? = null,
    val homePens: Int? = null,
    val awayPens: Int? = null,
    /** Minute of play while live, when the API reports it. / 进行中时的比赛分钟（接口提供时）。 */
    val minute: Int? = null,
)

/** The snapshot of `football.matches`: the followed team's recent and coming matches. / `football.matches` 的快照：关注球队近期与接下来的比赛。 */
@Serializable
data class TeamMatches(val teamId: Int = 0, val matches: List<Match> = emptyList())

@Serializable
data class TableRow(
    val position: Int = 0,
    val team: TeamRef = TeamRef(),
    val played: Int = 0,
    val won: Int = 0,
    val draw: Int = 0,
    val lost: Int = 0,
    val goalDifference: Int = 0,
    val points: Int = 0,
)

/** One table; leagues have one, group stages several, each with its group name. / 一张积分表；联赛只有一张，小组赛有多张，各带组名。 */
@Serializable
data class TableGroup(val group: String = "", val rows: List<TableRow> = emptyList())

/** The snapshot of `football.standings`. / `football.standings` 的快照。 */
@Serializable
data class Standings(val competitionCode: String = "", val competition: String = "", val groups: List<TableGroup> = emptyList())

/** How a finished match went for the followed team. / 已结束的比赛对关注球队而言的结果。 */
enum class Outcome { WIN, DRAW, LOSS }
