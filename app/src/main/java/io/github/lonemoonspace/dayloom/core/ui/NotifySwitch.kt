package io.github.lonemoonspace.dayloom.core.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.notify.NotificationChannels
import io.github.lonemoonspace.dayloom.core.ui.theme.statusColors

/**
 * The one switch for every kind of notification, so the permission flow exists once. The permission is requested only when
 * the user turns a switch on, never at startup: then the system dialog has an obvious reason. If it is refused the switch
 * stays off. When notifications cannot arrive anyway (permission revoked later, or this channel turned off in the system
 * settings) the switch says so and offers the way there. Both are re-checked on every resume, because the user fixes them
 * outside the app.
 * 所有通知共用的开关，权限流程只写这一处。只在用户打开开关时请求权限，从不在启动时请求：这时系统对话框的来由一目了然。
 * 被拒绝时开关保持关闭。通知本来就到不了时（之后撤销了权限，或在系统设置里关掉了这个渠道），开关会说明并给出入口。
 * 两者每次回到前台都重新检查，因为用户是在 App 之外改的。
 */
@Composable
fun NotifySwitch(label: String, summary: String, checked: Boolean, channelId: String, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasPermission(context)) }
    var channelOff by remember(channelId) { mutableStateOf(false) }
    var refused by remember { mutableStateOf(false) }
    LifecycleResumeEffect(channelId) {
        granted = hasPermission(context)
        channelOff = NotificationChannels.isChannelDisabled(context, channelId)
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        refused = !ok
        if (ok) onCheckedChange(true)
    }
    Column {
        SwitchRow(label = label, summary = summary, checked = checked, onCheckedChange = { on ->
            refused = false
            when {
                !on -> onCheckedChange(false)
                hasPermission(context) -> onCheckedChange(true)
                else -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        })
        when {
            refused || (checked && !granted) -> Problem(stringResource(R.string.notify_permission_denied)) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            checked && channelOff -> Problem(stringResource(R.string.notify_channel_off)) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            }
        }
    }
}

@Composable
private fun Problem(text: String, intent: () -> Intent) {
    val context = LocalContext.current
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.statusColors.red)
    TextButton(onClick = { context.startActivity(intent()) }) { Text(stringResource(R.string.notify_open_settings)) }
}

private fun hasPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
