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
import io.github.lonemoonspace.dayloom.core.notify.ChannelImportance
import io.github.lonemoonspace.dayloom.core.notify.ChannelSpec
import io.github.lonemoonspace.dayloom.core.ui.NotifySwitch
import io.github.lonemoonspace.dayloom.core.ui.PlacePicker
import io.github.lonemoonspace.dayloom.core.ui.placeTitle
import io.github.lonemoonspace.dayloom.core.ui.placeTitleText
import io.github.lonemoonspace.dayloom.feature.weather.data.MetAlertsApi
import io.github.lonemoonspace.dayloom.feature.weather.data.MetApi
import io.github.lonemoonspace.dayloom.feature.weather.data.WarningsSource
import io.github.lonemoonspace.dayloom.feature.weather.data.WeatherParams
import io.github.lonemoonspace.dayloom.feature.weather.data.WeatherSource
import io.github.lonemoonspace.dayloom.feature.weather.domain.Forecast
import io.github.lonemoonspace.dayloom.feature.weather.domain.WarningPolicy
import io.github.lonemoonspace.dayloom.feature.weather.domain.WarningRule
import io.github.lonemoonspace.dayloom.feature.weather.domain.WarningText
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherBriefPolicy
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherWarning
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherWarnings
import io.github.lonemoonspace.dayloom.feature.weather.ui.WeatherCard
import io.github.lonemoonspace.dayloom.feature.weather.ui.clothingText
import io.github.lonemoonspace.dayloom.feature.weather.ui.conditionText
import java.time.ZonedDateTime
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
    /** Notify about new MET warnings; off until the user asks for it. / 新的 MET 预警发通知；用户打开之前不发。 */
    val notifyWarnings: Boolean = false,
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

    /** `weather.warnings`. */
    private val warnings = WarningsSource(
        id = ctx.sourceId("warnings"),
        store = ctx.snapshots(ctx.sourceId("warnings"), WeatherWarnings.serializer()),
        clock = ctx.clock,
        place = place,
        api = MetAlertsApi(ctx.http),
    )

    private val channel = ChannelSpec(
        "warnings",
        R.string.weather_warning_channel,
        R.string.weather_warning_channel_description,
        // A warning is about the hours ahead. / 预警说的是接下来几个小时。
        ChannelImportance.HIGH,
    )

    override val sources = listOf(source, warnings)

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
        NotifySwitch(
            label = stringResource(R.string.weather_warning_switch),
            summary = stringResource(R.string.weather_warning_switch_summary),
            checked = saved.notifyWarnings,
            channelId = ctx.channelId(channel.name),
        ) { on -> ctx.appScope.launch { store.update { it.copy(notifyWarnings = on) } } }
        val shown by place.collectAsStateWithLifecycle(initialValue = null)
        // Said here because the card simply shows no warnings abroad. / 在这里说明，因为在国外时卡片只是不显示预警。
        if (shown?.let(WarningsSource::outsideNorway) == true) {
            Text(
                text = stringResource(R.string.weather_warnings_norway_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    override val notificationChannels = listOf(channel)

    override val notificationRules = listOf(
        WarningRule(
            enabled = { store.get().notifyWarnings },
            refreshed = { report -> report.succeeded(warnings.id) },
            warnings = { warnings.current()?.value },
            channelId = ctx.channelId(channel.name),
            deepLink = ctx.deepLink,
            notificationId = ctx.notificationId(0),
        ),
    )

    /** Warnings in force at [now] from a snapshot recent enough to trust. / [now] 时仍有效、且快照新到可以信任的预警。 */
    private suspend fun activeWarnings(now: ZonedDateTime): List<WeatherWarning> {
        val snapshot = warnings.current()?.takeUnless { warnings.isStale(it, now.toInstant()) } ?: return emptyList()
        return WarningPolicy.active(snapshot.value.warnings, now)
    }

    /**
     * A warning goes first, on its own line, so it leads the summary and the morning brief; the weather line follows.
     * 预警排在最前、单独一行，在摘要和早间简报里都打头；天气那一行跟在后面。
     */
    override val brief = BriefContributor { now ->
        val lines = activeWarnings(now).take(MAX_BRIEF_WARNINGS).map { WarningText.line(it, now) } + listOfNotNull(weatherLine(now))
        when (lines.size) {
            0 -> null
            1 -> lines.first()
            else -> UiText.Lines(lines)
        }
    }

    private suspend fun weatherLine(now: ZonedDateTime): UiText? {
        val snapshot = source.current()?.takeUnless { source.isStale(it, now.toInstant()) } ?: return null
        val brief = WeatherBriefPolicy.brief(snapshot.value, now) ?: return null
        val condition = UiText.Res(conditionText(brief.condition))
        // Without an outlook for the day there is no clothing advice, but the reading itself is still worth a line.
        // 没有当天概况就没有穿衣建议，但当前读数本身仍值得一行。
        val clothing = brief.clothing ?: return UiText.Res(R.string.weather_brief_short, listOf(condition, brief.temperature))
        return UiText.Res(
            if (brief.umbrella) R.string.weather_brief_umbrella else R.string.weather_brief,
            listOf(condition, brief.temperature, UiText.Res(clothingText(clothing))),
        )
    }

    @Composable
    private fun Card() {
        val snapshot by source.observe().collectAsStateWithLifecycle(initialValue = null)
        val status by ctx.coordinator.status.collectAsStateWithLifecycle()
        val shown by place.collectAsStateWithLifecycle(initialValue = null)
        val alerts by warnings.observe().collectAsStateWithLifecycle(initialValue = null)
        // Home needs no label; any other place is named so the reading is not taken for Home's. / 家不用标注；其他地点标出名字，免得被当成家的天气。
        val label = shown?.takeIf { it.id != Place.HOME }?.let { placeTitle(it) }
        WeatherCard(
            snapshot = snapshot,
            error = status[source.id]?.lastError,
            isStale = source::isStale,
            placeLabel = label,
            // A warnings snapshot too old to trust shows nothing rather than a warning MET may have withdrawn.
            // 太旧的预警快照不显示，免得显示 MET 可能已经撤回的预警。
            warnings = alerts?.takeUnless { warnings.isStale(it, ctx.clock.now().toInstant()) }?.value?.warnings.orEmpty(),
            coordinates = shown?.let { WeatherParams(it.lat, it.lon) },
        )
    }
}

/** Two warnings at once is already rare; more would push the rest of the brief out of view. / 同时两条预警已经少见，再多会把简报其余内容挤出视野。 */
private const val MAX_BRIEF_WARNINGS = 2
