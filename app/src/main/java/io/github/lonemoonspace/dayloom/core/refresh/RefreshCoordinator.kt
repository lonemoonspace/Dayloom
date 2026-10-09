package io.github.lonemoonspace.dayloom.core.refresh

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.error.toAppError
import io.github.lonemoonspace.dayloom.core.network.NetworkStatus
import io.github.lonemoonspace.dayloom.core.time.AppClock
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The single refresh entry point for the UI and background work. Semantics (each covered by RefreshCoordinatorTest):
 * 1. Sources refresh in parallel; one failure does not affect the others.
 * 2. Per-source single-flight: same params key → share the in-flight result; different key → wait for it, re-read the
 *    inputs, then share or refetch (so a caller holding old inputs cannot overwrite a newer snapshot).
 * 3. Non-USER triggers while offline → Skipped(OFFLINE), snapshot untouched (a source that is not set up reports NOT_CONFIGURED instead).
 * 4. Inputs are read one-shot at the start of each refresh.
 * 5. With [watchInputs], a change of a source's refreshKey triggers an INPUTS_CHANGED refresh of that source only.
 * 6. Success clears lastError; failure keeps the old snapshot and records lastError.
 * BACKGROUND and LIVE_POLL are silent: no refreshing flag, no lastError on failure (success still clears it).
 * Sources are registered when their module is created, so the set of sources may grow at runtime.
 *
 * 前台与后台共用的刷新入口。语义（每条都有 RefreshCoordinatorTest 覆盖）：
 * 1. 各来源并行刷新，一个失败不影响其他。
 * 2. 按来源 single-flight：参数 key 相同 → 共享在途结果；不同 → 等它结束、重新读取输入，再决定共享还是重抓
 *    （持有旧输入的调用方不能覆盖更新的快照）。
 * 3. 非 USER 触发且离线 → Skipped(OFFLINE)，快照不动（未设置好的来源改报 NOT_CONFIGURED）。
 * 4. 每次刷新开始时一次性读取输入。
 * 5. [watchInputs] 打开时，某个来源的 refreshKey 变化只触发该来源的 INPUTS_CHANGED 刷新。
 * 6. 成功清除 lastError；失败保留旧快照并记录 lastError。
 * BACKGROUND 与 LIVE_POLL 是静默的：不置 refreshing，失败不写 lastError（成功照样清除）。
 * 来源在所属模块创建时注册，所以来源集合可能在运行时增加。
 *
 * In-flight fetches run in [appScope], not the caller's scope: closing a screen does not cancel a request that others share.
 * 在途抓取跑在 [appScope] 而不是调用方作用域：关闭页面不会取消别人也在等的请求。
 */
class RefreshCoordinator(
    private val connectivity: NetworkStatus,
    private val clock: AppClock,
    private val appScope: CoroutineScope,
    watchInputs: Boolean,
    /**
     * Called when watching fails (logged in production); [SourceId] is null for a failure outside any single source.
     * 监听出错时调用（生产环境记日志）；不属于某个来源的失败 [SourceId] 为 null。
     */
    private val onWatchError: (SourceId?, Throwable) -> Unit = { _, _ -> },
    /**
     * Where fetches run; production passes Dispatchers.IO, because fetches make blocking network calls (OkHttp `execute()`)
     * that must not occupy the CPU-bound Default pool. Tests keep the default so virtual time still applies.
     * 抓取在哪里运行；生产环境传 Dispatchers.IO，因为抓取里是阻塞的网络调用（OkHttp `execute()`），不能占用面向 CPU 的
     * Default 线程池。测试保留默认值，虚拟时间才仍然有效。
     */
    private val fetchContext: CoroutineContext = EmptyCoroutineContext,
) {
    private val registered = MutableStateFlow<Map<SourceId, CachedSource<*, *>>>(emptyMap())
    private val _status = MutableStateFlow<Map<SourceId, SourceStatus>>(emptyMap())

    /** Refreshing / last error per source, including refreshes started in the background. / 每个来源的刷新中与上次错误，含后台发起的刷新。 */
    val status: StateFlow<Map<SourceId, SourceStatus>> = _status.asStateFlow()

    private val lock = Mutex()
    private val inFlight = mutableMapOf<SourceId, Flight>()

    /** [visible] becomes true once a visible trigger joins a silent flight. Guarded by [lock]. / 有可见触发加入静默的在途刷新后改为 true。由 [lock] 保护。 */
    private class Flight(val paramsKey: String, var visible: Boolean) {
        lateinit var result: Deferred<SourceResult>
    }

    /** Skipped carries no error, but NotConfigured is still shown as lastError. / Skipped 不带错误，但 NotConfigured 仍作为 lastError 展示。 */
    private class Outcome(val result: SourceResult, val error: AppError?)

    init {
        if (watchInputs) appScope.launch { watchInputChanges() }
    }

    val sourceIds: Set<SourceId> get() = registered.value.keys

    fun register(sources: Collection<CachedSource<*, *>>) {
        registered.update { current ->
            sources.forEach { source ->
                val existing = current[source.id]
                require(existing == null || existing === source) { "source ${source.id} registered twice" }
            }
            current + sources.associateBy { it.id }
        }
        _status.update { all -> all + sources.filter { it.id !in all }.associate { it.id to SourceStatus() } }
    }

    suspend fun refresh(ids: Set<SourceId>, trigger: Trigger): RefreshReport {
        val sources = registered.value
        require(sources.keys.containsAll(ids)) { "unregistered sources: ${ids - sources.keys}" }
        if (ids.isEmpty()) return RefreshReport(emptyMap(), clock.instant())

        // One-shot query instead of the first frame of `online`, which may not have arrived on a cold start.
        // 一次性查询，不看 `online` 的首帧（冷启动时它未必已经到达）。
        val offline = trigger != Trigger.USER && !connectivity.isOnline()
        val results = coroutineScope {
            ids.map { id ->
                async {
                    val source = sources.getValue(id)
                    id to if (offline) skipOffline(source, trigger.visible) else runSource(source, trigger)
                }
            }.awaitAll().toMap()
        }
        return RefreshReport(results, clock.instant())
    }

    /**
     * Offline: a source that is not set up still reports what is missing, because "you are offline" would hide the
     * fact that the user has something to configure. Reading inputs is local and cheap.
     * 离线时，未设置好的来源仍然报告缺什么：只显示「当前离线」会掩盖用户还需要去设置的事实。读取输入是本地操作，代价很小。
     */
    private suspend fun <P> skipOffline(source: CachedSource<P, *>, visible: Boolean): SourceResult {
        val input = when (val read = readInput(source, visible)) {
            is InputRead.Ok -> read.input
            is InputRead.Failed -> return SourceResult.Failed(read.error)
        }
        if (input is SourceInput.Missing) return notConfigured(source.id, AppError.NotConfigured(input.what), visible)
        if (visible) updateStatus(source.id) { it.copy(lastError = AppError.Offline()) }
        return SourceResult.Skipped(SkipReason.OFFLINE)
    }

    private suspend fun <P> runSource(source: CachedSource<P, *>, trigger: Trigger): SourceResult {
        val visible = trigger.visible
        var input = when (val read = readInput(source, visible)) {
            is InputRead.Ok -> read.input
            is InputRead.Failed -> return SourceResult.Failed(read.error)
        }
        while (true) {
            val ready = when (input) {
                is SourceInput.Missing -> return notConfigured(source.id, AppError.NotConfigured(input.what), visible)
                is SourceInput.Ready -> input
            }
            val flight = lock.withLock {
                val existing = inFlight[source.id]
                when {
                    existing == null -> startFlight(source, ready, visible)
                    existing.paramsKey == ready.key -> existing.also { joinVisibly(source.id, it, visible) }
                    else -> existing
                }
            }
            if (flight.paramsKey == ready.key) return awaitFlight(flight)

            // A flight with other params is running: wait for it, then re-read, because our inputs may be the older ones.
            // 在途的是另一组参数：等它结束再重新读取，因为我们手里的输入可能才是旧的。
            flight.result.join()
            input = when (val read = readInput(source, visible)) {
                is InputRead.Ok -> read.input
                is InputRead.Failed -> return SourceResult.Failed(read.error)
            }
            if (input is SourceInput.Ready && input.key == flight.paramsKey && !flight.result.isCancelled) {
                return awaitFlight(flight)
            }
        }
    }

    private sealed interface InputRead<out P> {
        data class Ok<P>(val input: SourceInput<P>) : InputRead<P>
        data class Failed(val error: AppError) : InputRead<Nothing>
    }

    /** Reading the inputs can fail (e.g. a settings file I/O error); that fails only this source. / 读取输入可能失败（如设置文件读写出错），只算这一个来源失败。 */
    private suspend fun <P> readInput(source: CachedSource<P, *>, visible: Boolean): InputRead<P> = try {
        InputRead.Ok(source.readInput())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val error = e.toAppError()
        if (visible) updateStatus(source.id) { it.copy(lastError = error) }
        InputRead.Failed(error)
    }

    /**
     * A cancelled in-flight fetch (it runs in appScope, so only a cancellation thrown inside the fetch) fails only this
     * source; rethrowing would cancel the other sources of the same round through awaitAll. The caller's own cancellation still propagates.
     * 在途抓取被取消（它在 appScope 里，只可能是抓取内部抛出的取消）时只算这一个来源失败；直接往上抛会经 awaitAll
     * 连带取消同一轮的其他来源。调用方自己被取消时照常抛出。
     */
    private suspend fun awaitFlight(flight: Flight): SourceResult = try {
        flight.result.await()
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        SourceResult.Failed(AppError.Unexpected(e))
    }

    /** Caller holds [lock]. / 调用方需持有 [lock]。 */
    private fun <P> startFlight(source: CachedSource<P, *>, input: SourceInput.Ready<P>, visible: Boolean): Flight {
        val flight = Flight(input.key, visible)
        flight.result = appScope.async(fetchContext, start = CoroutineStart.LAZY) { execute(source, input, flight) }
        inFlight[source.id] = flight
        if (visible) updateStatus(source.id) { it.copy(refreshing = true) }
        flight.result.start()
        return flight
    }

    private fun joinVisibly(id: SourceId, flight: Flight, visible: Boolean) {
        if (visible && !flight.visible) {
            flight.visible = true
            if (inFlight[id] === flight) updateStatus(id) { it.copy(refreshing = true) }
        }
    }

    private suspend fun <P> execute(source: CachedSource<P, *>, input: SourceInput.Ready<P>, flight: Flight): SourceResult {
        val outcome = try {
            val snapshot = source.refresh(input)
            Outcome(SourceResult.Success(Instant.ofEpochMilli(snapshot.fetchedAt)), null)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { finish(source.id, flight, null) }
            throw e
        } catch (e: Exception) {
            val error = e.toAppError()
            if (error is AppError.NotConfigured) {
                Outcome(SourceResult.Skipped(SkipReason.NOT_CONFIGURED), error)
            } else {
                Outcome(SourceResult.Failed(error), error)
            }
        }
        finish(source.id, flight, outcome)
        return outcome.result
    }

    private suspend fun finish(id: SourceId, flight: Flight, outcome: Outcome?) {
        lock.withLock {
            if (inFlight[id] === flight) inFlight.remove(id)
            updateStatus(id) {
                when {
                    outcome == null -> it.copy(refreshing = false)
                    outcome.error == null -> it.copy(refreshing = false, lastError = null)
                    flight.visible -> it.copy(refreshing = false, lastError = outcome.error)
                    else -> it.copy(refreshing = false)
                }
            }
        }
    }

    private fun notConfigured(id: SourceId, error: AppError.NotConfigured, visible: Boolean): SourceResult {
        if (visible) updateStatus(id) { it.copy(lastError = error) }
        return SourceResult.Skipped(SkipReason.NOT_CONFIGURED)
    }

    private sealed interface Watched {
        data class Value(val refreshKey: Any?) : Watched
        data object Unavailable : Watched
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun watchInputChanges() {
        // Baseline per source: the first value a source emits never triggers a refresh (the first load is the caller's job).
        // 每个来源的基线：来源发出的第一个值不触发刷新（首次加载由调用方负责）。
        val baseline = mutableMapOf<SourceId, Any?>()
        try {
            registered
                .flatMapLatest { sources ->
                    if (sources.isEmpty()) flowOf(emptyMap()) else combine(sources.values.map(::watch)) { it.toMap() }
                }
                .collect { current ->
                    val targets = mutableSetOf<SourceId>()
                    for ((id, watched) in current) {
                        // A failing input keeps the previous baseline, so a read error never looks like a change.
                        // 读取失败的来源保留原基线，读错不会被当成变化。
                        val key = (watched as? Watched.Value ?: continue).refreshKey
                        if (id !in baseline) {
                            baseline[id] = key
                        } else if (baseline[id] != key) {
                            baseline[id] = key
                            targets += id
                        }
                    }
                    if (targets.isNotEmpty()) appScope.launch { refreshOnInputsChange(targets) }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Must not escape appScope and crash the process. / 不能逃出 appScope 把进程带崩。
            onWatchError(null, e)
        }
    }

    /**
     * A failing inputs flow emits [Watched.Unavailable] and resubscribes after a backoff instead of ending: `combine` needs
     * every source to keep emitting, otherwise one broken file would stop watching all sources.
     * 输入流失败时发出 [Watched.Unavailable] 并在退避后重新订阅，而不是结束：`combine` 需要每个来源持续发射，
     * 否则一个坏文件会让所有来源的监听都停掉。
     */
    private fun watch(source: CachedSource<*, *>): Flow<Pair<SourceId, Watched>> =
        source.inputs
            .map<SourceInput<*>, Watched> { Watched.Value((it as? SourceInput.Ready<*>)?.refreshKey) }
            .retryWhen { cause, attempt ->
                if (cause is CancellationException) return@retryWhen false
                onWatchError(source.id, cause)
                emit(Watched.Unavailable)
                delay(minOf(attempt + 1, MAX_WATCH_RETRY_DELAY_SEC) * 1_000L)
                true
            }
            .map { source.id to it }

    /** Runs in appScope with nobody to catch; a failure is dropped until the next change. / 跑在 appScope、没有调用方接异常；失败就放弃本轮，等下次变化。 */
    private suspend fun refreshOnInputsChange(targets: Set<SourceId>) {
        try {
            refresh(targets, Trigger.INPUTS_CHANGED)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private fun updateStatus(id: SourceId, transform: (SourceStatus) -> SourceStatus) {
        _status.update { all -> all + (id to transform(all[id] ?: SourceStatus())) }
    }

    private companion object {
        const val MAX_WATCH_RETRY_DELAY_SEC = 10L
    }
}
