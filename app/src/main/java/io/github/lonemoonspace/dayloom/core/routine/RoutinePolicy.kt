package io.github.lonemoonspace.dayloom.core.routine

import java.time.ZonedDateTime

/** Which way the commute goes. / 通勤往哪个方向。 */
enum class CommuteDirection { TO_WORK, BACK_HOME }

/**
 * The day's rhythm, the same for everyone and every day: mornings are about getting to work, afternoons about getting home,
 * and nights are quiet. It replaced user-set commute windows after the rc.6 test, because the cards only ever needed to
 * know which way to look, and two time pickers asked more than that answer was worth.
 * 一天的节奏，对所有人、每一天都一样：上午关心去上班，下午关心回家，夜里保持安静。rc.6 测试后取代了用户自己设的通勤时间窗：
 * 卡片只需要知道看哪个方向，为此让人设两组时间不值得。
 */
object RoutinePolicy {
    /** From this hour on the cards look homewards. / 从这个钟点起卡片改看回家方向。 */
    const val SWITCH_HOUR = 12

    /** No commute alerts from this hour... / 从这个钟点起不发通勤提醒…… */
    const val QUIET_FROM_HOUR = 22

    /** ...until this one. / ……直到这个钟点。 */
    const val QUIET_UNTIL_HOUR = 6

    fun direction(now: ZonedDateTime): CommuteDirection =
        if (now.hour < SWITCH_HOUR) CommuteDirection.TO_WORK else CommuteDirection.BACK_HOME

    /**
     * Daytime: sources that change quickly refresh more often, and commute alerts may go out.
     * 白天：变化快的来源刷新得更勤，通勤提醒也可以发出。
     */
    fun isDaytime(now: ZonedDateTime): Boolean = now.hour in QUIET_UNTIL_HOUR until QUIET_FROM_HOUR
}
