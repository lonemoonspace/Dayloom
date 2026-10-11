package io.github.lonemoonspace.dayloom.feature.weather

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.github.lonemoonspace.dayloom.core.notify.BriefContributor
import io.github.lonemoonspace.dayloom.feature.weather.data.MetApi
import io.github.lonemoonspace.dayloom.feature.weather.data.WeatherSource
import io.github.lonemoonspace.dayloom.feature.weather.domain.Forecast
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherBriefPolicy
import io.github.lonemoonspace.dayloom.feature.weather.ui.WeatherCard
import io.github.lonemoonspace.dayloom.feature.weather.ui.clothingText
import io.github.lonemoonspace.dayloom.feature.weather.ui.conditionText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Weather at Home from MET Norway (worldwide, no key): now, and what today is like — or tomorrow in the evening — with what to
 * wear. Always Home, so it has no settings of its own.
 * 来自 MET Norway 的家所在地天气（全球可用、无需 Key）：现在的天气，以及今天（晚上则是明天）的整体情况与穿衣建议。
 * 固定为家，所以没有自己的设置。
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

private class WeatherInstance(private val ctx: ModuleContext) : ModuleInstance {
    private val home: Flow<Place?> = ctx.places.observe(Place.HOME)

    private val source = WeatherSource(
        id = ctx.sourceId("forecast"),
        store = ctx.snapshots(ctx.sourceId("forecast"), Forecast.serializer()),
        clock = ctx.clock,
        place = home,
        api = MetApi(ctx.http),
    )

    override val sources = listOf(source)

    override val configured: Flow<ConfigState> = home.map {
        if (it == null) ConfigState.NeedsSetup(uiText(R.string.weather_setup_place)) else ConfigState.Ready
    }

    override val homeCards = listOf(
        HomeCard(key = "now", title = R.string.weather_title, defaultOrder = 100) { Card() },
    )

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
        WeatherCard(snapshot = snapshot, error = status[source.id]?.lastError, isStale = source::isStale)
    }
}
