package io.github.lonemoonspace.dayloom.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether a usable network exists. Only used to fail fast on automatic refreshes: ConnectivityManager misjudges
 * (VPNs, captive portals, some ROMs), so a refresh the user triggers always goes out regardless.
 * 当前是否有可用网络。只用于让自动刷新快速失败：ConnectivityManager 会误判（VPN、强制门户、部分 ROM），
 * 所以用户主动触发的刷新无论如何都会真正发出请求。
 */
interface NetworkStatus {
    val online: Flow<Boolean>

    suspend fun isOnline(): Boolean

    /**
     * Whether the current network is metered. When unsure, answer "metered": wrongly saving data is cheap, wrongly
     * spending the user's mobile data is not. (Opposite direction to [isOnline] on purpose.)
     * 当前网络是否计费。拿不准时按「计费」：误省流量代价小，误用用户的移动数据代价大。（与 [isOnline] 的方向刻意相反。）
     */
    suspend fun isMetered(): Boolean
}

class ConnectivityMonitor(context: Context) : NetworkStatus {

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    // INTERNET states intent; VALIDATED means it actually reached the internet.
    // INTERNET 只表示意图；VALIDATED 才表示确实连通了公网。
    private val networkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        .build()

    override val online: Flow<Boolean> = callbackFlow {
        val cm = connectivityManager
        if (cm == null) {
            // Cannot tell: treat as online so the app never refuses to work. / 无法判断：按在线处理，绝不拒绝工作。
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentlyOnline(cm))
            }

            override fun onLost(network: Network) {
                trySend(currentlyOnline(cm))
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(currentlyOnline(cm))
            }

            override fun onUnavailable() {
                trySend(currentlyOnline(cm))
            }
        }
        // Emit the current state first so the UI does not start with a wrong default. / 先发当前状态，界面首帧才不会是错误的默认值。
        trySend(currentlyOnline(cm))
        cm.registerNetworkCallback(networkRequest, callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    override suspend fun isOnline(): Boolean {
        val cm = connectivityManager ?: return true
        return currentlyOnline(cm)
    }

    override suspend fun isMetered(): Boolean {
        val cm = connectivityManager ?: return true
        val network = cm.activeNetwork ?: return true
        val capabilities = cm.getNetworkCapabilities(network) ?: return true
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun currentlyOnline(cm: ConnectivityManager): Boolean {
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
