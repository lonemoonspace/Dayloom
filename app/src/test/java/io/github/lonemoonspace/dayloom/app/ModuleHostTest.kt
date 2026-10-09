package io.github.lonemoonspace.dayloom.app

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.app.home.HomeViewModel
import io.github.lonemoonspace.dayloom.core.location.PlaceBook
import io.github.lonemoonspace.dayloom.core.module.CardPlacement
import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.core.module.HomeCard
import io.github.lonemoonspace.dayloom.core.module.ModuleContext
import io.github.lonemoonspace.dayloom.core.module.ModuleInstance
import io.github.lonemoonspace.dayloom.core.module.ModuleSecret
import io.github.lonemoonspace.dayloom.core.module.ModuleTab
import io.github.lonemoonspace.dayloom.core.module.SettingsSection
import io.github.lonemoonspace.dayloom.core.network.FakeNetworkStatus
import io.github.lonemoonspace.dayloom.app.work.BackgroundRound
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.notify.AppNotification
import io.github.lonemoonspace.dayloom.core.notify.BriefContributor
import io.github.lonemoonspace.dayloom.core.notify.ChannelSpec
import io.github.lonemoonspace.dayloom.core.notify.CoreNotifications
import io.github.lonemoonspace.dayloom.core.notify.NotificationEngine
import io.github.lonemoonspace.dayloom.core.notify.NotificationStateStore
import io.github.lonemoonspace.dayloom.core.notify.Notifier
import io.github.lonemoonspace.dayloom.core.notify.NotificationRule
import io.github.lonemoonspace.dayloom.core.notify.RuleDecision
import io.github.lonemoonspace.dayloom.core.notify.RuleInput
import io.github.lonemoonspace.dayloom.core.notify.StringStateCodec
import io.github.lonemoonspace.dayloom.core.refresh.CachedSource
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCoordinator
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.refresh.SourceInput
import io.github.lonemoonspace.dayloom.core.refresh.Trigger
import io.github.lonemoonspace.dayloom.core.routine.Routine
import io.github.lonemoonspace.dayloom.core.secret.SecretState
import io.github.lonemoonspace.dayloom.core.storage.AppSettings
import io.github.lonemoonspace.dayloom.core.storage.InMemorySnapshotStore
import io.github.lonemoonspace.dayloom.core.storage.InMemoryValueStore
import io.github.lonemoonspace.dayloom.core.storage.SharedData
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.storage.SnapshotStore
import io.github.lonemoonspace.dayloom.core.storage.ValueStore
import io.github.lonemoonspace.dayloom.core.time.AppClock
import io.github.lonemoonspace.dayloom.core.time.FixedClock
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Acceptance test of design §4.4: a module written only against core plugs into the host, the coordinator, notifications and
 * the home screen without changing any of them.
 * 设计文档 §4.4 的验收测试：一个只依赖 core 写成的模块，不改宿主、协调器、通知与首页中的任何一处就能接入。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModuleHostTest {

    private val clock = FixedClock(ZonedDateTime.of(2026, 10, 9, 8, 0, 0, 0, ZoneId.of("Europe/Oslo")))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ---- A module as a third party would write it / 一个按第三方方式写成的模块 ----

    private class SampleSource(ctx: ModuleContext, param: Flow<String>) :
        CachedSource<String, String>(ctx.sourceId("main"), ctx.snapshots(ctx.sourceId("main"), String.serializer()), ctx.clock) {
        override val schemaVersion = 1
        override val maxAge: Duration = Duration.ofHours(1)
        override val inputs = param.map { SourceInput.Ready(it, key = it) }
        var fetches = 0
        override suspend fun fetch(params: String, now: ZonedDateTime, previous: Snapshot<String>?): String {
            fetches++
            return "hello $params"
        }
    }

    private class SampleInstance(ctx: ModuleContext) : ModuleInstance {
        val placement = MutableStateFlow(CardPlacement.NORMAL)
        val source = SampleSource(ctx, flowOf("world"))
        override val sources = listOf(source)
        override val homeCards = listOf(HomeCard("card", R.string.app_name, defaultOrder = 10, placement = placement) {})
        override val tab = ModuleTab(R.string.app_name, R.drawable.ic_notification) {}
        override val settings = SettingsSection {}
        override val notificationChannels = listOf(ChannelSpec("alerts", R.string.app_name, R.string.app_name))
        override val notificationRules = listOf(object : NotificationRule<String?> {
            override val name = "seen"
            override val codec = StringStateCodec
            override suspend fun isEnabled() = true
            override suspend fun evaluate(input: RuleInput, previous: String?) =
                RuleDecision(if (input.report.succeeded(source.id)) "fresh" else previous)
        })
        override val brief = BriefContributor { UiText.Raw("sample line") }
    }

    private class SampleModule(
        override val id: String = "sample",
        override val defaultEnabled: Boolean = true,
        override val notificationIds: IntRange = 5_000..5_099,
    ) : FeatureModule {
        override val title = R.string.app_name
        override val summary = R.string.app_name
        override val icon = R.drawable.ic_notification
        var created = 0
        lateinit var instance: SampleInstance
        override fun create(ctx: ModuleContext): ModuleInstance {
            created++
            return SampleInstance(ctx).also { instance = it }
        }
    }

    // ---- Test plumbing / 测试装配 ----

    private class TestContext(
        override val moduleId: String,
        override val clock: AppClock,
        override val coordinator: RefreshCoordinator,
        override val appScope: CoroutineScope,
    ) : ModuleContext {
        override val http = OkHttpClient()
        override val connectivity = FakeNetworkStatus()
        override val places = PlaceBook(InMemoryValueStore(SharedData()))
        override val routine = flowOf(Routine())
        override fun <T> settings(serializer: KSerializer<T>, default: T): ValueStore<T> = InMemoryValueStore(default)
        override fun <T> snapshots(sourceId: SourceId, serializer: KSerializer<T>): SnapshotStore<T> = InMemorySnapshotStore()
        override fun secret(name: String) = object : ModuleSecret {
            override fun observe() = flowOf(SecretState.EMPTY)
            override suspend fun usable() = ""
            override suspend fun put(plain: String) {}
        }
        override fun notificationId(offset: Int) = offset
    }

    private fun CoroutineScope.host(modules: List<FeatureModule>, settings: InMemoryValueStore<AppSettings>): Pair<ModuleHost, RefreshCoordinator> {
        val coordinator = RefreshCoordinator(FakeNetworkStatus(), clock, this, watchInputs = false)
        val host = ModuleHost(modules, settings, { TestContext(it.id, clock, coordinator, this) }, coordinator, this)
        return host to coordinator
    }

    @Test
    fun `an enabled module is created once and its sources, rules and channels are wired with namespaced names`() = runTest {
        val module = SampleModule()
        val settings = InMemoryValueStore(AppSettings())
        val (host, coordinator) = backgroundScope.host(listOf(module), settings)
        testScheduler.runCurrent()

        val active = host.active.value!!
        assertEquals(listOf("sample"), active.map { it.module.id })
        assertEquals(setOf(SourceId("sample.main")), coordinator.sourceIds)
        assertEquals(setOf(SourceId("sample.main")), ModuleHost.sourceIds(active))
        assertEquals(listOf("sample.seen"), ModuleHost.rules(active).map { it.stateKey })
        assertEquals(setOf("sample.alerts", "core.brief"), ModuleHost.channels(active).keys)

        coordinator.refresh(ModuleHost.sourceIds(active), Trigger.USER)
        assertEquals(1, module.instance.source.fetches)
    }

    @Test
    fun `disabling hides a module and enabling again reuses the same instance`() = runTest {
        val module = SampleModule()
        val settings = InMemoryValueStore(AppSettings())
        val (host, _) = backgroundScope.host(listOf(module), settings)
        testScheduler.runCurrent()
        val first = host.active.value!!.single().instance

        settings.update { it.copy(moduleEnabled = mapOf("sample" to false)) }
        testScheduler.runCurrent()
        assertEquals(emptyList<ActiveModule>(), host.active.value)

        settings.update { it.copy(moduleEnabled = mapOf("sample" to true)) }
        testScheduler.runCurrent()
        assertSame(first, host.active.value!!.single().instance)
        assertEquals("created only once, so its sources are registered only once", 1, module.created)
    }

    @Test
    fun `a module that is off by default is not created until turned on`() = runTest {
        val module = SampleModule(defaultEnabled = false)
        val settings = InMemoryValueStore(AppSettings())
        val (host, coordinator) = backgroundScope.host(listOf(module), settings)
        testScheduler.runCurrent()

        assertEquals(emptyList<ActiveModule>(), host.active.value)
        assertEquals(0, module.created)
        assertTrue(coordinator.sourceIds.isEmpty())
    }

    @Test
    fun `the home screen shows the new module's card and follows its placement`() = runTest {
        val module = SampleModule()
        val other = SampleModule(id = "other", notificationIds = 6_000..6_099)
        val settings = InMemoryValueStore(AppSettings(cardOrder = listOf("other.card", "sample.card")))
        val (host, coordinator) = backgroundScope.host(listOf(module, other), settings)
        val vm = HomeViewModel(host.active, settings, coordinator, flowOf(Routine()), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        testScheduler.runCurrent()

        assertEquals(listOf("other.card", "sample.card"), vm.state.value.cards.map { it.key })

        module.instance.placement.value = CardPlacement.TOP
        testScheduler.runCurrent()
        assertEquals(listOf("sample.card", "other.card"), vm.state.value.cards.map { it.key })
        assertEquals("pinning does not change the user's order", listOf("other.card", "sample.card"), vm.state.value.userOrder.map { it.key })

        vm.saveOrder(listOf("sample.card", "other.card"))
        testScheduler.runCurrent()
        assertEquals(listOf("sample.card", "other.card"), settings.state.value.cardOrder)
    }

    private class MemoryState : NotificationStateStore {
        val values = mutableMapOf<String, String>()
        override suspend fun read(key: String) = values[key]
        override suspend fun write(key: String, value: String) {
            values[key] = value
        }
    }

    private class Sent : Notifier {
        val notifications = mutableListOf<AppNotification>()
        override fun send(notification: AppNotification) {
            notifications += notification
        }
    }

    @Test
    fun `a background round refreshes due sources, runs the module rules and sends the morning brief`() = runTest {
        val module = SampleModule()
        val settings = InMemoryValueStore(AppSettings(morningBrief = true))
        val (host, coordinator) = backgroundScope.host(listOf(module), settings)
        val state = MemoryState()
        val sent = Sent()
        // Friday 08:00, inside the default to-work window. / 周五 08:00，在默认的上班时间窗内。
        val round = BackgroundRound(
            active = host.active,
            routine = flowOf(Routine()),
            clock = clock,
            coordinator = coordinator,
            engine = NotificationEngine(state, sent),
            morningBrief = { settings.get().morningBrief },
        )
        testScheduler.runCurrent()

        assertFalse("success needs no retry", round.run())
        assertEquals(1, module.instance.source.fetches)
        assertEquals("fresh", state.values["sample.seen"])
        val brief = sent.notifications.single()
        assertEquals(UiText.Lines(listOf(UiText.Raw("sample line"))), brief.body)
        assertEquals("2026-10-09", state.values[CoreNotifications.BRIEF_STATE_KEY])

        round.run()
        assertEquals("not due again within its cadence", 1, module.instance.source.fetches)
        assertEquals("the brief goes out once a day", 1, sent.notifications.size)
    }

    @Test
    fun `the visible home screen refreshes due sources and stops when hidden`() = runTest {
        val module = SampleModule()
        val settings = InMemoryValueStore(AppSettings())
        val (host, coordinator) = backgroundScope.host(listOf(module), settings)
        val vm = HomeViewModel(host.active, settings, coordinator, flowOf(Routine()), clock)
        testScheduler.runCurrent()

        vm.startPolling()
        testScheduler.runCurrent()
        assertEquals("refreshed on becoming visible", 1, module.instance.source.fetches)

        testScheduler.advanceTimeBy(61_000)
        testScheduler.runCurrent()
        assertEquals("still fresh a minute later", 1, module.instance.source.fetches)

        clock.current = clock.current.plusHours(2)
        testScheduler.advanceTimeBy(60_000)
        testScheduler.runCurrent()
        assertEquals("due again", 2, module.instance.source.fetches)

        vm.stopPolling()
        clock.current = clock.current.plusHours(2)
        testScheduler.advanceTimeBy(120_000)
        testScheduler.runCurrent()
        assertEquals("hidden screens do not poll", 2, module.instance.source.fetches)
    }

    @Test
    fun `an invalid registry is rejected at startup`() = runTest {
        val settings = InMemoryValueStore(AppSettings())
        assertThrows(IllegalArgumentException::class.java) {
            backgroundScope.host(listOf(SampleModule(), SampleModule()), settings)
        }
    }
}
