package io.github.lonemoonspace.dayloom.feature.traffic

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.i18n.uiText
import io.github.lonemoonspace.dayloom.core.location.Place
import io.github.lonemoonspace.dayloom.core.module.ConfigState
import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.core.module.HomeCard
import io.github.lonemoonspace.dayloom.core.module.ModuleContext
import io.github.lonemoonspace.dayloom.core.module.ModuleInstance
import io.github.lonemoonspace.dayloom.core.module.SettingsSection
import io.github.lonemoonspace.dayloom.core.notify.BriefContributor
import io.github.lonemoonspace.dayloom.core.secret.SecretState
import io.github.lonemoonspace.dayloom.core.time.minuteTicks
import io.github.lonemoonspace.dayloom.core.ui.PlacePicker
import io.github.lonemoonspace.dayloom.core.ui.SecretInput
import io.github.lonemoonspace.dayloom.core.ui.placeTitleText
import io.github.lonemoonspace.dayloom.feature.traffic.data.GoogleRoutesApi
import io.github.lonemoonspace.dayloom.feature.traffic.data.TrafficSource
import io.github.lonemoonspace.dayloom.feature.traffic.domain.Direction
import io.github.lonemoonspace.dayloom.feature.traffic.domain.TrafficLevel
import io.github.lonemoonspace.dayloom.feature.traffic.domain.TrafficPolicy
import io.github.lonemoonspace.dayloom.feature.traffic.domain.TrafficStatus
import io.github.lonemoonspace.dayloom.feature.traffic.ui.TrafficCard
import io.github.lonemoonspace.dayloom.feature.traffic.ui.levelText
import io.github.lonemoonspace.dayloom.feature.traffic.ui.wholeMinutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Driving time and congestion between two saved places (Home → Work by default) with the user's own Google Routes key.
 * Off by default, so users without a key never see an error card.
 * 用用户自己的 Google Routes Key 查两个已保存地点之间（默认家 → 公司）的驾车时间与拥堵。默认关闭，没有 Key 的用户永远看不到报错卡片。
 */
object TrafficModule : FeatureModule {
    override val id = "traffic"
    override val title = R.string.traffic_title
    override val summary = R.string.traffic_summary
    override val icon = R.drawable.ic_traffic
    override val defaultEnabled = false

    override fun create(ctx: ModuleContext): ModuleInstance = TrafficInstance(ctx)
}

@Serializable
data class TrafficSettings(
    /** Reserved for migrations after v1.0.0. / 预留给 v1.0.0 之后的迁移。 */
    val version: Int = 1,
    val fromPlaceId: String = Place.HOME,
    val toPlaceId: String = Place.WORK,
)

@OptIn(ExperimentalCoroutinesApi::class)
private class TrafficInstance(private val ctx: ModuleContext) : ModuleInstance {
    private val store = ctx.settings(TrafficSettings.serializer(), TrafficSettings())
    private val key = ctx.googleMapsKey

    private val from: Flow<Place?> = store.flow.map { it.fromPlaceId }.distinctUntilChanged().flatMapLatest(ctx.places::observe)
    private val to: Flow<Place?> = store.flow.map { it.toPlaceId }.distinctUntilChanged().flatMapLatest(ctx.places::observe)

    // Re-evaluated every minute but only emits when the direction flips, which then triggers a refresh.
    // 每分钟重新判断，但只在方向变化时发出，随后触发一次刷新。
    private val direction: Flow<Direction> = combine(ctx.routine, ctx.clock.minuteTicks(), TrafficPolicy::direction)
        .distinctUntilChanged()

    private val source = TrafficSource(
        id = ctx.sourceId("route"),
        store = ctx.snapshots(ctx.sourceId("route"), TrafficStatus.serializer()),
        clock = ctx.clock,
        from = from,
        to = to,
        direction = direction,
        secret = key.observe(),
        apiKey = key::usable,
        api = GoogleRoutesApi(ctx.http),
    )

    override val sources = listOf(source)

    override val configured: Flow<ConfigState> = combine(from, to, key.observe()) { a, b, secret ->
        when {
            a == null || b == null -> ConfigState.NeedsSetup(uiText(R.string.traffic_setup_places))
            secret.display.isBlank() -> ConfigState.NeedsSetup(uiText(R.string.traffic_setup_key))
            else -> ConfigState.Ready
        }
    }

    override val homeCards = listOf(
        HomeCard(key = "route", title = R.string.traffic_title, defaultOrder = 200) {
            val snapshot by source.observe().collectAsStateWithLifecycle(initialValue = null)
            val status by ctx.coordinator.status.collectAsStateWithLifecycle()
            val fromPlace by from.collectAsStateWithLifecycle(initialValue = null)
            val toPlace by to.collectAsStateWithLifecycle(initialValue = null)
            TrafficCard(snapshot, status[source.id]?.lastError, source::isStale, fromPlace, toPlace)
        },
    )

    override val settings = SettingsSection { Settings() }

    override val brief = BriefContributor { now ->
        val status = source.current()?.takeUnless { source.isStale(it, now.toInstant()) }?.value ?: return@BriefContributor null
        // Only the way to work belongs in a morning brief. / 早间简报只说去上班的路。
        if (status.direction != Direction.TO_WORK) return@BriefContributor null
        val destination = to.first() ?: return@BriefContributor null
        val minutes = wholeMinutes(status.durationSec)
        val duration = UiText.Plural(R.plurals.traffic_minutes, minutes)
        if (status.level == TrafficLevel.UNKNOWN) {
            UiText.Res(R.string.traffic_brief_plain, listOf(placeTitleText(destination), duration))
        } else {
            UiText.Res(R.string.traffic_brief, listOf(placeTitleText(destination), duration, UiText.Res(levelText(status.level))))
        }
    }

    @Composable
    private fun Settings() {
        val saved by store.flow.collectAsStateWithLifecycle(initialValue = TrafficSettings())
        val places by ctx.places.all.collectAsStateWithLifecycle(initialValue = emptyList())
        val secret by key.observe().collectAsStateWithLifecycle(initialValue = SecretState.EMPTY)
        // Saved in appScope so leaving the screen right away does not cancel the write. / 在 appScope 里保存，立刻离开页面也不会取消写入。
        fun update(transform: (TrafficSettings) -> TrafficSettings) {
            ctx.appScope.launch { store.update(transform) }
        }
        Label(stringResource(R.string.traffic_from))
        PlacePicker(places, saved.fromPlaceId) { id -> update { it.copy(fromPlaceId = id) } }
        Label(stringResource(R.string.traffic_to))
        PlacePicker(places, saved.toPlaceId) { id -> update { it.copy(toPlaceId = id) } }
        Label(stringResource(R.string.traffic_key_note))
        SecretInput(stringResource(R.string.traffic_key), secret) { plain -> ctx.appScope.launch { key.put(plain) } }
    }

    @Composable
    private fun Label(text: String) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
