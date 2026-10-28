package io.github.lonemoonspace.dayloom.feature.transit.domain

import java.time.Duration

/**
 * The late opposing train the first train of an option waits for, or may have to wait for: [line], [delayMinutes] late.
 * 方案第一班车在等、或可能要等的晚点对向车：[line]，晚了 [delayMinutes] 分钟。
 */
data class OpposingHint(val line: String, val delayMinutes: Int)

/**
 * Names a late opposing train at the first stop in two cases. When the first train is already late and leaves right after
 * it, it is the cause. When the first train still shows on time but the opposing one is due later, it may hold the first
 * train: Bane NOR often adds the crossing wait to the expected time only minutes before departure, too late to plan a
 * change. A first train that is late but leaves well before the opposing one gets nothing, since the crossing has moved.
 * Entur has no track data and does not tell rail directions apart, so the opposing train is one with the same line, a
 * different sign and a different platform, and both trains need live data; missing data gives no hint.
 * 在两种情况下写出起点站晚点的对向车。第一班车已经晚点、紧跟着它开时，它就是原因。第一班车还显示准点、而对向车要更晚才到时，
 * 第一班车可能要等它：Bane NOR 常常到开车前几分钟才把会车等待算进预计时刻，来不及安排换乘。第一班车晚点却远早于对向车开时
 * 不提示，因为会车点已经改了。Entur 没有轨道数据，也不区分火车方向，所以对向车按「线路相同、终点牌不同、站台不同」来认，
 * 两班车都要有实时数据；缺数据时不提示。
 */
object OpposingPolicy {

    /** How far apart the two timetabled departures may be to count as a crossing. / 两班车时刻表相差多少以内算会车。 */
    val CROSSING_WINDOW: Duration = Duration.ofMinutes(5)

    /** Delays and waits shorter than this are noise in real-time data. / 比这更短的晚点和等待只是实时数据的噪声。 */
    const val MIN_DELAY_MINUTES = 2

    /**
     * A late first train counts as held when it leaves between this long before and [HOLD_AFTER] after the opposing train;
     * the two are a few seconds apart in practice, either way round.
     * 晚点的第一班车在对向车之前这么久到之后 [HOLD_AFTER] 之内开，才算在等它；实际两者只差几秒，先后都有可能。
     */
    val HOLD_BEFORE: Duration = Duration.ofMinutes(1)

    val HOLD_AFTER: Duration = Duration.ofMinutes(2)

    /**
     * The late opposing train that holds, or may hold, [option]'s first train given the [departures] from its first stop,
     * or null when there is none.
     * 根据第一站的 [departures] 找出让 [option] 第一班车在等、或可能要等的晚点对向车；没有时返回 null。
     */
    fun hint(option: TripOption, departures: List<BoardDeparture>): OpposingHint? {
        val first = option.legs.firstOrNull() ?: return null
        // A rail replacement bus keeps mode `rail` but never waits at a crossing. / 铁路替代巴士的 mode 仍是 `rail`，但不会等会车。
        if (first.mode != RAIL || first.replacementBus || first.cancelled || !first.realtime || first.line.isEmpty()) return null
        val late = first.expectedDeparture - first.aimedDeparture >= MIN_DELAY_MINUTES * MINUTE
        val window = CROSSING_WINDOW.toMillis()
        val matching = departures.filter {
            it.mode == RAIL && !it.replacementBus && !it.cancelled && it.realtime && it.line == first.line &&
                it.frontText != first.frontText && differentPlatform(it.platform, first.platform) &&
                kotlin.math.abs(it.aimed - first.aimedDeparture) <= window &&
                it.expected - it.aimed >= MIN_DELAY_MINUTES * MINUTE &&
                if (late) {
                    first.expectedDeparture - it.expected in -HOLD_BEFORE.toMillis()..HOLD_AFTER.toMillis()
                } else {
                    it.expected - first.expectedDeparture >= MIN_DELAY_MINUTES * MINUTE
                }
        }
        val reason = matching.minByOrNull { kotlin.math.abs(first.expectedDeparture - it.expected) } ?: return null
        return OpposingHint(reason.line, Math.floorDiv(reason.expected - reason.aimed, MINUTE).toInt())
    }

    /** Without both platforms a different sign could just be a branch of the same direction. / 缺一个站台时，终点牌不同也可能只是同方向的另一条支线。 */
    private fun differentPlatform(a: String, b: String) = a.isNotEmpty() && b.isNotEmpty() && a != b

    private const val MINUTE = 60_000L

    private const val RAIL = "rail"
}
