package io.github.lonemoonspace.dayloom.core.network

import kotlinx.coroutines.flow.MutableStateFlow

class FakeNetworkStatus(online: Boolean = true, private val metered: Boolean = false) : NetworkStatus {
    override val online = MutableStateFlow(online)
    override suspend fun isOnline(): Boolean = online.value
    override suspend fun isMetered(): Boolean = metered
}
