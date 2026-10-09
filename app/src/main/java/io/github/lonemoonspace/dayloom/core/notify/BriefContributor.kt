package io.github.lonemoonspace.dayloom.core.notify

import io.github.lonemoonspace.dayloom.core.i18n.UiText
import java.time.ZonedDateTime

/**
 * One line of the morning brief from a module ("Rain from 08:00, take an umbrella"). Lines are combined in module order
 * into a single notification, so a new module joins the brief just by implementing this.
 * 模块提供的一行早间简报（「08:00 起有雨，记得带伞」）。各行按模块顺序拼成一条通知，新模块实现本接口即可加入简报。
 */
fun interface BriefContributor {
    /** Null when the module has nothing worth saying today. / 今天没有值得说的就返回 null。 */
    suspend fun line(now: ZonedDateTime): UiText?
}
