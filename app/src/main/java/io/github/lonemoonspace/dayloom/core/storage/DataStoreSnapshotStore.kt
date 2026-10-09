package io.github.lonemoonspace.dayloom.core.storage

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * [SnapshotStore] backed by one JSON file per source.
 * 每个来源一个 JSON 文件的 [SnapshotStore]。
 */
class DataStoreSnapshotStore<T>(
    valueSerializer: KSerializer<T>,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> File,
) : SnapshotStore<T> {

    // An undecodable snapshot is just a missing cache: the next successful refresh overwrites it.
    // 解不出来的快照只是缓存丢了：下次成功刷新会覆盖它。
    private val dataStore = jsonFileDataStore(
        serializer = Slot.serializer(valueSerializer),
        default = Slot(null),
        scope = scope,
        onDecodeError = {},
        produceFile = produceFile,
    )

    override val flow: Flow<Snapshot<T>?> = dataStore.data
        .map { it.snapshot }
        // A failing file read counts as "no cache" and the flow resubscribes; ending the flow would stop this source's updates for good.
        // 读文件失败按「无缓存」处理并重新订阅；让流结束的话，这个来源之后的更新就再也到不了观察者。
        .retryWhen { cause, attempt ->
            if (cause is IOException) {
                emit(null)
                delay(minOf(attempt + 1, MAX_RETRY_DELAY_SEC) * 1_000L)
                true
            } else {
                false
            }
        }
        .distinctUntilChanged()

    override suspend fun read(): Snapshot<T>? = try {
        dataStore.data.first().snapshot
    } catch (_: IOException) {
        null
    }

    override suspend fun write(snapshot: Snapshot<T>) {
        dataStore.updateData { Slot(snapshot) }
    }

    override suspend fun clear() {
        dataStore.updateData { Slot(null) }
    }

    /**
     * DataStore needs a non-null default; the wrapper expresses "no snapshot".
     * DataStore 需要非空默认值，用这层包装表达「没有快照」。
     */
    @Serializable
    internal data class Slot<T>(val snapshot: Snapshot<T>? = null)

    private companion object {
        const val MAX_RETRY_DELAY_SEC = 10L
    }
}
