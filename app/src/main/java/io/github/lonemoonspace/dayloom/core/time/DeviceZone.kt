package io.github.lonemoonspace.dayloom.core.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import java.time.ZoneId
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The device's current time zone, updated when the user travels or changes it in system settings.
 * 设备当前时区；用户出行或在系统设置里修改时随之更新。
 */
fun deviceZoneFlow(context: Context): Flow<ZoneId> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            trySend(ZoneId.systemDefault())
        }
    }
    trySend(ZoneId.systemDefault())
    ContextCompat.registerReceiver(
        context.applicationContext,
        receiver,
        IntentFilter(Intent.ACTION_TIMEZONE_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    awaitClose { context.applicationContext.unregisterReceiver(receiver) }
}.distinctUntilChanged()

/**
 * The device zone right now, used before [deviceZoneFlow] has emitted.
 * 此刻的设备时区，在 [deviceZoneFlow] 发出第一个值之前使用。
 */
fun currentDeviceZone(): ZoneId = ZoneId.systemDefault()
