package io.github.lonemoonspace.dayloom.core.notify

import io.github.lonemoonspace.dayloom.core.i18n.UiText
import java.time.ZonedDateTime

/**
 * One line of the morning brief from a module ("Rain from 08:00, take an umbrella"). Lines are combined in module order
 * into a single notification, so a new module joins the brief just by implementing this.
 * The brief is sent from a background round, so a line read from a source only stays fresh if the background worker
 * refreshes that source ([io.github.lonemoonspace.dayloom.core.refresh.RefreshCadence.background], true by default).
 * 模块提供的一行早间简报（「08:00 起有雨，记得带伞」）。各行按模块顺序拼成一条通知，新模块实现本接口即可加入简报。
 * 简报在后台刷新轮次里发出，所以取自某个来源的一行，只有该来源由后台任务刷新时才会是新的
 * （[io.github.lonemoonspace.dayloom.core.refresh.RefreshCadence.background]，默认为 true）。
 */
fun interface BriefContributor {
    /**
     * Null when the module has nothing worth saying today, or its data is too old to be trusted.
     * 今天没有值得说的，或数据太旧不可信时返回 null。
     */
    suspend fun line(now: ZonedDateTime): UiText?
}
