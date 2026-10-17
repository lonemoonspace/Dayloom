package io.github.lonemoonspace.dayloom.app.nav

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R
import kotlinx.coroutines.delay

/**
 * Before the settings are read. A normal start reads them in milliseconds, so the screen stays blank; when the file keeps
 * failing (the store retries on its own), the user is told after [SLOW_START_MS] instead of facing an empty screen forever,
 * with a way to the system's app info, where storage can be checked or the app restarted.
 * 设置读出来之前。正常启动几毫秒就读到，所以先保持空白；文件一直读不出来时（存储会自己重试），[SLOW_START_MS] 后告诉用户，而不是
 * 永远一片空白，并给出打开系统「应用信息」的入口，可以在那里查看存储空间或重启 App。
 */
@Composable
internal fun StartupWait() {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SLOW_START_MS)
        slow = true
    }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!slow) return@Column
        val context = LocalContext.current
        CircularProgressIndicator()
        Text(stringResource(R.string.startup_read_failed), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        OutlinedButton(onClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // Some trimmed-down systems have no app info page; the retries go on regardless. / 个别精简系统没有应用信息页；重试照常进行。
            }
        }) { Text(stringResource(R.string.startup_app_info)) }
    }
}

/** Long enough that a slow phone never sees the message on a normal start. / 足够长，慢手机正常启动时也不会看到这段提示。 */
private const val SLOW_START_MS = 4_000L
