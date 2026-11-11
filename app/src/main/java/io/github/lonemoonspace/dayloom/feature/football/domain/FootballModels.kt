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
    /** Stoppage time added to [minute], e.g. 3 for 90+3. / 加在 [minute] 上的补时，例如 90+3 里的 3。 */
    val injuryTime: Int? = null,
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
data class Standings(
    val competitionCode: String = "",
    val competition: String = "",
    val groups: List<TableGroup> = emptyList(),
    /** The season's current matchday, when the competition has one. / 本赛季当前轮次（赛事有轮次时）。 */
    val matchday: Int? = null,
    /** The API's stage of the tables, e.g. `REGULAR_SEASON` or `LEAGUE_STAGE`. / 接口给的积分表阶段，例如 `REGULAR_SEASON` 或 `LEAGUE_STAGE`。 */
    val stage: String = "",
    /**
     * When this table was fetched, epoch millis; a table kept from an earlier round after a failed request keeps its own
     * time, so the tab does not call it fresh.
     * 这张表的抓取时刻，epoch 毫秒；请求失败后沿用的旧表保留自己的时刻，标签页就不会把它当成新数据。
     */
    val fetchedAt: Long = 0,
)

/**
 * The snapshot of `football.standings`: one table per competition the team plays in, its league first.
 * `football.standings` 的快照：球队参加的每项赛事各一张表，联赛排在最前。
 */
@Serializable
data class StandingsSet(val teamId: Int = 0, val tables: List<Standings> = emptyList()) {
    val codes: List<String> get() = tables.map { it.competitionCode }
}

/** One line of the scorers list; missing counts stay null rather than zero. / 射手榜的一行；缺失的数字保持为空而不是 0。 */
@Serializable
data class Scorer(
    val player: String = "",
    val team: TeamRef = TeamRef(),
    val playedMatches: Int? = null,
    val goals: Int = 0,
    val penalties: Int? = null,
)

/** The snapshot of `football.scorers`: the competition's top scorers. / `football.scorers` 的快照：赛事射手榜。 */
@Serializable
data class Scorers(
    val competitionCode: String = "",
    val competition: String = "",
    val matchday: Int? = null,
    val scorers: List<Scorer> = emptyList(),
    /** As [Standings.fetchedAt]. / 同 [Standings.fetchedAt]。 */
    val fetchedAt: Long = 0,
)

/**
 * The snapshot of `football.scorers`: one scorers list per competition, in the order of [StandingsSet].
 * `football.scorers` 的快照：每项赛事一张射手榜，顺序与 [StandingsSet] 相同。
 */
@Serializable
data class ScorersSet(val tables: List<Scorers> = emptyList())

/**
 * Where a table position leads in a UEFA league phase; leagues get [NONE] because their places differ by country and year.
 * 欧战联赛阶段里某个名次的去向；国内联赛一律 [NONE]，因为各国、各年的名额都不同。
 */
enum class TableZone { QUALIFY, PLAYOFF, NONE }

/** How a finished match went for the followed team. / 已结束的比赛对关注球队而言的结果。 */
enum class Outcome { WIN, DRAW, LOSS }

/** Time left until kick-off. / 距开球还剩的时间。 */
data class Countdown(val days: Int, val hours: Int, val minutes: Int)
