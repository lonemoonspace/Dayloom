package io.github.lonemoonspace.dayloom.app

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.app.settings.ModuleStatus
import io.github.lonemoonspace.dayloom.app.settings.SettingsViewModel
import io.github.lonemoonspace.dayloom.core.i18n.AppLanguage
import io.github.lonemoonspace.dayloom.core.i18n.LanguageChoice
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.module.ConfigState
import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.core.module.ModuleContext
import io.github.lonemoonspace.dayloom.core.module.ModuleInstance
import io.github.lonemoonspace.dayloom.core.storage.AppSettings
import io.github.lonemoonspace.dayloom.core.storage.InMemoryValueStore
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Module(override val id: String, override val defaultEnabled: Boolean) : FeatureModule {
        override val title = R.string.app_name
        override val summary = R.string.app_name
        override val icon = R.drawable.ic_notification
        override fun create(ctx: ModuleContext): ModuleInstance = object : ModuleInstance {}
    }

    private class FakeLanguage(var value: LanguageChoice = LanguageChoice.SYSTEM) : AppLanguage {
        override fun current() = value
        override fun set(choice: LanguageChoice) {
            value = choice
        }
    }

    private val settings = InMemoryValueStore(AppSettings())
    private val language = FakeLanguage()
    private val modules = listOf(Module("a", defaultEnabled = true), Module("b", defaultEnabled = false))

    private fun vm() = SettingsViewModel(
        modules = modules,
        settings = settings,
        sectionsOf = { emptyList() },
        zone = MutableStateFlow(ZoneId.of("Europe/Oslo")),
        language = language,
    )

    @Test
    fun `each module row says what is missing, else the module's own line`() = runTest {
        val lines = mapOf("a" to MutableStateFlow<UiText?>(UiText.Raw("Lindvik → Nordby")), "b" to MutableStateFlow<UiText?>(null))
        val configs = mapOf("a" to MutableStateFlow<ConfigState>(ConfigState.Ready), "b" to MutableStateFlow<ConfigState>(ConfigState.Ready))
        val vm = SettingsViewModel(
            modules = modules,
            settings = settings,
            sectionsOf = { emptyList() },
            statusOf = { lines.getValue(it.id) },
            configOf = { configs.getValue(it.id) },
            zone = MutableStateFlow(ZoneId.of("Europe/Oslo")),
            language = language,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        assertEquals(
            listOf(ModuleStatus.Line(UiText.Raw("Lindvik → Nordby")), ModuleStatus.Line(null)),
            vm.state.value.modules.map { it.status },
        )

        val missing = UiText.Raw("stops")
        configs.getValue("a").value = ConfigState.NeedsSetup(missing)
        assertEquals(ModuleStatus.NeedsSetup(missing), vm.state.value.modules.first().status)
    }

    @Test
    fun `a module that never answers stays loading without holding up the screen`() = runTest {
        val silent: Flow<UiText?> = emptyFlow()
        val vm = SettingsViewModel(
            modules = modules,
            settings = settings,
            sectionsOf = { emptyList() },
            statusOf = { if (it.id == "a") silent else MutableStateFlow(null) },
            configOf = { MutableStateFlow(ConfigState.Ready) },
            zone = MutableStateFlow(ZoneId.of("Europe/Oslo")),
            language = language,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        assertEquals(listOf(ModuleStatus.Loading, ModuleStatus.Line(null)), vm.state.value.modules.map { it.status })
        vm.setMorningBrief(true)
        assertTrue(vm.state.value.morningBrief)
    }

    @Test
    fun `module switches show defaults and save explicit choices`() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        assertEquals(listOf(true, false), vm.state.value.modules.map { it.enabled })

        vm.setModuleEnabled("b", true)
        assertEquals(mapOf("b" to true), settings.state.value.moduleEnabled)
        assertEquals(listOf(true, true), vm.state.value.modules.map { it.enabled })
    }

    @Test
    fun `an unknown time zone is rejected and nothing is saved, blank follows the device`() = runTest {
        val vm = vm()

        assertFalse(vm.setTimeZone("Mars/Base"))
        assertEquals("", settings.state.value.timeZoneOverride)

        assertTrue(vm.setTimeZone(" Asia/Shanghai "))
        assertEquals("Asia/Shanghai", settings.state.value.timeZoneOverride)

        assertTrue(vm.setTimeZone(""))
        assertEquals("", settings.state.value.timeZoneOverride)
    }

    @Test
    fun `the language choice goes to the system and shows up in the state`() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        vm.setLanguage(LanguageChoice.CHINESE)

        assertEquals(LanguageChoice.CHINESE, language.value)
        assertEquals(LanguageChoice.CHINESE, vm.state.value.language)
    }

    @Test
    fun `the morning brief and the end of the onboarding are saved`() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertFalse("off until the user opts in", vm.state.value.morningBrief)

        vm.setMorningBrief(true)
        assertTrue(vm.state.value.morningBrief)
        assertEquals("07:00 by default", 7 * 60, vm.state.value.morningBriefMinute)
        vm.setMorningBriefTime(6 * 60 + 30)
        assertEquals(6 * 60 + 30, vm.state.value.morningBriefMinute)
        vm.setMorningBriefTime(24 * 60)
        assertEquals("an impossible time is not saved", 6 * 60 + 30, vm.state.value.morningBriefMinute)

        assertFalse(settings.state.value.onboardingDone)
        vm.finishOnboarding()
        assertTrue(settings.state.value.onboardingDone)
    }

    @Test
    fun `the theme preset choice is saved and shows up in the state`() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        assertEquals("", vm.state.value.themePreset)
        vm.setThemePreset("nordic_dawn")
        assertEquals("nordic_dawn", settings.state.value.themePreset)
        assertEquals("nordic_dawn", vm.state.value.themePreset)
    }

    @Test
    fun `the woven background is on by default and can be turned off`() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        assertTrue(vm.state.value.weaveBackground)
        vm.setWeaveBackground(false)
        assertFalse(settings.state.value.weaveBackground)
        assertFalse(vm.state.value.weaveBackground)
    }
}
