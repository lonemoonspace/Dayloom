package io.github.lonemoonspace.dayloom.core.routine

import java.time.DayOfWeek
import kotlinx.serialization.Serializable

/**
 * The user's daily rhythm shared by every module: when they leave for work and when they head home, on which days.
 * Weather, transport, traffic and the morning brief all read this instead of asking separately.
 * 所有模块共用的日常节奏：哪几天、几点出门上班、几点回家。天气、公交、路况与早间简报都读这里，不再各问一遍。
 */
@Serializable
data class Routine(
    /** False for people without a commute; windows are then ignored. / 不通勤的人设为 false，此时忽略时间窗。 */
    val enabled: Boolean = true,
    val toWork: DailyWindow = DailyWindow(startMinute = 7 * 60, endMinute = 9 * 60),
    val backHome: DailyWindow = DailyWindow(startMinute = 15 * 60 + 30, endMinute = 17 * 60 + 30),
    /** ISO day numbers, Monday = 1. / ISO 星期编号，周一 = 1。 */
    val workingDays: Set<Int> = (1..5).toSet(),
) {
    fun window(kind: WindowKind): DailyWindow = when (kind) {
        WindowKind.TO_WORK -> toWork
        WindowKind.BACK_HOME -> backHome
    }

    fun isWorkingDay(day: DayOfWeek): Boolean = day.value in workingDays
}

/**
 * A daily time window in minutes since midnight. [endMinute] < [startMinute] means it crosses midnight (night shifts); equal
 * values are an empty, invalid window.
 * 以「午夜起的分钟数」表示的每日时间窗。[endMinute] < [startMinute] 表示跨午夜（夜班）；两者相等是空的、无效的时间窗。
 */
@Serializable
data class DailyWindow(val startMinute: Int = 0, val endMinute: Int = 0)

enum class WindowKind { TO_WORK, BACK_HOME }
