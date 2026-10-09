package io.github.lonemoonspace.dayloom.core.routine

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * One concrete occurrence of a daily window, e.g. "to work, Tuesday 07:00–09:00".
 * 某个时间窗的一次具体出现，例如「上班，周二 07:00–09:00」。
 */
data class WindowOccurrence(val kind: WindowKind, val start: ZonedDateTime, val end: ZonedDateTime) {
    operator fun contains(time: ZonedDateTime): Boolean = !time.isBefore(start) && time.isBefore(end)
}

/**
 * Decides which daily window is active and when the next one starts. A window belongs to the day it starts on, so a night
 * shift from Friday 22:00 to Saturday 06:00 counts as Friday's.
 * 判断当前处于哪个时间窗、下一个何时开始。时间窗归属于它开始的那一天，所以周五 22:00 到周六 06:00 的夜班算周五的。
 */
object RoutinePolicy {

    private const val MINUTES_PER_DAY = 24 * 60

    fun isValid(window: DailyWindow): Boolean =
        window.startMinute in 0 until MINUTES_PER_DAY &&
            window.endMinute in 0 until MINUTES_PER_DAY &&
            window.startMinute != window.endMinute

    /** The window active at [now], if any. / [now] 时刻处于的时间窗，没有则为 null。 */
    fun active(routine: Routine, now: ZonedDateTime): WindowOccurrence? =
        WindowKind.entries.firstNotNullOfOrNull { kind ->
            // Yesterday too: a window crossing midnight started then. / 也看昨天：跨午夜的时间窗是昨天开始的。
            listOf(now.toLocalDate(), now.toLocalDate().minusDays(1))
                .firstNotNullOfOrNull { day -> occurrence(routine, kind, day, now)?.takeIf { now in it } }
        }

    /**
     * The occurrence of [kind] that is under way at [now], otherwise the next one to start; null when the routine is off,
     * the window is invalid or no working day comes within a week.
     * [now] 时刻正在进行的 [kind] 时间窗，否则是下一个将开始的；通勤关闭、时间窗无效或一周内没有工作日时为 null。
     */
    fun next(routine: Routine, kind: WindowKind, now: ZonedDateTime): WindowOccurrence? {
        val today = now.toLocalDate()
        return (-1L..7L).asSequence()
            .mapNotNull { offset -> occurrence(routine, kind, today.plusDays(offset), now) }
            .firstOrNull { it.end.isAfter(now) }
    }

    private fun timeOf(minute: Int): LocalTime = LocalTime.MIDNIGHT.plusMinutes(minute.toLong())

    private fun occurrence(routine: Routine, kind: WindowKind, day: LocalDate, now: ZonedDateTime): WindowOccurrence? {
        val window = routine.window(kind)
        if (!routine.enabled || !isValid(window) || !routine.isWorkingDay(day.dayOfWeek)) return null
        // Both ends are wall-clock times, so on DST change days the window is an hour shorter or longer, like the clock.
        // 起止都是墙上时间，所以夏令时切换日的时间窗会随时钟短一小时或长一小时。
        val start = day.atTime(timeOf(window.startMinute)).atZone(now.zone)
        val endDay = if (window.endMinute > window.startMinute) day else day.plusDays(1)
        return WindowOccurrence(kind, start, endDay.atTime(timeOf(window.endMinute)).atZone(now.zone))
    }
}
