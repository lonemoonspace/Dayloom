package io.github.lonemoonspace.dayloom.core.storage

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

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
    override val flow: Flow<T> = dataStore.data

    override suspend fun update(transform: (T) -> T) {
        dataStore.updateData { transform(it) }
    }
}
