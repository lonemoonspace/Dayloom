package io.github.lonemoonspace.dayloom.feature.football.data

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.i18n.uiText
import io.github.lonemoonspace.dayloom.core.refresh.CachedSource
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCadence
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.refresh.SourceInput
import io.github.lonemoonspace.dayloom.core.secret.SecretState
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.storage.SnapshotStore
import io.github.lonemoonspace.dayloom.core.time.AppClock
import io.github.lonemoonspace.dayloom.feature.football.domain.FootballPolicy
import io.github.lonemoonspace.dayloom.feature.football.domain.ScorersSet
import io.github.lonemoonspace.dayloom.feature.football.domain.StandingsSet
import io.github.lonemoonspace.dayloom.feature.football.domain.TeamMatches
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Objects
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Builds the input of a key-protected source: missing [what] or a missing key is a setup prompt, never an error; the key
 * only enters [SourceInput.Ready.refreshKey] as a hash, so a new key refetches.
 * 生成需要 Key 的来源的输入：缺 [what] 或缺 Key 都是设置提示而不是错误；Key 只以哈希形式进入 [SourceInput.Ready.refreshKey]，
 * 换了 Key 就会重抓。
 */
internal fun <P> keyedInput(params: P?, key: String, secret: SecretState, missing: Int): SourceInput<P> {
    if (params == null) return SourceInput.Missing(uiText(missing))
    if (secret.display.isBlank()) return SourceInput.Missing(uiText(R.string.football_setup_key))
    return SourceInput.Ready(params, key = key, refreshKey = key to Objects.hash(secret.display, secret.unreadable))
}

/**
 * `football.matches`: the followed team's matches from four weeks back to five weeks ahead. Its cadence follows the
 * schedule ([FootballPolicy.cadence]): every minute while a match is on, so the score is live on the home card and in the tab.
 * `football.matches`：关注球队往前四周到往后五周的比赛。刷新节奏随赛程变化（[FootballPolicy.cadence]）：比赛进行中每分钟一次，
 * 首页卡片与标签页的比分因此是实时的。
 */
class MatchesSource(
    id: SourceId,
    store: SnapshotStore<TeamMatches>,
    clock: AppClock,
    teamId: Flow<Int>,
    secret: Flow<SecretState>,
    private val apiKey: suspend () -> String,
    private val api: FootballDataApi,
) : CachedSource<Int, TeamMatches>(id, store, clock) {

    override val schemaVersion = 1

    override val maxAge: Duration = Duration.ofHours(12)

    override fun cadence(current: Snapshot<TeamMatches>?): RefreshCadence =
        FootballPolicy.cadence(current?.value?.matches.orEmpty(), clock.now().toInstant())

    override val inputs: Flow<SourceInput<Int>> = combine(teamId, secret) { team, s ->
        keyedInput(team.takeIf { it > 0 }, key = "team:$team", secret = s, missing = R.string.football_setup_team)
    }

    override suspend fun fetch(params: Int, now: ZonedDateTime, previous: Snapshot<TeamMatches>?): TeamMatches {
        val key = apiKey().ifBlank { throw AppError.NotConfigured(uiText(R.string.football_setup_key)) }
        val today = now.toLocalDate()
        val matches = api.teamMatches(key, params, today.minusDays(FootballPolicy.PAST_DAYS), today.plusDays(FootballPolicy.FUTURE_DAYS))
        return TeamMatches(teamId = params, matches = matches.sortedBy { it.kickoff })
    }
}

/** The followed team and the competition it was picked from. / 关注的球队与挑选它时所在的赛事。 */
data class StandingsParams(val teamId: Int, val league: String)

/**
 * Fetches one entry per competition in [codes]: the first (the league) must load, the others fall back to [previous] on an
 * error, so a passing 429 never takes the Champions League table away for six hours.
 * 按 [codes] 每项赛事抓一份：第一项（联赛）必须成功，其余出错时沿用 [previous]，这样一次偶然的 429 不会让欧冠积分榜消失六个小时。
 */
internal suspend fun <T> fetchEach(codes: List<String>, previous: Map<String, T>, fetch: suspend (String) -> T): List<T> {
    val fresh = mutableMapOf<String, T>()
    codes.forEachIndexed { i, code ->
        try {
            fresh[code] = fetch(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppError) {
            if (i == 0) throw e
        }
    }
    return FootballPolicy.withPrevious(codes, fresh, previous)
}

/**
 * `football.scorers`: the top scorers of every competition in the table snapshot, on the same cadence as the tables.
 * `football.scorers`：积分榜快照里每项赛事的射手榜，刷新节奏与积分榜相同。
 */
class ScorersSource(
    id: SourceId,
    store: SnapshotStore<ScorersSet>,
    clock: AppClock,
    competition: Flow<String>,
    /** Competitions of the current table snapshot, empty before it loads. / 当前积分榜快照里的赛事，加载前为空。 */
    tableCodes: Flow<List<String>>,
    secret: Flow<SecretState>,
    private val apiKey: suspend () -> String,
    private val api: FootballDataApi,
) : CachedSource<List<String>, ScorersSet>(id, store, clock) {

    override val schemaVersion = 2

    override val maxAge: Duration = Duration.ofDays(2)

    override val cadence = RefreshCadence(interval = Duration.ofHours(6))

    override val inputs: Flow<SourceInput<List<String>>> = combine(competition, tableCodes, secret) { code, codes, s ->
        val list = FootballPolicy.competitions(code, codes).takeIf { code.isNotBlank() }
        keyedInput(list, key = "competitions:${list.orEmpty().joinToString(",")}", secret = s, missing = R.string.football_setup_competition)
    }

    override suspend fun fetch(params: List<String>, now: ZonedDateTime, previous: Snapshot<ScorersSet>?): ScorersSet {
        val key = apiKey().ifBlank { throw AppError.NotConfigured(uiText(R.string.football_setup_key)) }
        val before = previous?.value?.tables.orEmpty().associateBy { it.competitionCode }
        return ScorersSet(fetchEach(params, before) { code -> api.scorers(key, code) })
    }
}

/**
 * `football.standings`: the table of the competition the team was picked from, then those of the other free competitions
 * it plays in (the Champions League for most top clubs). Which ones is asked of the API on every fetch; if that request
 * fails the previous list is kept.
 * `football.standings`：挑选球队时所在赛事的积分表，再加上它参加的其他免费赛事（多数豪门是欧冠）。参加哪些赛事每次抓取时都问一次接口；
 * 这一请求失败时沿用上次的列表。
 */
class StandingsSource(
    id: SourceId,
    store: SnapshotStore<StandingsSet>,
    clock: AppClock,
    params: Flow<StandingsParams>,
    secret: Flow<SecretState>,
    private val apiKey: suspend () -> String,
    private val api: FootballDataApi,
) : CachedSource<StandingsParams, StandingsSet>(id, store, clock) {

    override val schemaVersion = 2

    override val maxAge: Duration = Duration.ofDays(2)

    // Tables change after match days; every six hours keeps them current at a fraction of the request budget.
    // 积分表在比赛日之后才变；每六小时一次就够新，只占请求额度的一小部分。
    override val cadence = RefreshCadence(interval = Duration.ofHours(6))

    override val inputs: Flow<SourceInput<StandingsParams>> = combine(params, secret) { p, s ->
        val ready = p.takeIf { it.teamId > 0 && it.league.isNotBlank() }
        keyedInput(ready, key = "team:${p.teamId}:competition:${p.league}", secret = s, missing = R.string.football_setup_competition)
    }

    override suspend fun fetch(params: StandingsParams, now: ZonedDateTime, previous: Snapshot<StandingsSet>?): StandingsSet {
        val key = apiKey().ifBlank { throw AppError.NotConfigured(uiText(R.string.football_setup_key)) }
        val before = previous?.value?.tables.orEmpty()
        val running = try {
            api.runningCompetitions(key, params.teamId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: AppError) {
            before.map { it.competitionCode }
        }
        val codes = FootballPolicy.competitions(params.league, running)
        return StandingsSet(params.teamId, fetchEach(codes, before.associateBy { it.competitionCode }) { code -> api.standings(key, code) })
    }
}
