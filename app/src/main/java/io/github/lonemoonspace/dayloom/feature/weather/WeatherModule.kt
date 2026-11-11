package io.github.lonemoonspace.dayloom.feature.weather

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
import io.github.lonemoonspace.dayloom.core.ui.PlacePicker
import io.github.lonemoonspace.dayloom.core.ui.placeTitle
import io.github.lonemoonspace.dayloom.core.ui.placeTitleText
import io.github.lonemoonspace.dayloom.feature.weather.data.MetApi
import io.github.lonemoonspace.dayloom.feature.weather.data.WeatherSource
import io.github.lonemoonspace.dayloom.feature.weather.domain.Forecast
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherBriefPolicy
import io.github.lonemoonspace.dayloom.feature.weather.ui.WeatherCard
import io.github.lonemoonspace.dayloom.feature.weather.ui.clothingText
import io.github.lonemoonspace.dayloom.feature.weather.ui.conditionText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Weather at one saved place (Home unless another is picked) from MET Norway (worldwide, no key): now, and what today is
 * like — or tomorrow in the evening — with what to wear.
 * 来自 MET Norway 的某个已保存地点（不另选就是家）的天气（全球可用、无需 Key）：现在的天气，以及今天（晚上则是明天）的整体
 * 情况与穿衣建议。
 */
object WeatherModule : FeatureModule {
    override val id = "weather"
    override val title = R.string.weather_title
    override val summary = R.string.weather_summary
    override val icon = R.drawable.ic_wx_partly_day
    override val defaultEnabled = true
    override val notificationIds = 1_000..1_999

    override fun create(ctx: ModuleContext): ModuleInstance = WeatherInstance(ctx)
}

@Serializable
data class WeatherSettings(
    /** Reserved for migrations after v1.0.0. / 预留给 v1.0.0 之后的迁移。 */
    val version: Int = 1,
    val placeId: String = Place.HOME,
)

@OptIn(ExperimentalCoroutinesApi::class)
private class WeatherInstance(private val ctx: ModuleContext) : ModuleInstance {
    private val store = ctx.settings(WeatherSettings.serializer(), WeatherSettings())

    /**
     * A picked place that was deleted since falls back to Home, so the card keeps showing weather instead of asking for setup.
     * 选中的地点后来被删掉时退回到家，卡片继续显示天气，而不是要求重新设置。
     */
    private val place: Flow<Place?> = combine(
        store.flow.map { it.placeId }.distinctUntilChanged().flatMapLatest(ctx.places::observe),
        ctx.places.observe(Place.HOME),
    ) { picked, home -> picked ?: home }

    private val source = WeatherSource(
        id = ctx.sourceId("forecast"),
        store = ctx.snapshots(ctx.sourceId("forecast"), Forecast.serializer()),
        clock = ctx.clock,
        place = place,
        api = MetApi(ctx.http),
    )

    override val sources = listOf(source)

    override val configured: Flow<ConfigState> = place.map {
        if (it == null) ConfigState.NeedsSetup(uiText(R.string.weather_setup_place)) else ConfigState.Ready
    }

    override val settingsStatus: Flow<UiText?> = place.map { it?.let(::placeTitleText) }

    override val homeCards = listOf(
        HomeCard(key = "now", title = R.string.weather_title, defaultOrder = 100) { Card() },
    )

    override val settings = SettingsSection {
        val saved by store.flow.collectAsStateWithLifecycle(initialValue = WeatherSettings())
        val places by ctx.places.all.collectAsStateWithLifecycle(initialValue = emptyList())
        Text(
            text = stringResource(R.string.weather_place),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Saved in appScope so leaving the screen right away does not cancel the write. / 在 appScope 里保存，立刻离开页面也不会取消写入。
        PlacePicker(places, saved.placeId) { id -> ctx.appScope.launch { store.update { it.copy(placeId = id) } } }
    }

    override val brief = BriefContributor { now ->
        val snapshot = source.current()?.takeUnless { source.isStale(it, now.toInstant()) } ?: return@BriefContributor null
        val brief = WeatherBriefPolicy.brief(snapshot.value, now) ?: return@BriefContributor null
        val condition = UiText.Res(conditionText(brief.condition))
        // Without an outlook for the day there is no clothing advice, but the reading itself is still worth a line.
        // 没有当天概况就没有穿衣建议，但当前读数本身仍值得一行。
        val clothing = brief.clothing ?: return@BriefContributor UiText.Res(R.string.weather_brief_short, listOf(condition, brief.temperature))
        UiText.Res(
            if (brief.umbrella) R.string.weather_brief_umbrella else R.string.weather_brief,
            listOf(condition, brief.temperature, UiText.Res(clothingText(clothing))),
        )
    }

    @Composable
    private fun Card() {
        val snapshot by source.observe().collectAsStateWithLifecycle(initialValue = null)
        val status by ctx.coordinator.status.collectAsStateWithLifecycle()
        val shown by place.collectAsStateWithLifecycle(initialValue = null)
        // Home needs no label; any other place is named so the reading is not taken for Home's. / 家不用标注；其他地点标出名字，免得被当成家的天气。
        val label = shown?.takeIf { it.id != Place.HOME }?.let { placeTitle(it) }
        WeatherCard(snapshot = snapshot, error = status[source.id]?.lastError, isStale = source::isStale, placeLabel = label)
    }
}
