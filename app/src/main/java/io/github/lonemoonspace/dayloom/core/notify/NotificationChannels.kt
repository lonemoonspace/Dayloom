package io.github.lonemoonspace.dayloom.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes

enum class ChannelImportance(val platform: Int) {
    /** Time-critical (a cancelled train). / 有时效性（列车取消）。 */
    HIGH(NotificationManager.IMPORTANCE_HIGH),
    DEFAULT(NotificationManager.IMPORTANCE_DEFAULT),
    LOW(NotificationManager.IMPORTANCE_LOW),
}

/**
 * A notification channel declared by a module, so users can turn each kind off separately in system settings.
 * [name] is local; the channel id becomes `<moduleId>.<name>`.
 * 模块声明的通知渠道，用户可以在系统设置里分别关闭每一类通知。[name] 是本地名，渠道 id 为 `<模块id>.<名称>`。
 */
data class ChannelSpec(
    val name: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val importance: ChannelImportance = ChannelImportance.DEFAULT,
)

object NotificationChannels {

    /**
     * Creates or updates the channels. Idempotent, and re-run after a language change so channel names follow the app language.
     * 创建或更新渠道。幂等；切换语言后再运行一次，渠道名称就会跟着应用语言变化。
     */
    fun ensure(context: Context, channels: Map<String, ChannelSpec>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        channels.forEach { (id, spec) ->
            manager.createNotificationChannel(
                NotificationChannel(id, context.getString(spec.title), spec.importance.platform).apply {
                    description = context.getString(spec.description)
                },
            )
        }
    }

    /**
     * Whether the user switched the channel off in system settings: the app switch may still be on, but the system drops the posts.
     * 用户是否在系统设置里关掉了这个渠道：App 内开关可能仍开着，但系统会直接丢弃通知。
     */
    fun isChannelDisabled(context: Context, channelId: String): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(channelId) ?: return false
        return channel.importance == NotificationManager.IMPORTANCE_NONE
    }
}
