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
            // Only an update of the same event stays silent (kick-off → final score); see [AppNotification.alertOnce].
            // 只有同一件事的更新不再响铃（开赛 → 终场）；见 [AppNotification.alertOnce]。
            .setOnlyAlertOnce(notification.alertOnce)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notification.id, built)
    }

    override fun cancel(id: Int) = NotificationManagerCompat.from(context).cancel(id)

    // Only what is shown, so a range of a thousand ids costs a few calls, not a thousand. / 只撤已显示的，千个 id 的号段也只需几次调用。
    override fun cancelShown(ids: IntRange) {
        val manager = NotificationManagerCompat.from(context)
        manager.activeNotifications.filter { it.id in ids }.forEach { manager.cancel(it.id) }
    }
}
