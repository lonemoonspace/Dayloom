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
import io.github.lonemoonspace.dayloom.core.routine.Routine
import io.github.lonemoonspace.dayloom.core.ui.PlacePicker
import io.github.lonemoonspace.dayloom.feature.weather.data.MetApi
import io.github.lonemoonspace.dayloom.feature.weather.data.WeatherSource
import io.github.lonemoonspace.dayloom.feature.weather.domain.Forecast
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherBriefPolicy
import io.github.lonemoonspace.dayloom.feature.weather.ui.WeatherCard
import io.github.lonemoonspace.dayloom.feature.weather.ui.conditionText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Weather for one saved place from MET Norway (worldwide, no key): now, the next hours, and the weather when the user
 * leaves and comes home.
 * 来自 MET Norway 的单个已保存地点的天气（全球可用、无需 Key）：现在、接下来几小时，以及出门和回家时的天气。
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
    private val settingsStore = ctx.settings(WeatherSettings.serializer(), WeatherSettings())
    private val placeId: Flow<String> = settingsStore.flow.map { it.placeId }.distinctUntilChanged()
    private val place: Flow<Place?> = placeId.flatMapLatest(ctx.places::observe)

    private val source = WeatherSource(
        id = ctx.sourceId("forecast"),
        store = ctx.snapshots(ctx.sourceId("forecast"), Forecast.serializer()),
        clock = ctx.clock,
        placeId = placeId,
        places = ctx.places,
        api = MetApi(ctx.http),
    )

    override val sources = listOf(source)

    override val configured: Flow<ConfigState> = place.map {
        if (it == null) ConfigState.NeedsSetup(uiText(R.string.weather_setup_place)) else ConfigState.Ready
    }

    override val homeCards = listOf(
        HomeCard(key = "now", title = R.string.weather_title, defaultOrder = 100) { Card() },
    )

    override val settings = SettingsSection { Settings() }

    override val brief = BriefContributor { now ->
        val snapshot = source.current()?.takeUnless { source.isStale(it, now.toInstant()) } ?: return@BriefContributor null
        val brief = WeatherBriefPolicy.brief(snapshot.value, ctx.routine.first(), now) ?: return@BriefContributor null
        val condition = UiText.Res(conditionText(brief.condition))
        UiText.Res(if (brief.umbrella) R.string.weather_brief_umbrella else R.string.weather_brief, listOf(condition, brief.temperature))
    }

    @Composable
    private fun Card() {
        val snapshot by source.observe().collectAsStateWithLifecycle(initialValue = null)
        val status by ctx.coordinator.status.collectAsStateWithLifecycle()
        val routine by ctx.routine.collectAsStateWithLifecycle(initialValue = Routine())
        val place by place.collectAsStateWithLifecycle(initialValue = null)
        WeatherCard(
            snapshot = snapshot,
            error = status[source.id]?.lastError,
            isStale = source::isStale,
            routine = routine,
            place = place,
        )
    }

    @Composable
    private fun Settings() {
        val saved by settingsStore.flow.collectAsStateWithLifecycle(initialValue = WeatherSettings())
        val places by ctx.places.all.collectAsStateWithLifecycle(initialValue = emptyList())
        Text(
            text = stringResource(R.string.weather_settings_place),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Saved in appScope so leaving the screen right away does not cancel the write. / 在 appScope 里保存，立刻离开页面也不会取消写入。
        PlacePicker(places, saved.placeId) { id -> ctx.appScope.launch { settingsStore.update { it.copy(placeId = id) } } }
    }
}
