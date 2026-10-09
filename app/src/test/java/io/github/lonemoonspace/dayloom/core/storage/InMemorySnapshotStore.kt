package io.github.lonemoonspace.dayloom.core.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemorySnapshotStore<T>(initial: Snapshot<T>? = null) : SnapshotStore<T> {
    private val state = MutableStateFlow(initial)

    var writes = 0
        private set

    val current: Snapshot<T>? get() = state.value

    override val flow: Flow<Snapshot<T>?> = state

    override suspend fun read(): Snapshot<T>? = state.value

    override suspend fun write(snapshot: Snapshot<T>) {
        writes++
        state.value = snapshot
    }

    override suspend fun clear() {
        state.value = null
    }
}
