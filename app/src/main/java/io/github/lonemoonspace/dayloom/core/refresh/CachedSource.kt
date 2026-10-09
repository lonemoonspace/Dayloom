package io.github.lonemoonspace.dayloom.core.refresh

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.storage.SnapshotStore
import io.github.lonemoonspace.dayloom.core.time.AppClock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * A cached data source: fetch → write its own [SnapshotStore] → let the UI observe the snapshot matching the current inputs.
 * It only knows how to fetch, store and match; concurrency, offline skipping and error state live in [RefreshCoordinator].
 * [P] is the source's own parameter type, built by its module from module settings; core never sees module settings.
 * 带缓存的数据来源：抓取 → 写入自己的 [SnapshotStore] → 让界面观察与当前输入匹配的快照。
 * 它只负责怎么抓、怎么存、什么算匹配；并发、离线跳过与错误状态都在 [RefreshCoordinator]。
 * [P] 是来源自己的参数类型，由所属模块从模块设置里算出；core 永远看不到模块设置。
 */
abstract class CachedSource<P, T>(
    val id: SourceId,
    private val store: SnapshotStore<T>,
    protected val clock: AppClock,
) {
    /** Bump on incompatible cache changes; older snapshots are then ignored. / 缓存结构有不兼容变化时加一；旧快照随即被忽略。 */
    abstract val schemaVersion: Int

    /** Older than this, the UI marks the data as cached. / 超过这个时长，界面标注「缓存」。 */
    abstract val maxAge: Duration

    open val cadence: RefreshCadence = RefreshCadence.DEFAULT

    /**
     * Everything the fetch depends on. Must emit the current value immediately (DataStore flows do), because the coordinator
     * reads it one-shot before each refresh.
     * 抓取依赖的全部输入。必须立即发出当前值（DataStore 的流就是这样），因为协调器每次刷新前一次性读取它。
     */
    abstract val inputs: Flow<SourceInput<P>>

    /**
     * The network fetch; failures throw [AppError]. [previous] is the last snapshot for the same parameters and schema, if any.
     * 真正的网络抓取，失败抛 [AppError]。[previous] 是同参数、同 schema 的上一份快照（可能没有）。
     */
    protected abstract suspend fun fetch(params: P, now: ZonedDateTime, previous: Snapshot<T>?): T

    /** The moment the data describes; defaults to the fetch time. / 数据所描述的时刻；默认为抓取时刻。 */
    open fun dataTime(snapshot: Snapshot<T>): Instant = Instant.ofEpochMilli(snapshot.fetchedAt)

    /**
     * The snapshot matching the current inputs and schema, re-evaluated as soon as the inputs change.
     * 与当前输入及 schema 匹配的快照；输入一变立即重新判定。
     */
    fun observe(): Flow<Snapshot<T>?> =
        combine(store.flow, inputs.map { (it as? SourceInput.Ready)?.key }.distinctUntilChanged()) { snapshot, key ->
            snapshot?.takeIf { key != null && matches(it, key) }
        }.distinctUntilChanged()

    /** One-shot [observe], for workers and notification rules after a refresh. / [observe] 的一次性版本，供刷新后的 Worker 与通知规则使用。 */
    suspend fun current(): Snapshot<T>? {
        val key = (readInput() as? SourceInput.Ready)?.key ?: return null
        return store.read()?.takeIf { matches(it, key) }
    }

    fun isStale(snapshot: Snapshot<T>, now: Instant): Boolean = Duration.between(dataTime(snapshot), now) > maxAge

    internal suspend fun readInput(): SourceInput<P> = inputs.first()

    internal suspend fun refresh(input: SourceInput.Ready<P>): Snapshot<T> {
        val now = clock.now()
        val previous = store.read()?.takeIf { matches(it, input.key) }
        val snapshot = Snapshot(
            value = fetch(input.params, now, previous),
            fetchedAt = now.toInstant().toEpochMilli(),
            paramsKey = input.key,
            schema = schemaVersion,
        )
        store.write(snapshot)
        return snapshot
    }

    private fun matches(snapshot: Snapshot<T>, key: String): Boolean =
        snapshot.schema == schemaVersion && snapshot.paramsKey == key
}
