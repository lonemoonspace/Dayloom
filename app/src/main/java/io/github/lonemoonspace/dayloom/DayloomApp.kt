package io.github.lonemoonspace.dayloom

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import io.github.lonemoonspace.dayloom.app.AppGraph
import io.github.lonemoonspace.dayloom.app.ModuleHost
import io.github.lonemoonspace.dayloom.app.work.RefreshWorker
import io.github.lonemoonspace.dayloom.core.notify.NotificationChannels
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class DayloomApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        // Channels must exist before the first notification; creating them does not disturb the user.
        // Re-run whenever the enabled modules change so new modules get their channels.
        // 渠道必须在第一次发通知之前创建；创建渠道本身不会打扰用户。启用的模块变化时重新运行，新模块才有自己的渠道。
        graph.appScope.launch {
            graph.host.active.filterNotNull().collect { active ->
                NotificationChannels.ensure(this@DayloomApp, ModuleHost.channels(active))
            }
        }
        RefreshWorker.schedule(this)
    }

    /**
     * Channel names are shown in system settings; after a language switch they are renamed in the new language.
     * 渠道名称显示在系统设置里；切换语言后用新语言重新命名。
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val active = graph.host.active.value ?: return
        NotificationChannels.ensure(this, ModuleHost.channels(active))
    }

    companion object {
        /**
         * Not a bare cast: previews use a plain Application, and a ClassCastException would not say why.
         * 不直接强转：预览用的是普通 Application，ClassCastException 看不出原因。
         */
        fun from(context: Context): DayloomApp =
            context.applicationContext as? DayloomApp
                ?: error("applicationContext is ${context.applicationContext.javaClass.name}, not DayloomApp; inject dependencies in previews")
    }
}
