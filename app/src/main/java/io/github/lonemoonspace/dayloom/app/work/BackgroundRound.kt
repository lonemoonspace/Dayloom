package io.github.lonemoonspace.dayloom.app.work

import io.github.lonemoonspace.dayloom.app.ActiveModule
import io.github.lonemoonspace.dayloom.app.ModuleHost
import io.github.lonemoonspace.dayloom.core.notify.CoreNotifications
import io.github.lonemoonspace.dayloom.core.notify.MorningBriefRule
import io.github.lonemoonspace.dayloom.core.notify.NotificationEngine
import io.github.lonemoonspace.dayloom.core.notify.RuleInput
import io.github.lonemoonspace.dayloom.core.notify.ScopedRule
import io.github.lonemoonspace.dayloom.core.refresh.BackgroundRefreshPolicy
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCoordinator
import io.github.lonemoonspace.dayloom.core.refresh.Trigger
import io.github.lonemoonspace.dayloom.core.routine.Routine
import io.github.lonemoonspace.dayloom.core.routine.RoutinePolicy
import io.github.lonemoonspace.dayloom.core.time.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull

/**
 * One background round, kept out of the Worker so it can be tested without WorkManager: refresh the due sources of the
 * enabled modules, then let every notification rule look at the result. The rules run even when nothing was due or the
 * phone is offline, because some (expiry reminders) depend only on the clock.
 * 一轮后台工作，放在 Worker 之外以便不依赖 WorkManager 测试：刷新已开启模块里到期的来源，再让所有通知规则看结果。
 * 即使没有来源到期或手机离线，规则也照样运行，因为有些规则（到期提醒）只取决于时间。
 */
class BackgroundRound(
    private val active: Flow<List<ActiveModule>?>,
    private val routine: Flow<Routine>,
    private val clock: AppClock,
    private val coordinator: RefreshCoordinator,
    private val engine: NotificationEngine,
    private val morningBrief: suspend () -> Boolean,
    private val onBriefError: (Exception) -> Unit = {},
) {
    /** Returns whether WorkManager should retry soon. / 返回是否应让 WorkManager 尽快重试。 */
    suspend fun run(): Boolean {
        val modules = active.filterNotNull().first()
        val currentRoutine = routine.first()
        val ids = modules.flatMap { it.instance.sources }.filter { it.cadence.background }.mapTo(mutableSetOf()) { it.id }
        val inWindow = RoutinePolicy.active(currentRoutine, clock.now()) != null
        val report = coordinator.refreshDue(ids, Trigger.BACKGROUND, inWindow, throttleFailures = false)
        engine.run(rules(modules, currentRoutine), RuleInput(report, clock.now()))
        return BackgroundRefreshPolicy.shouldRetry(report.results.values)
    }

    private fun rules(modules: List<ActiveModule>, currentRoutine: Routine): List<ScopedRule> {
        val brief = MorningBriefRule(
            enabled = morningBrief,
            routine = { currentRoutine },
            contributors = { modules.mapNotNull { it.instance.brief } },
            onError = onBriefError,
        )
        return ModuleHost.rules(modules) + ScopedRule(CoreNotifications.BRIEF_STATE_KEY, brief)
    }
}
