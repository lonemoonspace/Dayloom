package io.github.lonemoonspace.dayloom.core.network

import kotlinx.coroutines.flow.MutableStateFlow

class FakeNetworkStatus(online: Boolean = true) : NetworkStatus {
    override val online = MutableStateFlow(online)
    override suspend fun isOnline(): Boolean = online.value
}
