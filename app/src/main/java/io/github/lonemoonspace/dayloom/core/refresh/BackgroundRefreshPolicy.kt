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
}
