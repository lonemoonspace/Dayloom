package io.github.lonemoonspace.dayloom.feature.football

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.i18n.uiText
import io.github.lonemoonspace.dayloom.core.module.CardPlacement
import io.github.lonemoonspace.dayloom.core.module.ConfigState
import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.core.module.HomeCard
import io.github.lonemoonspace.dayloom.core.module.ModuleContext
import io.github.lonemoonspace.dayloom.core.module.ModuleInstance
import io.github.lonemoonspace.dayloom.core.module.ModuleTab
import io.github.lonemoonspace.dayloom.core.module.SettingsSection
import io.github.lonemoonspace.dayloom.core.notify.BriefContributor
import io.github.lonemoonspace.dayloom.core.notify.ChannelSpec
import io.github.lonemoonspace.dayloom.core.refresh.Trigger
import io.github.lonemoonspace.dayloom.core.secret.SecretState
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.SkeletonLines
import io.github.lonemoonspace.dayloom.core.ui.plusBars
import io.github.lonemoonspace.dayloom.core.ui.rememberMinuteTick
import io.github.lonemoonspace.dayloom.feature.football.data.FootballDataApi
import io.github.lonemoonspace.dayloom.feature.football.data.MatchesSource
import io.github.lonemoonspace.dayloom.feature.football.data.StandingsSource
import io.github.lonemoonspace.dayloom.feature.football.domain.FootballBrief
import io.github.lonemoonspace.dayloom.feature.football.domain.FootballNotifyTarget
import io.github.lonemoonspace.dayloom.feature.football.domain.FootballPolicy
import io.github.lonemoonspace.dayloom.feature.football.domain.KickoffRule
import io.github.lonemoonspace.dayloom.feature.football.domain.ResultRule
import io.github.lonemoonspace.dayloom.feature.football.domain.Standings
import io.github.lonemoonspace.dayloom.feature.football.domain.TeamMatches
import io.github.lonemoonspace.dayloom.feature.football.domain.TeamRef
import io.github.lonemoonspace.dayloom.feature.football.ui.Attribution
import io.github.lonemoonspace.dayloom.feature.football.ui.CardSpacing
import io.github.lonemoonspace.dayloom.feature.football.ui.Crest
import io.github.lonemoonspace.dayloom.feature.football.ui.CrestLoader
import io.github.lonemoonspace.dayloom.feature.football.ui.ErrorLine
import io.github.lonemoonspace.dayloom.feature.football.ui.FootballSettingsSection
import io.github.lonemoonspace.dayloom.feature.football.ui.Hint
import io.github.lonemoonspace.dayloom.feature.football.ui.MatchRow
import io.github.lonemoonspace.dayloom.feature.football.ui.Scoreboard
import io.github.lonemoonspace.dayloom.feature.football.ui.TableRows
import io.github.lonemoonspace.dayloom.feature.football.ui.competitionLabel
import io.github.lonemoonspace.dayloom.feature.football.ui.updatedAgo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * The followed team from football-data.org with the user's own key: its own tab with fixtures, results and the table, a
 * home card around match time, and opt-in kick-off and final-score notifications. Off by default because it needs a key.
 * 用用户自己的 Key 从 football-data.org 获取关注球队的数据：自己的标签页（赛程、赛果、积分榜）、比赛前后的首页卡片，以及需要
 * 主动打开的开赛与终场通知。需要 Key，所以默认关闭。
 */
object FootballModule : FeatureModule {
    override val id = "football"
    override val title = R.string.football_title
    override val summary = R.string.football_summary
    override val icon = R.drawable.ic_football
    override val defaultEnabled = false
    override val notificationIds = 3_000..3_999

    override fun create(ctx: ModuleContext): ModuleInstance = FootballInstance(ctx)
}

@Serializable
data class FootballSettings(
    /** Reserved for migrations after v1.0.0. / 预留给 v1.0.0 之后的迁移。 */
    val version: Int = 1,
    /** Competition code the team was picked from, also the table shown. / 挑选球队时所在的赛事代码，也是显示的积分表。 */
    val competition: String = "",
    val team: TeamRef = TeamRef(),
    /** Opt-in, off by default like every notification (design §7.4). / 选择加入，与所有通知一样默认关闭（设计文档 §7.4）。 */
    val notifyKickoff: Boolean = false,
    val notifyResult: Boolean = false,
)

private class FootballInstance(private val ctx: ModuleContext) : ModuleInstance {
    private val store = ctx.settings(FootballSettings.serializer(), FootballSettings())
    private val key = ctx.secret("football_data")
    private val api = FootballDataApi(ctx.http)
    private val crests = CrestLoader(ctx.http)
    private val channel = ChannelSpec("matches", R.string.football_channel, R.string.football_channel_description)

    private val matches = MatchesSource(
        id = ctx.sourceId("matches"),
        store = ctx.snapshots(ctx.sourceId("matches"), TeamMatches.serializer()),
        clock = ctx.clock,
        teamId = store.flow.map { it.team.id }.distinctUntilChanged(),
        secret = key.observe(),
        apiKey = key::usable,
        api = api,
    )

    private val standings = StandingsSource(
        id = ctx.sourceId("standings"),
        store = ctx.snapshots(ctx.sourceId("standings"), Standings.serializer()),
        clock = ctx.clock,
        competition = store.flow.map { it.competition }.distinctUntilChanged(),
        secret = key.observe(),
        apiKey = key::usable,
        api = api,
    )

    init {
        // The matches cadence follows the schedule; it learns it from the cached snapshot too, not only from a fetch.
        // 比赛来源的刷新节奏随赛程变化；它不只从抓取结果、也从缓存快照里得知赛程。
        ctx.appScope.launch { matches.observe().collect { snapshot -> matches.known = snapshot?.value?.matches.orEmpty() } }
    }

    override val sources = listOf(matches, standings)

    override val configured: Flow<ConfigState> = combine(store.flow, key.observe()) { s, secret ->
        when {
            secret.display.isBlank() -> ConfigState.NeedsSetup(uiText(R.string.football_setup_key))
            s.team.id <= 0 -> ConfigState.NeedsSetup(uiText(R.string.football_setup_team))
            else -> ConfigState.Ready
        }
    }

    /** Around match time only; at other times the card takes no space. / 只在比赛前后出现；其余时间卡片不占位置。 */
    override val homeCards = listOf(
        HomeCard(
            key = "match",
            title = R.string.football_title,
            defaultOrder = 250,
            // A live match moves above the user's order while it lasts. / 比赛进行期间卡片排到最前。
            placement = matches.observe().map { s ->
                if (s?.value?.matches.orEmpty().any { it.status.isLive }) CardPlacement.TOP else CardPlacement.NORMAL
            }.distinctUntilChanged(),
        ) { HomeMatchCard() },
    )

    override val tab = ModuleTab(label = R.string.football_title, icon = R.drawable.ic_football) { Tab() }

    override val settings = SettingsSection {
        val saved by store.flow.collectAsStateWithLifecycle(initialValue = FootballSettings())
        val secret by key.observe().collectAsStateWithLifecycle(initialValue = SecretState.EMPTY)
        FootballSettingsSection(
            saved = saved,
            secret = secret,
            channelId = ctx.channelId(channel.name),
            crests = crests,
            loadTeams = { code -> api.teams(key.usable(), code) },
            saveKey = { plain -> ctx.appScope.launch { key.put(plain) } },
        ) { transform -> ctx.appScope.launch { store.update(transform) } }
    }

    override val brief = BriefContributor { now ->
        val snapshot = matches.current()?.takeUnless { matches.isStale(it, now.toInstant()) } ?: return@BriefContributor null
        FootballBrief.line(snapshot.value.matches, now)
    }

    override val notificationChannels = listOf(channel)

    private val target get() = FootballNotifyTarget(ctx.channelId(channel.name), ctx.deepLink, ctx.notificationId(0))

    override val notificationRules = listOf(
        KickoffRule(
            enabled = { store.get().notifyKickoff },
            matches = { matches.current()?.value?.matches },
            target = target,
        ),
        ResultRule(
            enabled = { store.get().notifyResult },
            refreshed = { report -> report.succeeded(matches.id) },
            matches = { matches.current()?.value?.matches },
            teamId = { store.get().team.id },
            target = target,
        ),
    )

    @Composable
    private fun HomeMatchCard() {
        val snapshot by matches.observe().collectAsStateWithLifecycle(initialValue = null)
        val settings by store.flow.collectAsStateWithLifecycle(initialValue = null)
        val now = rememberMinuteTick()
        val match = snapshot?.value?.matches?.let { FootballPolicy.homeMatch(it, now.toInstant()) } ?: return
        if (settings?.team?.id != snapshot?.value?.teamId) return
        InfoCard(
            title = stringResource(R.string.football_title),
            icon = R.drawable.ic_football,
            subtitle = competitionLabel(match.competitionCode, match.competition),
            meta = snapshot?.let { updatedAgo(it.fetchedAt, now) },
            stale = snapshot?.let { matches.isStale(it, now.toInstant()) } == true,
        ) {
            Scoreboard(match, now, crests)
        }
    }

    /**
     * The tab: team, the match of the moment, fixtures, results and the table. While it is open the due sources are
     * refreshed every minute, which during a match means a live score.
     * 标签页：球队、当下的比赛、赛程、赛果与积分榜。页面打开期间每分钟刷新到期的来源，比赛进行中即为实时比分。
     */
    @Composable
    private fun Tab() {
        val settings by store.flow.collectAsStateWithLifecycle(initialValue = null)
        val matchSnapshot by matches.observe().collectAsStateWithLifecycle(initialValue = null)
        val tableSnapshot by standings.observe().collectAsStateWithLifecycle(initialValue = null)
        val status by ctx.coordinator.status.collectAsStateWithLifecycle()
        val now = rememberMinuteTick()
        LaunchedEffect(Unit) {
            var trigger = Trigger.AUTO
            while (true) {
                try {
                    ctx.coordinator.refreshDue(setOf(matches.id, standings.id), trigger, inWindow = false, throttleFailures = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Errors are in the coordinator's status and shown on the cards. / 错误记录在协调器状态里，卡片上会显示。
                }
                trigger = Trigger.LIVE_POLL
                delay(POLL_MS)
            }
        }
        val s = settings ?: return
        val team = s.team
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp).plusBars(top = true),
            verticalArrangement = CardSpacing,
        ) {
            if (team.id <= 0) {
                item { InfoCard(title = stringResource(R.string.football_title), icon = R.drawable.ic_football) { Hint(stringResource(R.string.football_tab_setup)) } }
                return@LazyColumn
            }
            val list = matchSnapshot?.value?.takeIf { it.teamId == team.id }?.matches
            val matchError = status[matches.id]?.lastError
            item(key = "team") {
                InfoCard(title = null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Crest(team, crests, size = 44.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                            Hint(competitionLabel(s.competition, tableSnapshot?.value?.competition.orEmpty()))
                        }
                        matchSnapshot?.let { Hint(updatedAgo(it.fetchedAt, now)) }
                    }
                    if (matchError != null) ErrorLine(matchError)
                }
            }
            val featured = list?.let { FootballPolicy.homeMatch(it, now.toInstant()) }
            if (featured != null) {
                item(key = "featured") {
                    InfoCard(title = competitionLabel(featured.competitionCode, featured.competition), icon = R.drawable.ic_football) {
                        Scoreboard(featured, now, crests)
                    }
                }
            }
            item(key = "upcoming") {
                InfoCard(title = stringResource(R.string.football_upcoming)) {
                    when {
                        list == null -> if (matchError == null) SkeletonLines()
                        else -> {
                            val upcoming = FootballPolicy.upcoming(list, now.toInstant()).filterNot { it.id == featured?.id }
                            if (upcoming.isEmpty()) Hint(stringResource(R.string.football_no_matches))
                            upcoming.forEachIndexed { i, m -> MatchRow(m, team.id, now, crests, divider = i > 0) }
                        }
                    }
                }
            }
            item(key = "recent") {
                InfoCard(title = stringResource(R.string.football_recent)) {
                    when {
                        list == null -> if (matchError == null) SkeletonLines()
                        else -> {
                            val recent = FootballPolicy.recent(list).filterNot { it.id == featured?.id }
                            if (recent.isEmpty()) Hint(stringResource(R.string.football_no_results))
                            recent.forEachIndexed { i, m -> MatchRow(m, team.id, now, crests, divider = i > 0) }
                        }
                    }
                }
            }
            item(key = "table") {
                val tableError = status[standings.id]?.lastError
                InfoCard(
                    title = stringResource(R.string.football_table),
                    stale = tableSnapshot?.let { standings.isStale(it, now.toInstant()) } == true,
                ) {
                    val table = tableSnapshot?.value?.takeIf { it.competitionCode == s.competition }
                    when {
                        table != null -> TableRows(table, team.id, crests)
                        tableError == null -> SkeletonLines(lines = 4)
                    }
                    if (tableError != null) ErrorLine(tableError)
                    Attribution()
                }
            }
        }
    }

    private companion object {
        const val POLL_MS = 60_000L
    }
}
