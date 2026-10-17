package io.github.lonemoonspace.dayloom.core.storage

import androidx.datastore.core.DataStore
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen

/**
 * One observable, persisted value (a settings object). Interface so ViewModels and policies can be tested with an in-memory fake.
 * 一个可观察、可持久化的值（一份设置）。抽成接口，ViewModel 与 Policy 测试时用内存 fake。
 */
interface ValueStore<T> {
    /** Emits the current value immediately, then every change. / 立即发出当前值，之后每次变化都发出。 */
    val flow: Flow<T>

    suspend fun get(): T = flow.first()

    /** Atomic read-modify-write. / 原子的读-改-写。 */
    suspend fun update(transform: (T) -> T)
}

internal class DataStoreValueStore<T>(private val dataStore: DataStore<T>) : ValueStore<T> {
    override val flow: Flow<T> = dataStore.data.retryOnReadError()

    // A one-off read fails instead of waiting out a broken file; only observers wait. / 单次读取直接失败，不去等坏掉的文件；只有观察者等待。
    override suspend fun get(): T = dataStore.data.first()

    override suspend fun update(transform: (T) -> T) {
        dataStore.updateData { transform(it) }
    }
}

/**
 * Keeps a store's flow alive when reading its file fails: the read is tried again after a growing pause, and observers keep
 * the last value meanwhile. No default stands in, because defaults would look like a fresh install (the onboarding, the
 * default modules). Uncaught, the error would crash the app on every start.
 * 读文件失败时让存储的流继续活着：间隔逐渐拉长地重试，期间观察者保留上一个值。不拿默认值顶替，因为默认值看起来就像刚装好的 App
 * （首次引导、默认模块）。不接住的话，每次启动都会崩溃。
 */
internal fun <T> Flow<T>.retryOnReadError(): Flow<T> = retryWhen { cause, attempt ->
    if (cause is IOException) {
        delay(minOf(attempt + 1, MAX_READ_RETRY_DELAY_SEC) * 1_000L)
        true
    } else {
        false
    }
}

private const val MAX_READ_RETRY_DELAY_SEC = 10L
