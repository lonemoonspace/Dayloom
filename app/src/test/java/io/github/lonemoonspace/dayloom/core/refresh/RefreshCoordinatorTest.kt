package io.github.lonemoonspace.dayloom.core.refresh

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.network.FakeNetworkStatus
import io.github.lonemoonspace.dayloom.core.storage.InMemorySnapshotStore
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.time.FixedClock
import java.io.IOException
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The six semantics in RefreshCoordinator's KDoc, plus silent triggers, not-configured skips and runtime registration.
 * The coordinator's appScope is runTest's backgroundScope; in-flight refreshes are made with [FakeSource.gate].
 * RefreshCoordinator 类注释里的六条语义，外加静默触发、未配置跳过与运行时注册。
 * 协调器的 appScope 用 runTest 的 backgroundScope；在途刷新用 [FakeSource.gate] 制造。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshCoordinatorTest {

    private val clock = FixedClock(ZonedDateTime.of(2026, 9, 29, 8, 0, 0, 0, ZoneId.of("Europe/Oslo")))

    private fun TestScope.coordinator(
        vararg sources: FakeSource,
        online: Boolean = true,
        watch: Boolean = false,
        onWatchError: (SourceId?, Throwable) -> Unit = { _, _ -> },
    ) = RefreshCoordinator(FakeNetworkStatus(online), clock, backgroundScope, watch, onWatchError).apply {
        register(sources.toList())
    }

    private fun old(key: String) = Snapshot("old-$key", 0L, key, 1)

    private fun RefreshCoordinator.statusOf(source: FakeSource) = status.value.getValue(source.id)

    // ---- 1. Parallel, independent / 并行、互不影响 ----

    @Test
    fun `sources refresh in parallel and one failure only reports its own error`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val weather = FakeSource("weather", clock).apply { this.gate = gate }
        val train = FakeSource("train", clock).apply {
            this.gate = gate
            failWith = AppError.Network(IOException("boom"))
        }
        val c = coordinator(weather, train)

        val report = async { c.refresh(setOf(weather.id, train.id), Trigger.USER) }
        runCurrent()
        assertEquals("both in flight at once", 1 to 1, weather.fetchCount to train.fetchCount)

        gate.complete(Unit)
        val results = report.await().results

        assertTrue(results[weather.id] is SourceResult.Success)
        assertTrue((results[train.id] as SourceResult.Failed).error is AppError.Network)
        assertNull(c.statusOf(weather).lastError)
        assertTrue(c.statusOf(train).lastError is AppError.Network)
    }

    @Test
    fun `a cancelled fetch fails only its own source and the others still finish`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val weather = FakeSource("weather", clock).apply { this.gate = gate }
        val train = FakeSource("train", clock).apply {
            this.gate = gate
            failWith = CancellationException("cancelled inside the fetch")
        }
        val c = coordinator(weather, train)

        val report = async { c.refresh(setOf(weather.id, train.id), Trigger.USER) }
        runCurrent()
        gate.complete(Unit)
        val results = report.await().results

        assertTrue(results[weather.id] is SourceResult.Success)
        assertTrue(results[train.id] is SourceResult.Failed)
        assertEquals("A#1", weather.store.current?.value)
        assertFalse(c.statusOf(train).refreshing)
    }

    // ---- 2. Per-source single-flight / 按来源 single-flight ----

    @Test
    fun `concurrent refreshes with the same params share one fetch`() = runTest {
        val source = FakeSource("weather", clock).apply { gate = CompletableDeferred() }
        val c = coordinator(source)

        val first = async { c.refresh(setOf(source.id), Trigger.AUTO) }
        val second = async { c.refresh(setOf(source.id), Trigger.BACKGROUND) }
        runCurrent()
        assertEquals(1, source.fetchCount)

        source.gate!!.complete(Unit)

        assertEquals(first.await().results, second.await().results)
        assertEquals(1, source.fetchCount)
        assertEquals(1, source.store.writes)
    }

    @Test
    fun `a refresh with new params waits for the in-flight one and then refetches with the new inputs`() = runTest {
        val source = FakeSource("weather", clock).apply { gate = CompletableDeferred() }
        val c = coordinator(source)

        val first = async { c.refresh(setOf(source.id), Trigger.AUTO) }
        runCurrent()
        source.param.value = "B"
        val second = async { c.refresh(setOf(source.id), Trigger.INPUTS_CHANGED) }
        runCurrent()
        assertEquals("old params still in flight, new ones must wait", 1, source.fetchCount)

        source.gate!!.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("A", "B"), source.fetchedWith)
        assertEquals("B", source.store.current?.paramsKey)
    }

    @Test
    fun `after waiting, a caller re-reads the inputs and shares the finished result if params match again`() = runTest {
        val source = FakeSource("weather", clock).apply { gate = CompletableDeferred() }
        val c = coordinator(source)

        val first = async { c.refresh(setOf(source.id), Trigger.AUTO) }
        runCurrent()
        source.param.value = "B"
        val second = async { c.refresh(setOf(source.id), Trigger.INPUTS_CHANGED) }
        runCurrent()
        source.param.value = "A" // changed back / 又改回来了

        source.gate!!.complete(Unit)

        assertEquals(first.await().results, second.await().results)
        assertEquals(1, source.fetchCount)
    }

    // ---- 3. Offline / 离线 ----

    @Test
    fun `offline automatic refreshes are skipped, keep the snapshot and report Offline`() = runTest {
        for (trigger in listOf(Trigger.AUTO, Trigger.INPUTS_CHANGED)) {
            val source = FakeSource("weather", clock, InMemorySnapshotStore(old("A")))
            val c = coordinator(source, online = false)

            val report = c.refresh(setOf(source.id), trigger)

            assertEquals(SourceResult.Skipped(SkipReason.OFFLINE), report.results[source.id])
            assertEquals(0, source.fetchCount)
            assertEquals(old("A"), source.store.current)
            assertTrue(c.statusOf(source).lastError is AppError.Offline)
        }
    }

    @Test
    fun `offline, a source that is not set up reports what is missing rather than offline`() = runTest {
        val unset = FakeSource("weather", clock, initialParam = "")
        val ready = FakeSource("train", clock)
        val c = coordinator(unset, ready, online = false)

        val results = c.refresh(setOf(unset.id, ready.id), Trigger.AUTO).results

        assertEquals(SourceResult.Skipped(SkipReason.NOT_CONFIGURED), results[unset.id])
        assertTrue(c.statusOf(unset).lastError is AppError.NotConfigured)
        assertEquals(SourceResult.Skipped(SkipReason.OFFLINE), results[ready.id])
        assertTrue(c.statusOf(ready).lastError is AppError.Offline)
    }

    @Test
    fun `offline user refresh is not skipped`() = runTest {
        val source = FakeSource("weather", clock)
        val c = coordinator(source, online = false)

        val report = c.refresh(setOf(source.id), Trigger.USER)

        assertEquals(1, source.fetchCount)
        assertTrue(report.results[source.id] is SourceResult.Success)
    }

    // ---- 4. Inputs read one-shot / 一次性读取输入 ----

    @Test
    fun `refresh uses the inputs current at call time`() = runTest {
        val source = FakeSource("weather", clock)
        val c = coordinator(source)
        source.param.value = "Stored"

        c.refresh(setOf(source.id), Trigger.AUTO)

        assertEquals(listOf("Stored"), source.fetchedWith)
    }

    @Test
    fun `an unreadable input fails only that source and records the error`() = runTest {
        val broken = FakeSource("weather", clock).apply { inputsOverride = flow { throw IOException("disk") } }
        val fine = FakeSource("train", clock)
        val c = coordinator(broken, fine)

        val results = c.refresh(setOf(broken.id, fine.id), Trigger.USER).results

        assertTrue((results[broken.id] as SourceResult.Failed).error is AppError.Network)
        assertTrue(results[fine.id] is SourceResult.Success)
        assertTrue(c.statusOf(broken).lastError is AppError.Network)
    }

    // ---- 5. Watching inputs / 监听输入 ----

    @Test
    fun `a params change refreshes only the affected source, the first value is just a baseline`() = runTest {
        val weather = FakeSource("weather", clock)
        val train = FakeSource("train", clock)
        coordinator(weather, train, watch = true)

        runCurrent()
        assertEquals("first frame is a baseline", 0 to 0, weather.fetchCount to train.fetchCount)

        weather.param.value = "B"
        runCurrent()
        assertEquals(1 to 0, weather.fetchCount to train.fetchCount)
        assertEquals("B", weather.store.current?.paramsKey)
    }

    @Test
    fun `a refreshKey change triggers a refresh even when the params key is unchanged`() = runTest {
        val traffic = FakeSource("traffic", clock)
        coordinator(traffic, watch = true)
        runCurrent()

        traffic.signal.value = 1 // e.g. a new API key fingerprint / 例如换了 API Key 的指纹
        runCurrent()

        assertEquals(1, traffic.fetchCount)
        assertEquals("A", traffic.store.current?.paramsKey)
    }

    @Test
    fun `a signal change is ignored while the source is not configured`() = runTest {
        val traffic = FakeSource("traffic", clock, initialParam = " ")
        val c = coordinator(traffic, watch = true)
        runCurrent()

        traffic.signal.value = 1
        runCurrent()

        assertEquals(0, traffic.fetchCount)
        assertNull("no 'set up first' error just because the key changed", c.statusOf(traffic).lastError)
    }

    @Test
    fun `becoming unconfigured surfaces the missing item`() = runTest {
        val weather = FakeSource("weather", clock)
        val c = coordinator(weather, watch = true)
        runCurrent()

        weather.param.value = ""
        runCurrent()

        val error = c.statusOf(weather).lastError as AppError.NotConfigured
        assertEquals(FakeSource.MISSING, error.what)
    }

    @Test
    fun `a failing inputs flow is reported, does not stop watching others, and is resubscribed`() = runTest {
        var subscriptions = 0
        val broken = FakeSource("weather", clock)
        broken.inputsOverride = flow {
            subscriptions++
            if (subscriptions == 1) throw IOException("disk")
            emitAll(kotlinx.coroutines.flow.flowOf(FakeSource.input("A")))
        }
        val train = FakeSource("train", clock)
        val errors = mutableListOf<Pair<SourceId?, Throwable>>()
        coordinator(broken, train, watch = true, onWatchError = { id, e -> errors += id to e })
        runCurrent()

        assertEquals(broken.id, errors.single().first)
        train.param.value = "B"
        runCurrent()
        assertEquals("the other source is still watched", 1, train.fetchCount)

        advanceTimeBy(1_500)
        runCurrent()
        assertEquals("resubscribed after the backoff", 2, subscriptions)
        assertEquals("the recovered value is its baseline, not a change", 0, broken.fetchCount)
    }

    @Test
    fun `inputs are not watched when the switch is off`() = runTest {
        val weather = FakeSource("weather", clock)
        coordinator(weather, watch = false)

        weather.param.value = "B"
        runCurrent()

        assertEquals(0, weather.fetchCount)
    }

    @Test
    fun `a source registered later is watched with its own baseline`() = runTest {
        val weather = FakeSource("weather", clock)
        val c = coordinator(weather, watch = true)
        runCurrent()

        val late = FakeSource("late", clock)
        c.register(listOf(late))
        runCurrent()
        assertEquals("registering is not a change", 0, late.fetchCount)

        late.param.value = "B"
        runCurrent()
        assertEquals(1, late.fetchCount)
        assertEquals(0, weather.fetchCount)
    }

    @Test
    fun `registering another source under an existing id fails`() = runTest {
        val weather = FakeSource("weather", clock)
        val c = coordinator(weather)

        c.register(listOf(weather)) // the same instance again is fine / 同一个实例重复注册没问题
        assertThrows(IllegalArgumentException::class.java) { c.register(listOf(FakeSource("weather", clock))) }
    }

    // ---- 6. Errors and snapshots / 错误与快照 ----

    @Test
    fun `failure keeps the snapshot and records the error, a later success clears it`() = runTest {
        val source = FakeSource("weather", clock, InMemorySnapshotStore(old("A"))).apply {
            failWith = AppError.Network(IOException("down"))
        }
        val c = coordinator(source)

        c.refresh(setOf(source.id), Trigger.USER)
        assertEquals(old("A"), source.store.current)
        assertTrue(c.statusOf(source).lastError is AppError.Network)

        source.failWith = null
        c.refresh(setOf(source.id), Trigger.USER)
        assertNull(c.statusOf(source).lastError)
        assertEquals("A#2", source.store.current?.value)
    }

    @Test
    fun `refreshing is true only while a fetch is in flight`() = runTest {
        val source = FakeSource("weather", clock).apply { gate = CompletableDeferred() }
        val c = coordinator(source)

        val job = async { c.refresh(setOf(source.id), Trigger.AUTO) }
        runCurrent()
        assertTrue(c.statusOf(source).refreshing)

        source.gate!!.complete(Unit)
        job.await()
        assertFalse(c.statusOf(source).refreshing)
    }

    // ---- Not configured / 未配置 ----

    @Test
    fun `missing inputs skip the source as not configured and surface what is missing`() = runTest {
        val source = FakeSource("weather", clock, initialParam = " ")
        val c = coordinator(source)

        val report = c.refresh(setOf(source.id), Trigger.AUTO)

        assertEquals(SourceResult.Skipped(SkipReason.NOT_CONFIGURED), report.results[source.id])
        assertEquals(0, source.fetchCount)
        assertSame(FakeSource.MISSING, (c.statusOf(source).lastError as AppError.NotConfigured).what)
    }

    @Test
    fun `NotConfigured thrown by the fetch is also a not-configured skip`() = runTest {
        val source = FakeSource("football", clock).apply { failWith = AppError.NotConfigured(FakeSource.MISSING) }
        val c = coordinator(source)

        val report = c.refresh(setOf(source.id), Trigger.AUTO)

        assertEquals(SourceResult.Skipped(SkipReason.NOT_CONFIGURED), report.results[source.id])
        assertTrue(c.statusOf(source).lastError is AppError.NotConfigured)
    }

    // ---- Silent triggers / 静默触发 ----

    @Test
    fun `live poll neither shows refreshing nor records failures or offline`() = runTest {
        val source = FakeSource("football", clock).apply { gate = CompletableDeferred() }
        val c = coordinator(source)

        val job = async { c.refresh(setOf(source.id), Trigger.LIVE_POLL) }
        runCurrent()
        assertFalse(c.statusOf(source).refreshing)

        source.failWith = AppError.Network(IOException("blip"))
        source.gate!!.complete(Unit)
        assertTrue(job.await().results[source.id] is SourceResult.Failed)
        assertNull(c.statusOf(source).lastError)

        val offlineSource = FakeSource("football", clock)
        val offline = coordinator(offlineSource, online = false)
        offline.refresh(setOf(offlineSource.id), Trigger.LIVE_POLL)
        assertNull(offline.statusOf(offlineSource).lastError)
    }

    @Test
    fun `a user refresh joining a silent poll makes it visible`() = runTest {
        val source = FakeSource("football", clock).apply { gate = CompletableDeferred() }
        val c = coordinator(source)

        val poll = async { c.refresh(setOf(source.id), Trigger.LIVE_POLL) }
        runCurrent()
        val user = async { c.refresh(setOf(source.id), Trigger.USER) }
        runCurrent()
        assertTrue(c.statusOf(source).refreshing)

        source.failWith = AppError.Network(IOException("down"))
        source.gate!!.complete(Unit)
        poll.await()
        user.await()

        assertEquals(1, source.fetchCount)
        assertTrue(c.statusOf(source).lastError is AppError.Network)
    }

    @Test
    fun `background refresh is silent on failure, but success still clears the error`() = runTest {
        val source = FakeSource("weather", clock).apply { gate = CompletableDeferred() }
        val c = coordinator(source)

        val job = async { c.refresh(setOf(source.id), Trigger.BACKGROUND) }
        runCurrent()
        assertFalse(c.statusOf(source).refreshing)

        source.failWith = AppError.Network(IOException("blip"))
        source.gate!!.complete(Unit)
        assertTrue(job.await().results[source.id] is SourceResult.Failed)
        assertNull(c.statusOf(source).lastError)

        c.refresh(setOf(source.id), Trigger.USER)
        assertTrue(c.statusOf(source).lastError is AppError.Network)
        source.failWith = null
        c.refresh(setOf(source.id), Trigger.BACKGROUND)
        assertNull(c.statusOf(source).lastError)
    }

    @Test
    fun `offline and not configured background refreshes leave lastError untouched`() = runTest {
        val offlineSource = FakeSource("weather", clock)
        val offline = coordinator(offlineSource, online = false)
        val offlineReport = offline.refresh(setOf(offlineSource.id), Trigger.BACKGROUND)
        assertEquals(SourceResult.Skipped(SkipReason.OFFLINE), offlineReport.results[offlineSource.id])
        assertNull(offline.statusOf(offlineSource).lastError)

        val unsetSource = FakeSource("weather", clock, initialParam = "")
        val unset = coordinator(unsetSource)
        val unsetReport = unset.refresh(setOf(unsetSource.id), Trigger.BACKGROUND)
        assertEquals(SourceResult.Skipped(SkipReason.NOT_CONFIGURED), unsetReport.results[unsetSource.id])
        assertNull(unset.statusOf(unsetSource).lastError)
    }

    @Test
    fun `fetches run in the given fetch context, not the caller's`() = runTest {
        val source = FakeSource("weather", clock)
        val c = RefreshCoordinator(
            FakeNetworkStatus(),
            clock,
            backgroundScope,
            watchInputs = false,
            fetchContext = CoroutineName("fetch-pool"),
        ).apply { register(listOf(source)) }

        c.refresh(setOf(source.id), Trigger.USER)

        assertEquals(listOf<String?>("fetch-pool"), source.fetchedIn)
    }

    // ---- Due refreshes / 按节奏刷新 ----

    private val hourly = RefreshCadence(interval = Duration.ofMinutes(60), busyInterval = Duration.ofMinutes(5), busyInWindows = true)

    private fun fetchedMinutesAgo(minutes: Long) =
        InMemorySnapshotStore(Snapshot("A#0", clock.current.minusMinutes(minutes).toInstant().toEpochMilli(), "A", 1))

    @Test
    fun `only due sources are refreshed, and windows use the busy interval`() = runTest {
        val fresh = FakeSource("fresh", clock, store = fetchedMinutesAgo(20), cadence = hourly)
        val old = FakeSource("old", clock, store = fetchedMinutesAgo(90), cadence = hourly)
        val never = FakeSource("never", clock, cadence = hourly)
        val c = coordinator(fresh, old, never)
        val all = setOf(fresh.id, old.id, never.id)

        val report = c.refreshDue(all, Trigger.BACKGROUND, inWindow = false, throttleFailures = false)
        assertEquals(setOf(old.id, never.id), report.results.keys)
        assertEquals(0, fresh.fetchCount)

        c.refreshDue(all, Trigger.BACKGROUND, inWindow = true, throttleFailures = false)
        assertEquals("20 minutes is past the 5-minute busy interval", 1, fresh.fetchCount)
    }

    @Test
    fun `a snapshot for other inputs counts as never fetched`() = runTest {
        val source = FakeSource("weather", clock, store = fetchedMinutesAgo(1), initialParam = "B", cadence = hourly)
        val c = coordinator(source)
        c.refreshDue(setOf(source.id), Trigger.BACKGROUND, inWindow = false, throttleFailures = false)
        assertEquals(listOf("B"), source.fetchedWith)
    }

    @Test
    fun `polling waits an interval after a failure, the background worker does not`() = runTest {
        val source = FakeSource("weather", clock, cadence = hourly).apply { failWith = AppError.Network(IOException("down")) }
        val c = coordinator(source)
        val ids = setOf(source.id)

        c.refreshDue(ids, Trigger.LIVE_POLL, inWindow = false, throttleFailures = true)
        clock.current = clock.current.plusMinutes(1)
        c.refreshDue(ids, Trigger.LIVE_POLL, inWindow = false, throttleFailures = true)
        assertEquals("the failed attempt throttles polling", 1, source.fetchCount)

        c.refreshDue(ids, Trigger.BACKGROUND, inWindow = false, throttleFailures = false)
        assertEquals("a worker retry refetches", 2, source.fetchCount)

        clock.current = clock.current.plusMinutes(60)
        c.refreshDue(ids, Trigger.LIVE_POLL, inWindow = false, throttleFailures = true)
        assertEquals(3, source.fetchCount)
    }

    @Test
    fun `refreshing an unregistered source is a programming error`() = runTest {
        val c = coordinator(FakeSource("weather", clock))
        val result = runCatching { c.refresh(setOf(SourceId("test.nope")), Trigger.USER) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
