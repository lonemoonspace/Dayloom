package io.github.lonemoonspace.dayloom.core.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class InMemoryValueStore<T>(initial: T) : ValueStore<T> {
    val state = MutableStateFlow(initial)

    override val flow: StateFlow<T> = state

    override suspend fun update(transform: (T) -> T) {
        state.update(transform)
    }
}
