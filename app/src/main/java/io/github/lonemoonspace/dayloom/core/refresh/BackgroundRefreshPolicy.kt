package io.github.lonemoonspace.dayloom.core.refresh

/**
 * Retry decision for the background refresh worker.
 * 后台刷新任务的重试判定。
 */
object BackgroundRefreshPolicy {

    /**
     * Retry soon only when every source that was actually attempted failed. Skipped sources (offline, not configured)
     * are left out: a source that cannot succeed (no key entered) would otherwise make every round retry for nothing.
     * 只有实际尝试过的来源全部失败时才尽快重试。跳过的来源（离线、未配置）不计入：本来就不可能成功的来源（没填 Key）
     * 否则会让每一轮都白白重试。
     */
    fun shouldRetry(results: Collection<SourceResult>): Boolean {
        val attempted = results.filterNot { it is SourceResult.Skipped }
        return attempted.isNotEmpty() && attempted.all { it is SourceResult.Failed }
    }

    /**
     * Whether a module's sources are worth refreshing while the app is closed: only when something will use the result
     * without the user opening the app, i.e. one of its notification rules is on, or the morning brief is about to quote
     * it. Everything else is refreshed when the app is opened, which costs no battery or data in between.
     * 应用关着的时候，一个模块的来源值不值得刷新：只有结果会在用户不打开 App 的情况下被用到时才值得，也就是它有通知规则开着，
     * 或者早间简报马上要引用它。其余的等打开 App 时再刷新，中间不耗电也不耗流量。
     */
    fun wanted(anyRuleEnabled: Boolean, hasBriefLine: Boolean, briefNeedsData: Boolean): Boolean =
        anyRuleEnabled || (hasBriefLine && briefNeedsData)
}
