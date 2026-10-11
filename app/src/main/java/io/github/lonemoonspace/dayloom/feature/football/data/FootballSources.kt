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
import io.github.lonemoonspace.dayloom.feature.football.domain.Match
import io.github.lonemoonspace.dayloom.feature.football.domain.Standings
import io.github.lonemoonspace.dayloom.feature.football.domain.TeamMatches
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Objects
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

    /** The last known matches, kept by the module from [observe] so the cadence can follow them. / 最近一次的比赛列表，由模块从 [observe] 更新，刷新节奏据此变化。 */
    @Volatile
    var known: List<Match> = emptyList()

    override val cadence: RefreshCadence get() = FootballPolicy.cadence(known, clock.now().toInstant())

    override val inputs: Flow<SourceInput<Int>> = combine(teamId, secret) { team, s ->
        keyedInput(team.takeIf { it > 0 }, key = "team:$team", secret = s, missing = R.string.football_setup_team)
    }

    override suspend fun fetch(params: Int, now: ZonedDateTime, previous: Snapshot<TeamMatches>?): TeamMatches {
        val key = apiKey().ifBlank { throw AppError.NotConfigured(uiText(R.string.football_setup_key)) }
        val today = now.toLocalDate()
        val matches = api.teamMatches(key, params, today.minusDays(FootballPolicy.PAST_DAYS), today.plusDays(FootballPolicy.FUTURE_DAYS))
        return TeamMatches(teamId = params, matches = matches.sortedBy { it.kickoff }).also { known = it.matches }
    }
}

/**
 * `football.standings`: the table of the competition the team was picked from.
 * `football.standings`：挑选球队时所在赛事的积分表。
 */
class StandingsSource(
    id: SourceId,
    store: SnapshotStore<Standings>,
    clock: AppClock,
    competition: Flow<String>,
    secret: Flow<SecretState>,
    private val apiKey: suspend () -> String,
    private val api: FootballDataApi,
) : CachedSource<String, Standings>(id, store, clock) {

    override val schemaVersion = 1

    override val maxAge: Duration = Duration.ofDays(2)

    // Tables change after match days; every six hours keeps them current at a fraction of the request budget.
    // 积分表在比赛日之后才变；每六小时一次就够新，只占请求额度的一小部分。
    override val cadence = RefreshCadence(interval = Duration.ofHours(6))

    override val inputs: Flow<SourceInput<String>> = combine(competition, secret) { code, s ->
        keyedInput(code.takeIf { it.isNotBlank() }, key = "competition:$code", secret = s, missing = R.string.football_setup_team)
    }

    override suspend fun fetch(params: String, now: ZonedDateTime, previous: Snapshot<Standings>?): Standings {
        val key = apiKey().ifBlank { throw AppError.NotConfigured(uiText(R.string.football_setup_key)) }
        return api.standings(key, params)
    }
}
