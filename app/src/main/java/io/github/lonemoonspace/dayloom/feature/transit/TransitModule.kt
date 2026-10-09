package io.github.lonemoonspace.dayloom.feature.transit

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.uiText
import io.github.lonemoonspace.dayloom.core.location.Place
import io.github.lonemoonspace.dayloom.core.module.ConfigState
import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.core.module.HomeCard
import io.github.lonemoonspace.dayloom.core.module.ModuleContext
import io.github.lonemoonspace.dayloom.core.module.ModuleInstance
import io.github.lonemoonspace.dayloom.core.module.SettingsSection
import io.github.lonemoonspace.dayloom.core.notify.BriefContributor
import io.github.lonemoonspace.dayloom.core.notify.ChannelImportance
import io.github.lonemoonspace.dayloom.core.notify.ChannelSpec
import io.github.lonemoonspace.dayloom.core.time.minuteTicks
import io.github.lonemoonspace.dayloom.feature.transit.data.BoardsSource
import io.github.lonemoonspace.dayloom.feature.transit.data.CommuteSource
import io.github.lonemoonspace.dayloom.feature.transit.data.EnturProvider
import io.github.lonemoonspace.dayloom.feature.transit.domain.Boards
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteMode
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteTrips
import io.github.lonemoonspace.dayloom.feature.transit.domain.DisruptionRule
import io.github.lonemoonspace.dayloom.feature.transit.domain.FavouriteBoard
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitBrief
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitPolicy
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitStop
import io.github.lonemoonspace.dayloom.feature.transit.ui.BoardsCard
import io.github.lonemoonspace.dayloom.feature.transit.ui.CommuteCard
import io.github.lonemoonspace.dayloom.feature.transit.ui.TransitSettingsSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Norwegian public transport from Entur: commute trip options between two stops (any line, with transfers) and real-time
 * departure boards of favourite stops. Off by default because it only covers Norway.
 * 来自 Entur 的挪威公共交通：两个站点之间的通勤方案（任意线路，可换乘），以及收藏站点的实时发车板。只覆盖挪威，所以默认关闭。
 */
object TransitModule : FeatureModule {
    override val id = "transit"
    override val title = R.string.transit_title
    override val summary = R.string.transit_summary
    override val icon = R.drawable.ic_transit
    override val defaultEnabled = false
    override val notificationIds = 2_000..2_999

    override fun create(ctx: ModuleContext): ModuleInstance = TransitInstance(ctx)
}

@Serializable
data class TransitSettings(
    /** Reserved for migrations after v1.0.0. / 预留给 v1.0.0 之后的迁移。 */
    val version: Int = 1,
    val origin: TransitStop = TransitStop(),
    val destination: TransitStop = TransitStop(),
    val options: Int = TransitPolicy.DEFAULT_OPTIONS,
    val boards: List<FavouriteBoard> = emptyList(),
    /** Opt-in, off by default like every notification (design §7.4). / 选择加入，与所有通知一样默认关闭（设计文档 §7.4）。 */
    val notify: Boolean = false,
)

private class TransitInstance(private val ctx: ModuleContext) : ModuleInstance {
    private val store = ctx.settings(TransitSettings.serializer(), TransitSettings())
    private val provider = EnturProvider(ctx.http)
    private val channel = ChannelSpec(
        "disruption",
        R.string.transit_channel,
        R.string.transit_channel_description,
        // A cancelled train is time-critical. / 列车取消有时效性。
        ChannelImportance.HIGH,
    )

    // Re-evaluated every minute, emits only when the window changes, which then triggers a refresh.
    // 每分钟重新判断，只在时间窗变化时发出，随后触发一次刷新。
    private val mode: Flow<CommuteMode> = combine(ctx.routine, ctx.clock.minuteTicks(), TransitPolicy::commuteMode).distinctUntilChanged()

    private val commute = CommuteSource(
        id = ctx.sourceId("commute"),
        store = ctx.snapshots(ctx.sourceId("commute"), CommuteTrips.serializer()),
        clock = ctx.clock,
        settings = store.flow.map { Triple(it.origin, it.destination, it.options) }.distinctUntilChanged(),
        mode = mode,
        provider = provider,
    )

    private val boards = BoardsSource(
        id = ctx.sourceId("boards"),
        store = ctx.snapshots(ctx.sourceId("boards"), Boards.serializer()),
        clock = ctx.clock,
        boards = store.flow.map { it.boards }.distinctUntilChanged(),
        provider = provider,
    )

    override val sources = listOf(commute, boards)

    override val configured: Flow<ConfigState> = store.flow.map {
        if (it.origin.isSet && it.destination.isSet) ConfigState.Ready else ConfigState.NeedsSetup(uiText(R.string.transit_setup_commute))
    }

    override val homeCards = listOf(
        HomeCard(key = "commute", title = R.string.transit_commute_title, defaultOrder = 150) {
            val snapshot by commute.observe().collectAsStateWithLifecycle(initialValue = null)
            val status by ctx.coordinator.status.collectAsStateWithLifecycle()
            val settings by store.flow.collectAsStateWithLifecycle(initialValue = null)
            settings?.let { CommuteCard(snapshot, status[commute.id]?.lastError, commute::isStale, it.origin, it.destination, it.options) }
        },
        HomeCard(key = "boards", title = R.string.transit_boards_title, defaultOrder = 160) {
            val snapshot by boards.observe().collectAsStateWithLifecycle(initialValue = null)
            val status by ctx.coordinator.status.collectAsStateWithLifecycle()
            val settings by store.flow.collectAsStateWithLifecycle(initialValue = null)
            // Without favourites the card takes no space. / 没有收藏站点时卡片不占位置。
            settings?.boards?.takeIf { it.isNotEmpty() }?.let { BoardsCard(snapshot, status[boards.id]?.lastError, boards::isStale, it) }
        },
    )

    override val settings = SettingsSection {
        val home by ctx.places.observe(Place.HOME).collectAsStateWithLifecycle(initialValue = null)
        TransitSettingsSection(
            settings = store.flow,
            provider = provider,
            // Entur covers Norway only; warn when Home is known to be elsewhere. / Entur 只覆盖挪威；已知家不在挪威时提示。
            outsideNorway = home?.countryCode?.let { it.isNotEmpty() && it != "NO" } == true,
            channelId = ctx.channelId(channel.name),
        ) { transform -> ctx.appScope.launch { store.update(transform) } }
    }

    override val brief = BriefContributor { now ->
        val snapshot = commute.current()?.takeUnless { commute.isStale(it, now.toInstant()) } ?: return@BriefContributor null
        TransitBrief.line(snapshot.value, now)
    }

    override val notificationChannels = listOf(channel)

    override val notificationRules = listOf(
        DisruptionRule(
            enabled = { store.get().notify },
            refreshed = { report -> report.succeeded(commute.id) },
            trips = { commute.current()?.value },
            channelId = ctx.channelId(channel.name),
            deepLink = ctx.deepLink,
            notificationId = ctx.notificationId(0),
        ),
    )
}
