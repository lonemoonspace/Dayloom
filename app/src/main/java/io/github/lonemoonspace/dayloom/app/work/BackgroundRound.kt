package io.github.lonemoonspace.dayloom.app.work

import io.github.lonemoonspace.dayloom.app.ActiveModule
import io.github.lonemoonspace.dayloom.app.ModuleHost
import io.github.lonemoonspace.dayloom.core.network.NetworkStatus
import io.github.lonemoonspace.dayloom.core.notify.CoreNotifications
import io.github.lonemoonspace.dayloom.core.notify.MorningBriefPolicy
import io.github.lonemoonspace.dayloom.core.notify.MorningBriefRule
import io.github.lonemoonspace.dayloom.core.notify.NotificationEngine
import io.github.lonemoonspace.dayloom.core.notify.RuleInput
import io.github.lonemoonspace.dayloom.core.notify.ScopedRule
import io.github.lonemoonspace.dayloom.core.refresh.BackgroundRefreshPolicy
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCoordinator
import io.github.lonemoonspace.dayloom.core.refresh.Trigger
import io.github.lonemoonspace.dayloom.core.routine.RoutinePolicy
import io.github.lonemoonspace.dayloom.core.time.AppClock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * One background round, kept out of the Worker so it can be tested without WorkManager: refresh the due sources that
 * something will use while the app is closed ([BackgroundRefreshPolicy.wanted]), then let every notification rule look at
 * the result. The rules run even when nothing was due or the phone is offline, because some (expiry reminders) depend only
 * on the clock.
 * 一轮后台工作，放在 Worker 之外以便不依赖 WorkManager 测试：刷新到期的、且在 App 关着时会被用到的来源
 * （[BackgroundRefreshPolicy.wanted]），再让所有通知规则看结果。即使没有来源到期或手机离线，规则也照样运行，因为有些规则
 * （到期提醒）只取决于时间。
 */
class BackgroundRound(
    private val active: Flow<List<ActiveModule>?>,
    private val clock: AppClock,
    private val coordinator: RefreshCoordinator,
    private val engine: NotificationEngine,
    private val network: NetworkStatus,
    private val morningBrief: suspend () -> Boolean,
    private val briefMinute: suspend () -> Int,
    /** The day the last brief went out, from the brief rule's saved state. / 上一条简报是哪天发的，取自简报规则保存的状态。 */
    private val briefLastSent: suspend () -> LocalDate?,
    private val onBriefError: (Exception) -> Unit = {},
) {
    /** Returns whether WorkManager should retry soon. / 返回是否应让 WorkManager 尽快重试。 */
    suspend fun run(): Boolean {
        val modules = active.filterNotNull().first()
        val now = clock.now()
        val briefNeedsData = morningBrief() && MorningBriefPolicy.needsData(briefMinute(), now, briefLastSent())
        val metered = network.isMetered()
        val ids = modules
            .filter { BackgroundRefreshPolicy.wanted(anyRuleEnabled(it), it.instance.brief != null, briefNeedsData) }
            .flatMap { it.instance.sources }
            .filter { BackgroundRefreshPolicy.allowed(it.cadence, metered) }
            .mapTo(mutableSetOf()) { it.id }
        val report = coordinator.refreshDue(ids, Trigger.BACKGROUND, RoutinePolicy.isDaytime(now), throttleFailures = false)
        engine.run(rules(modules), RuleInput(report, clock.now()))
        return BackgroundRefreshPolicy.shouldRetry(report.results.values)
    }

    /** A rule that cannot say whether it is on counts as on: refreshing once too often is the smaller mistake. / 说不清自己开没开的规则按「开」算：多刷新一次是较小的错。 */
    private suspend fun anyRuleEnabled(module: ActiveModule): Boolean = module.instance.notificationRules.any { rule ->
        try {
            rule.isEnabled()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            true
        }
    }

    private fun rules(modules: List<ActiveModule>): List<ScopedRule> {
        val brief = MorningBriefRule(
            enabled = morningBrief,
            briefMinute = briefMinute,
            contributors = { modules.mapNotNull { it.instance.brief } },
            onError = onBriefError,
        )
        return ModuleHost.rules(modules) + ScopedRule(CoreNotifications.BRIEF_STATE_KEY, brief)
    }
}
