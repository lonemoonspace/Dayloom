package io.github.lonemoonspace.dayloom.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lonemoonspace.dayloom.app.ActiveModule
import io.github.lonemoonspace.dayloom.app.ModulePolicy
import io.github.lonemoonspace.dayloom.core.i18n.AppLanguage
import io.github.lonemoonspace.dayloom.core.i18n.LanguageChoice
import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.core.module.SettingsSection
import io.github.lonemoonspace.dayloom.core.storage.AppSettings
import io.github.lonemoonspace.dayloom.core.storage.ValueStore
import io.github.lonemoonspace.dayloom.core.time.ZonePolicy
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModuleToggle(val module: FeatureModule, val enabled: Boolean)

data class SettingsState(
    val language: LanguageChoice = LanguageChoice.SYSTEM,
    /** Empty = follow the device. / 空 = 跟随设备。 */
    val timeZoneOverride: String = "",
    val effectiveZone: String = "",
    val modules: List<ModuleToggle> = emptyList(),
    /** Settings cards of the enabled modules, in registry order. / 已开启模块的设置卡片，按注册表顺序。 */
    val sections: List<SettingsCardEntry> = emptyList(),
    val morningBrief: Boolean = false,
)

class SettingsViewModel(
    private val modules: List<FeatureModule>,
    private val settings: ValueStore<AppSettings>,
    active: StateFlow<List<ActiveModule>?>,
    zone: StateFlow<ZoneId>,
    private val language: AppLanguage,
) : ViewModel() {

    private val languageChoice = MutableStateFlow(language.current())

    val state: StateFlow<SettingsState> = combine(settings.flow, active, zone, languageChoice) { s, act, z, lang ->
        val infos = modules.map { ModulePolicy.ModuleInfo(it.id, it.defaultEnabled, it.notificationIds) }
        val enabled = ModulePolicy.enabledIds(infos, s.moduleEnabled).toSet()
        SettingsState(
            language = lang,
            timeZoneOverride = s.timeZoneOverride,
            effectiveZone = z.id,
            modules = modules.map { ModuleToggle(it, it.id in enabled) },
            sections = act.orEmpty().flatMap { a -> a.instance.settingsSections.mapIndexed { i, section -> SettingsCardEntry(a, section, i) } },
            morningBrief = s.morningBrief,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

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

    fun setModuleEnabled(moduleId: String, enabled: Boolean) {
        viewModelScope.launch { settings.update { it.copy(moduleEnabled = it.moduleEnabled + (moduleId to enabled)) } }
    }
}

/** One settings card; [index] keeps the list keys of a module's cards apart. / 一张设置卡片；[index] 让同一模块的多张卡片列表键不重复。 */
class SettingsCardEntry(val active: ActiveModule, val section: SettingsSection, val index: Int)
