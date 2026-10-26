package io.github.lonemoonspace.dayloom.feature.transit.domain

import java.time.Duration

/** What a late opposing train may do to an option. / 对向车晚点可能给一个方案带来的后果。 */
sealed interface OpposingEffect {
    /** The change at the transfer stop may be missed; [line] is the train changed to. / 换乘站的换乘可能赶不上；[line] 是要换的车。 */
    data class MissTransfer(val line: String) : OpposingEffect

    /** The change still leaves enough time. / 换乘时间仍然够。 */
    data object TransferHolds : OpposingEffect

    /** A direct train arrives about [minutes] later. / 直达车大约晚到 [minutes] 分钟。 */
    data class LateArrival(val minutes: Int) : OpposingEffect
}

/** The hint for one option: which line runs the other way, how late it is, and what it may cause. / 一个方案的提示：对向是哪条线、晚了多少、可能造成什么。 */
data class OpposingHint(val line: String, val delayMinutes: Int, val effect: OpposingEffect)

/**
 * A guess at single-track crossings: when a train of the same line leaving the other way at about the same time is late,
 * the first train of an option may have to wait for it. Entur has no track data and does not tell rail directions apart, so
 * the opposing train is one with the same line, a different sign and a different platform; the hint is opt-in because a
 * double-track stop or a crossing moved elsewhere gives false alarms.
 * 对单线会车的推测：同一条线、反方向、差不多同时开的车晚点时，方案的第一班车可能要等它。Entur 没有轨道数据，也不区分火车方向，
 * 所以对向车按「线路相同、终点牌不同、站台不同」来认；双线车站或调度改了会车点时会误报，所以这个提示要用户自己打开。
 */
object OpposingPolicy {

    /** How far apart the two timetabled departures may be to count as a crossing. / 两班车时刻表相差多少以内算会车。 */
    val CROSSING_WINDOW: Duration = Duration.ofMinutes(5)

    /** Waits shorter than this are noise in real-time data. / 比这更短的等待只是实时数据的噪声。 */
    const val MIN_WAIT_MINUTES = 2

    /** The least time left at the transfer stop for a change still to count as made. / 换乘站至少还剩这么多时间，才算还赶得上。 */
    const val MIN_CHANGE_MINUTES = 2

    /**
     * The hint for [option] given the [departures] from its first stop, or null when no late opposing train makes its first
     * train wait. Once the first train's own expected time already covers the wait, there is nothing left to warn about.
     * 根据第一站的 [departures] 给 [option] 的提示；没有晚点的对向车让第一班车等待时返回 null。第一班车自己的预计时刻已经
     * 包含了这段等待时，就没什么要提醒的了。
     */
    fun hint(option: TripOption, departures: List<BoardDeparture>): OpposingHint? {
        val first = option.legs.firstOrNull() ?: return null
        if (first.mode != RAIL || first.cancelled || first.line.isEmpty()) return null
        val window = CROSSING_WINDOW.toMillis()
        val opposing = departures.filter {
            it.mode == RAIL && !it.cancelled && it.line == first.line &&
                it.frontText != first.frontText && differentPlatform(it.platform, first.platform) &&
                kotlin.math.abs(it.aimed - first.aimedDeparture) <= window
        }
        val worst = opposing.maxByOrNull { it.expected } ?: return null
        val waitMinutes = minutes(worst.expected - first.expectedDeparture)
        if (waitMinutes < MIN_WAIT_MINUTES || minutes(worst.expected - worst.aimed) < MIN_WAIT_MINUTES) return null
        val next = option.legs.getOrNull(1)
        val effect = if (next == null) {
            OpposingEffect.LateArrival(waitMinutes)
        } else {
            val slack = minutes(next.expectedDeparture - first.expectedArrival)
            if (slack - waitMinutes < MIN_CHANGE_MINUTES) OpposingEffect.MissTransfer(next.line) else OpposingEffect.TransferHolds
        }
        return OpposingHint(worst.line, minutes(worst.expected - worst.aimed), effect)
    }

    /** Unknown platforms do not rule a train out. / 站台未知时不据此排除。 */
    private fun differentPlatform(a: String, b: String) = a.isEmpty() || b.isEmpty() || a != b

    private fun minutes(millis: Long) = Math.floorDiv(millis, 60_000L).toInt()

    private const val RAIL = "rail"
}
