package io.github.lonemoonspace.dayloom.core.refresh

import java.time.Duration
import java.time.Instant

/**
 * How often a source should be refreshed. [busyInterval] applies inside the user's daily windows (commute times) when
 * [busyInWindows] is true; [interval] applies otherwise. [background] = refresh it from the background worker at all.
 * 来源该多久刷新一次。[busyInWindows] 为 true 时，在用户的日常时间窗（通勤时段）内用 [busyInterval]，其余时间用 [interval]。
 * [background] = 后台任务是否刷新它。
 */
data class RefreshCadence(
    val interval: Duration,
    val busyInterval: Duration = interval,
    val busyInWindows: Boolean = false,
    val background: Boolean = true,
) {
    companion object {
        val DEFAULT = RefreshCadence(interval = Duration.ofMinutes(60))
    }
}

/**
 * Decides whether a source is due. The background worker fires every 15 minutes (WorkManager's minimum for periodic work);
 * this function throttles each source to its own cadence. A periodic job kept alive by the system is more robust than a
 * self-rescheduling chain, which stops forever without any sign if one link breaks.
 * 判断某个来源是否该刷新了。后台任务每 15 分钟触发一次（WorkManager 周期任务的下限），本函数把每个来源节流到它自己的节奏。
 * 由系统保活的周期任务比自我重排的任务链更稳：链断一次就永久停止，而且没有任何迹象。
 */
object RefreshCadencePolicy {

    fun isDue(cadence: RefreshCadence, inWindow: Boolean, lastFetchedAt: Instant?, now: Instant): Boolean {
        if (lastFetchedAt == null) return true
        // A timestamp in the future (clock moved back, corrupt storage) must not block refreshing forever.
        // 未来的时间戳（时钟回拨、存储损坏）不能让刷新永远卡住。
        if (lastFetchedAt.isAfter(now)) return true
        val interval = if (inWindow && cadence.busyInWindows) cadence.busyInterval else cadence.interval
        // Small tolerance so a worker firing a few seconds early does not skip a whole period.
        // 留一点余量，后台任务早几秒触发时不至于白白跳过一整个周期。
        return Duration.between(lastFetchedAt, now) >= interval.minus(TOLERANCE)
    }

    private val TOLERANCE: Duration = Duration.ofMinutes(1)
}
