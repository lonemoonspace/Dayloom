package io.github.lonemoonspace.dayloom.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.core.module.HomeCard
import io.github.lonemoonspace.dayloom.core.module.ModuleContext
import io.github.lonemoonspace.dayloom.core.module.ModuleInstance
import io.github.lonemoonspace.dayloom.core.module.SettingsSection
import io.github.lonemoonspace.dayloom.core.ui.SwitchRow
import io.github.lonemoonspace.dayloom.feature.calendar.data.LunarJavaProvider
import io.github.lonemoonspace.dayloom.feature.calendar.domain.HolidayCountry
import io.github.lonemoonspace.dayloom.feature.calendar.ui.CalendarHeader
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * The date header at the top of the home screen: clock, date, ISO week, optional Chinese lunar calendar and the holidays of
 * the chosen countries. Everything is computed locally, so it has no data source.
 * 首页顶部的日期页头：时钟、日期、ISO 周数、可选的中国农历，以及所选国家的节日。全部本地计算，所以没有数据来源。
 */
object CalendarModule : FeatureModule {
    override val id = "calendar"
    override val title = R.string.calendar_title
    override val summary = R.string.calendar_summary
    override val icon = R.drawable.ic_calendar
    override val defaultEnabled = true

    override fun create(ctx: ModuleContext): ModuleInstance = CalendarInstance(ctx)
}

@Serializable
data class CalendarSettings(
    /** Reserved for migrations after v1.0.0. / 预留给 v1.0.0 之后的迁移。 */
    val version: Int = 1,
    val showLunar: Boolean = false,
    val holidayCountries: Set<HolidayCountry> = emptySet(),
)

private class CalendarInstance(private val ctx: ModuleContext) : ModuleInstance {
    private val settingsStore = ctx.settings(CalendarSettings.serializer(), CalendarSettings())
    private val lunar = LunarJavaProvider()

    // First by default: it is the page header. / 默认排第一：它是页头。
    override val homeCards = listOf(
        HomeCard(key = "header", title = R.string.calendar_title, defaultOrder = 0) {
            val settings by settingsStore.flow.collectAsStateWithLifecycle(initialValue = null)
            settings?.let { CalendarHeader(it.showLunar, it.holidayCountries, lunar) }
        },
    )

    override val settings = SettingsSection { Settings() }

    @Composable
    private fun Settings() {
        val saved by settingsStore.flow.collectAsStateWithLifecycle(initialValue = CalendarSettings())
        // Saved in appScope so leaving the screen right away does not cancel the write. / 在 appScope 里保存，立刻离开页面也不会取消写入。
        fun update(transform: (CalendarSettings) -> CalendarSettings) {
            ctx.appScope.launch { settingsStore.update(transform) }
        }
        SwitchRow(
            label = stringResource(R.string.calendar_show_lunar),
            summary = stringResource(R.string.calendar_show_lunar_summary),
            checked = saved.showLunar,
            onCheckedChange = { on -> update { it.copy(showLunar = on) } },
        )
        HolidayCountry.entries.forEach { country ->
            SwitchRow(
                label = stringResource(
                    R.string.calendar_holidays_of,
                    stringResource(
                        when (country) {
                            HolidayCountry.CN -> R.string.calendar_country_china
                            HolidayCountry.NO -> R.string.calendar_country_norway
                        },
                    ),
                ),
                checked = country in saved.holidayCountries,
                onCheckedChange = { on ->
                    update { it.copy(holidayCountries = if (on) it.holidayCountries + country else it.holidayCountries - country) }
                },
            )
        }
    }
}
