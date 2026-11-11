package io.github.lonemoonspace.dayloom.core.routine

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Which way the commute goes. / 通勤往哪个方向。 */
enum class CommuteDirection { TO_WORK, BACK_HOME }

/**
 * The day's rhythm, the same for everyone and every day: mornings are about getting to work, afternoons about getting home
 * (a card can be turned round by hand for the rest of that half-day), and nights are quiet. It replaced user-set commute
 * windows after the rc.6 test, because the cards only ever needed to know which way to look, and two time pickers asked
 * more than that answer was worth.
 * 一天的节奏，对所有人、每一天都一样：上午关心去上班，下午关心回家（卡片可以手动掉头，保持到这半天结束），夜里保持安静。
 * rc.6 测试后取代了用户自己设的通勤时间窗：卡片只需要知道看哪个方向，为此让人设两组时间不值得。
 */
object RoutinePolicy {
    /** From this hour on the cards look homewards. / 从这个钟点起卡片改看回家方向。 */
    const val SWITCH_HOUR = 12

    /** No commute alerts from this hour... / 从这个钟点起不发通勤提醒…… */
    const val QUIET_FROM_HOUR = 22

    /** ...until this one. / ……直到这个钟点。 */
    const val QUIET_UNTIL_HOUR = 6

    /**
     * [flippedAtMillis] is when the user last turned a card round by hand (0: never); see [isFlipped].
     * [flippedAtMillis] 是用户上次手动把卡片掉头的时刻（0 表示没有）；见 [isFlipped]。
     */
    fun direction(now: ZonedDateTime, flippedAtMillis: Long = 0): CommuteDirection {
        val byClock = now.hour < SWITCH_HOUR
        return if (byClock != isFlipped(now, flippedAtMillis)) CommuteDirection.TO_WORK else CommuteDirection.BACK_HOME
    }

    /**
     * A manual flip lasts until the clock next turns the direction itself (noon or midnight), so a weekend trip the other
     * way never leaves the card backwards the next morning. A flip from the future (the clock was set back) is ignored.
     * 手动掉头只保持到时钟下一次自己翻转方向（中午或午夜），周末反方向出一趟门不会让卡片第二天早上还反着。来自未来的记录
     * （时钟被调回）不算。
     */
    fun isFlipped(now: ZonedDateTime, flippedAtMillis: Long): Boolean {
        if (flippedAtMillis <= 0) return false
        val halfDayStart = now.truncatedTo(ChronoUnit.DAYS).withHour(if (now.hour < SWITCH_HOUR) 0 else SWITCH_HOUR)
        return flippedAtMillis in halfDayStart.toInstant().toEpochMilli()..now.toInstant().toEpochMilli()
    }

    /** The value to save when the user taps the flip button. / 用户点掉头按钮后该保存的值。 */
    fun toggleFlip(now: ZonedDateTime, flippedAtMillis: Long): Long =
        if (isFlipped(now, flippedAtMillis)) 0 else now.toInstant().toEpochMilli()

    /**
     * Daytime: sources that change quickly refresh more often, and commute alerts may go out.
     * 白天：变化快的来源刷新得更勤，通勤提醒也可以发出。
     */
    fun isDaytime(now: ZonedDateTime): Boolean = now.hour in QUIET_UNTIL_HOUR until QUIET_FROM_HOUR
}
