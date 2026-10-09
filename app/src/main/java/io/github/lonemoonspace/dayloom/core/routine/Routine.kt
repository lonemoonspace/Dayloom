package io.github.lonemoonspace.dayloom.core.routine

import kotlinx.serialization.Serializable

/**
 * The user's daily rhythm shared by every module: when they leave for work and when they head home. The windows apply every
 * day, weekends included, so the cards always look the same. Weather, transport, traffic and the morning brief all read
 * this instead of asking separately.
 * 所有模块共用的日常节奏：几点出门上班、几点回家。时间窗每天都生效（含周末），卡片每天的样子都一样。天气、公交、路况与早间简报
 * 都读这里，不再各问一遍。
 */
@Serializable
data class Routine(
    val toWork: DailyWindow = DailyWindow(startMinute = 7 * 60, endMinute = 9 * 60),
    val backHome: DailyWindow = DailyWindow(startMinute = 15 * 60 + 30, endMinute = 17 * 60 + 30),
) {
    fun window(kind: WindowKind): DailyWindow = when (kind) {
        WindowKind.TO_WORK -> toWork
        WindowKind.BACK_HOME -> backHome
    }
}

/**
 * A daily time window in minutes since midnight. [endMinute] < [startMinute] means it crosses midnight (night shifts); equal
 * values are an empty, invalid window.
 * 以「午夜起的分钟数」表示的每日时间窗。[endMinute] < [startMinute] 表示跨午夜（夜班）；两者相等是空的、无效的时间窗。
 */
@Serializable
data class DailyWindow(val startMinute: Int = 0, val endMinute: Int = 0)

enum class WindowKind { TO_WORK, BACK_HOME }
