package io.github.lonemoonspace.dayloom.core.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.resolve

/**
 * Posts [AppNotification]s. Tapping opens the deep link, the same path as navigating inside the app.
 * 发出 [AppNotification]。点击打开深链，与在 App 内导航走同一条路径。
 */
class NotificationSender(private val context: Context) : Notifier {

    override fun send(notification: AppNotification) {
        // Without the runtime permission, skip silently: revoking it is the user's choice, not an error.
        // 没有运行时权限时静默跳过：撤销权限是用户的选择，不是错误。
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, notification.deepLink.toUri()).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notification.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val resources = context.resources
        val body = notification.body.resolve(resources)
        val built = NotificationCompat.Builder(context, notification.channelId)
            // Must be a single-colour silhouette; the system only uses its alpha. / 必须是单色剪影，系统只取 alpha 通道。
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title.resolve(resources))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            // Updating the same id (kick-off → final score) should not ring again. / 同一 id 更新（开赛 → 终场）时不再重复响铃。
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notification.id, built)
    }
}
