package io.github.lonemoonspace.dayloom.feature.transit

import androidx.compose.runtime.Composable
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
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteKind
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteRoute
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteTrips
import io.github.lonemoonspace.dayloom.feature.transit.domain.DisruptionRule
import io.github.lonemoonspace.dayloom.feature.transit.domain.FavouriteBoard
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitBrief
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitPolicy
import io.github.lonemoonspace.dayloom.feature.transit.ui.BoardsCard
import io.github.lonemoonspace.dayloom.feature.transit.ui.BoardsSettingsSection
import io.github.lonemoonspace.dayloom.feature.transit.ui.CommuteCard
import io.github.lonemoonspace.dayloom.feature.transit.ui.RouteSettingsSection
import io.github.lonemoonspace.dayloom.feature.transit.ui.kindTitle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Norwegian public transport from Entur, as three modules with their own switches: a train commute and a bus commute, each
 * between its own two stops (any line of that kind, with transfers), and real-time departure boards of favourite stops.
 * All three are off by default because they only cover Norway.
 * 来自 Entur 的挪威公共交通，拆成三个各有开关的模块：火车通勤与公交通勤各自在两个站点之间（该类车辆的任意线路，可换乘），
 * 以及收藏站点的实时发车板。只覆盖挪威，所以三个都默认关闭。
 */
sealed class CommuteModule(private val kind: CommuteKind) : FeatureModule {
    override val title get() = kindTitle(kind)
    override val defaultEnabled = false
    override val splitFrom = FORMER_ID

    override fun create(ctx: ModuleContext): ModuleInstance = CommuteInstance(ctx, kind)
}

object TrainModule : CommuteModule(CommuteKind.TRAIN) {
    override val id = "train"
    override val summary = R.string.transit_train_summary
    override val icon = R.drawable.ic_train
    override val notificationIds = 2_000..2_099
}

object BusModule : CommuteModule(CommuteKind.BUS) {
    override val id = "bus"
    override val summary = R.string.transit_bus_summary
    override val icon = R.drawable.ic_bus
    override val notificationIds = 2_100..2_199
}

object DeparturesModule : FeatureModule {
    override val id = "departures"
    override val title = R.string.transit_boards_title
    override val summary = R.string.transit_boards_summary
    override val icon = R.drawable.ic_transit
    override val defaultEnabled = false
    override val splitFrom = FORMER_ID

    override fun create(ctx: ModuleContext): ModuleInstance = DeparturesInstance(ctx)
}

/**
 * The single module train, bus and departures were split out of; their switches and settings start from its saved ones.
 * 火车、公交与发车板拆分前所属的那个模块；它们的开关与设置从它保存的值开始。
 */
private const val FORMER_ID = "transit"

@Serializable
data class CommuteSettings(
    /** Reserved for migrations after v1.0.0. / 预留给 v1.0.0 之后的迁移。 */
    val version: Int = 1,
    val route: CommuteRoute = CommuteRoute(),
)

@Serializable
data class DepartureSettings(
    /** Reserved for migrations after v1.0.0. / 预留给 v1.0.0 之后的迁移。 */
    val version: Int = 1,
    val boards: List<FavouriteBoard> = emptyList(),
)

/**
 * The settings saved by the former `transit` module, read only to start the split-out modules from.
 * 原 `transit` 模块保存的设置，只用来给拆分出来的模块提供初始值。
 */
@Serializable
private data class FormerTransitSettings(
    val version: Int = 1,
    val train: CommuteRoute = CommuteRoute(),
    val bus: CommuteRoute = CommuteRoute(),
    val boards: List<FavouriteBoard> = emptyList(),
)

private class CommuteInstance(private val ctx: ModuleContext, private val kind: CommuteKind) : ModuleInstance {
    private val store = ctx.settings(CommuteSettings.serializer(), CommuteSettings(), FormerTransitSettings.serializer()) { former ->
        CommuteSettings(
            route = when (kind) {
                CommuteKind.TRAIN -> former.train
                CommuteKind.BUS -> former.bus
            },
        )
    }
    private val route = store.flow.map { it.route }.distinctUntilChanged()
    private val provider = EnturProvider(ctx.http)
    private val channel = ChannelSpec(
        "disruption",
        when (kind) {
            CommuteKind.TRAIN -> R.string.transit_train_channel
            CommuteKind.BUS -> R.string.transit_bus_channel
        },
        R.string.transit_channel_description,
        // A cancelled train is time-critical. / 列车取消有时效性。
        ChannelImportance.HIGH,
    )

    /** `train.commute` or `bus.commute`. / `train.commute` 或 `bus.commute`。 */
    private val source = CommuteSource(
        id = ctx.sourceId("commute"),
        store = ctx.snapshots(ctx.sourceId("commute"), CommuteTrips.serializer()),
        clock = ctx.clock,
        kind = kind,
        route = route.map { it.copy(notify = false) }.distinctUntilChanged(),
        // Re-evaluated every minute, emits only when the direction changes at noon or midnight, which then triggers a refresh.
        // 每分钟重新判断，只在中午或午夜方向变化时发出，随后触发一次刷新。
        mode = ctx.clock.minuteTicks().map(TransitPolicy::commuteMode).distinctUntilChanged(),
        provider = provider,
    )

    override val sources = listOf(source)

    override val configured = route.map { if (it.isSet) ConfigState.Ready else ConfigState.NeedsSetup(uiText(R.string.transit_setup_commute)) }

    override val homeCards = listOf(
        HomeCard(key = "commute", title = kindTitle(kind), defaultOrder = 150 + kind.ordinal * 5, formerKey = kind.formerCardKey) {
            val snapshot by source.observe().collectAsStateWithLifecycle(initialValue = null)
            val status by ctx.coordinator.status.collectAsStateWithLifecycle()
            val saved by route.collectAsStateWithLifecycle(initialValue = null)
            // Switched on without stops, the card asks for them. / 开启了但没设置站点时，卡片提示去设置。
            saved?.let { CommuteCard(kind, snapshot, status[source.id]?.lastError, source::isStale, it) }
        },
    )

    override val settingsSections = listOf(
        SettingsSection {
            RouteSettingsSection(
                kind = kind,
                saved = route,
                provider = provider,
                outsideNorway = homeOutsideNorway(ctx),
                channelId = ctx.channelId(channel.name),
            ) { transform -> ctx.appScope.launch { store.update { it.copy(route = transform(it.route)) } } }
        },
    )

    override val brief = BriefContributor { now ->
        source.current()?.takeUnless { source.isStale(it, now.toInstant()) }?.let { TransitBrief.line(it.value, now, it.fetchedAt) }
    }

    override val notificationChannels = listOf(channel)

    override val notificationRules = listOf(
        DisruptionRule(
            name = "disruption",
            enabled = { store.get().route.notify },
            refreshed = { report -> report.succeeded(source.id) },
            trips = { source.current()?.value },
            channelId = ctx.channelId(channel.name),
            deepLink = ctx.deepLink,
            notificationId = ctx.notificationId(0),
        ),
    )
}

private class DeparturesInstance(private val ctx: ModuleContext) : ModuleInstance {
    private val store = ctx.settings(DepartureSettings.serializer(), DepartureSettings(), FormerTransitSettings.serializer()) { former ->
        DepartureSettings(boards = former.boards)
    }
    private val favourites = store.flow.map { it.boards }.distinctUntilChanged()
    private val provider = EnturProvider(ctx.http)

    /** `departures.boards`. */
    private val boards = BoardsSource(
        id = ctx.sourceId("boards"),
        store = ctx.snapshots(ctx.sourceId("boards"), Boards.serializer()),
        clock = ctx.clock,
        boards = favourites,
        provider = provider,
    )

    override val sources = listOf(boards)

    override val configured = favourites.map { list ->
        if (list.any { it.stop.isSet }) ConfigState.Ready else ConfigState.NeedsSetup(uiText(R.string.transit_setup_boards))
    }

    override val homeCards = listOf(
        HomeCard(key = "boards", title = R.string.transit_boards_title, defaultOrder = 160, formerKey = "boards") {
            val snapshot by boards.observe().collectAsStateWithLifecycle(initialValue = null)
            val status by ctx.coordinator.status.collectAsStateWithLifecycle()
            val saved by favourites.collectAsStateWithLifecycle(initialValue = null)
            // Switched on without favourites, the card asks for one. / 开启了但没有收藏站点时，卡片提示去添加。
            saved?.let { BoardsCard(snapshot, status[boards.id]?.lastError, boards::isStale, it) }
        },
    )

    override val settingsSections = listOf(
        SettingsSection {
            BoardsSettingsSection(boards = favourites, provider = provider, outsideNorway = homeOutsideNorway(ctx)) { transform ->
                ctx.appScope.launch { store.update { it.copy(boards = transform(it.boards)) } }
            }
        },
    )
}

/**
 * Entur covers Norway only; true when Home is known to be elsewhere, so the settings can say so.
 * Entur 只覆盖挪威；已知家不在挪威时为 true，设置里据此提示。
 */
@Composable
private fun homeOutsideNorway(ctx: ModuleContext): Boolean {
    val home by ctx.places.observe(Place.HOME).collectAsStateWithLifecycle(initialValue = null)
    return home?.countryCode?.let { it.isNotEmpty() && it != "NO" } == true
}

/** The card's key in the former `transit` module, which keeps its place on the home screen. / 卡片在原 `transit` 模块里的键，用来保住它在首页的位置。 */
private val CommuteKind.formerCardKey: String
    get() = when (this) {
        CommuteKind.TRAIN -> "train"
        CommuteKind.BUS -> "bus"
    }
