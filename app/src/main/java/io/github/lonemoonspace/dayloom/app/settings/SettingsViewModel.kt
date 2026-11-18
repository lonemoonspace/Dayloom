package io.github.lonemoonspace.dayloom.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lonemoonspace.dayloom.app.ModulePolicy
import io.github.lonemoonspace.dayloom.core.i18n.AppLanguage
import io.github.lonemoonspace.dayloom.core.i18n.LanguageChoice
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.module.ConfigState
import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.core.module.SettingsSection
import io.github.lonemoonspace.dayloom.core.storage.AppSettings
import io.github.lonemoonspace.dayloom.core.storage.ValueStore
import io.github.lonemoonspace.dayloom.core.time.ZonePolicy
import java.time.ZoneId
import io.github.lonemoonspace.dayloom.core.notify.MorningBriefPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A module's switch and its settings cards; the cards are listed even while it is off, so the settings can be shown greyed out.
 * 模块的开关及其设置卡片；关闭时也列出这些卡片，好让设置以灰色显示。
 */
data class ModuleToggle(
    val module: FeatureModule,
    val enabled: Boolean,
    val sections: List<SettingsSection> = emptyList(),
    /** The line under the module's name in the settings list, once known. / 设置列表里模块名下面那行字，读到之后才有。 */
    val status: ModuleStatus = ModuleStatus.Loading,
)

/**
 * What the settings list says under a module's name: still reading, what is missing, the module's own line, or nothing of
 * its own (the list then shows the module's summary).
 * 设置列表在模块名下面写什么：还在读取、缺什么设置、模块自己给的一行字，或者模块没给（这时显示模块的简介）。
 */
sealed interface ModuleStatus {
    data object Loading : ModuleStatus

    data class NeedsSetup(val what: UiText) : ModuleStatus

    data class Line(val text: UiText?) : ModuleStatus
}

data class SettingsState(
    val language: LanguageChoice = LanguageChoice.SYSTEM,
    /** Empty = follow the device. / 空 = 跟随设备。 */
    val timeZoneOverride: String = "",
    val effectiveZone: String = "",
    val themePreset: String = "",
    val weaveBackground: Boolean = true,
    val homeSummary: Boolean = true,
    val modules: List<ModuleToggle> = emptyList(),
    val morningBrief: Boolean = false,
    /** Minutes after midnight. / 午夜起的分钟数。 */
    val morningBriefMinute: Int = 7 * 60,
)

class SettingsViewModel(
    private val modules: List<FeatureModule>,
    private val settings: ValueStore<AppSettings>,
    /** A module's settings cards; creating its instance for this does not start its refreshes. / 模块的设置卡片；为此创建实例不会启动它的刷新。 */
    private val sectionsOf: (FeatureModule) -> List<SettingsSection>,
    /** A module's own status line ([io.github.lonemoonspace.dayloom.core.module.ModuleInstance.settingsStatus]). / 模块自己的状态行。 */
    statusOf: (FeatureModule) -> Flow<UiText?> = { flowOf(null) },
    /** Whether a module is set up. / 模块是否已设置好。 */
    configOf: (FeatureModule) -> Flow<ConfigState> = { flowOf(ConfigState.Ready) },
    zone: StateFlow<ZoneId>,
    private val language: AppLanguage,
) : ViewModel() {

    private val languageChoice = MutableStateFlow(language.current())

    /**
     * Each module's line; one that has not answered yet stays [ModuleStatus.Loading] instead of holding up the whole screen.
     * 每个模块的那行字；还没给出结果的模块保持 [ModuleStatus.Loading]，不会拖住整个页面。
     */
    private val statuses: Flow<Map<String, ModuleStatus>> = modules
        .map { m ->
            combine(statusOf(m), configOf(m)) { line, config ->
                m.id to if (config is ConfigState.NeedsSetup) ModuleStatus.NeedsSetup(config.what) else ModuleStatus.Line(line)
            }.onStart { emit(m.id to ModuleStatus.Loading) }
        }
        .let { flows -> if (flows.isEmpty()) flowOf(emptyMap()) else combine(flows) { it.toMap() } }

    val state: StateFlow<SettingsState> = combine(settings.flow, zone, languageChoice, statuses) { s, z, lang, lines ->
        val infos = modules.map { ModulePolicy.ModuleInfo(it.id, it.defaultEnabled, it.notificationIds, it.splitFrom) }
        val enabled = ModulePolicy.enabledIds(infos, s.moduleEnabled).toSet()
        SettingsState(
            language = lang,
            timeZoneOverride = s.timeZoneOverride,
            effectiveZone = z.id,
            themePreset = s.themePreset,
            weaveBackground = s.weaveBackground,
            homeSummary = s.homeSummary,
            modules = modules.map { ModuleToggle(it, it.id in enabled, sectionsOf(it), lines[it.id] ?: ModuleStatus.Loading) },
            morningBrief = s.morningBrief,
            morningBriefMinute = s.morningBriefMinute,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setThemePreset(preset: String) {
        viewModelScope.launch { settings.update { it.copy(themePreset = preset) } }
    }

    fun setWeaveBackground(on: Boolean) {
        viewModelScope.launch { settings.update { it.copy(weaveBackground = on) } }
    }

    fun setHomeSummary(on: Boolean) {
        viewModelScope.launch { settings.update { it.copy(homeSummary = on) } }
    }

    fun setLanguage(choice: LanguageChoice) {
        language.set(choice)
        languageChoice.value = choice
    }

    /**
     * Saves a time zone override; returns false (and saves nothing) when [id] is not a known zone. Blank follows the device.
     * 保存时区覆盖值；[id] 不是已知时区时返回 false 且不保存。空白表示跟随设备。
     */
    fun setTimeZone(id: String): Boolean {
        val trimmed = id.trim()
        if (trimmed.isNotEmpty() && ZonePolicy.parse(trimmed) == null) return false
        viewModelScope.launch { settings.update { it.copy(timeZoneOverride = trimmed) } }
        return true
    }

    fun finishOnboarding() {
        viewModelScope.launch { settings.update { it.copy(onboardingDone = true) } }
    }

    fun setMorningBrief(enabled: Boolean) {
        viewModelScope.launch { settings.update { it.copy(morningBrief = enabled) } }
    }

    fun setMorningBriefTime(minute: Int) {
        if (!MorningBriefPolicy.isValidMinute(minute)) return
        viewModelScope.launch { settings.update { it.copy(morningBriefMinute = minute) } }
    }

    fun setModuleEnabled(moduleId: String, enabled: Boolean) {
        viewModelScope.launch { settings.update { it.copy(moduleEnabled = it.moduleEnabled + (moduleId to enabled)) } }
    }
}

/** One settings card; [index] keeps the list keys of a module's cards apart. / 一张设置卡片；[index] 让同一模块的多张卡片列表键不重复。 */
