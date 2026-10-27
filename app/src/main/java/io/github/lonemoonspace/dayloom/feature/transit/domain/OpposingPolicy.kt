package io.github.lonemoonspace.dayloom.feature.transit.domain

import java.time.Duration

/**
 * Why the first train of an option is late: it waits at its first stop for [line] going the other way, itself
 * [delayMinutes] late.
 * 方案第一班车晚点的原因：它在起点站等反方向的 [line] 会车，那班车自己晚了 [delayMinutes] 分钟。
 */
data class OpposingHint(val line: String, val delayMinutes: Int)

/**
 * Explains a delay the live data already shows, instead of predicting one: Bane NOR's expected times already include a
 * crossing wait, and when an opposing train is very late the crossing is often moved, so a prediction mostly misfired.
 * Entur has no track data and does not tell rail directions apart, so the opposing train is one with the same line, a
 * different sign and a different platform, and both trains need live data; missing data gives no hint.
 * 解释实时数据已经显示的晚点，而不是预测晚点：Bane NOR 的预计时刻已经算进了会车等待，对向车大晚点时会车点又常被挪走，
 * 所以预测大多不准。Entur 没有轨道数据，也不区分火车方向，所以对向车按「线路相同、终点牌不同、站台不同」来认，两班车都要有
 * 实时数据；缺数据时不提示。
 */
object OpposingPolicy {

    /** How far apart the two timetabled departures may be to count as a crossing. / 两班车时刻表相差多少以内算会车。 */
    val CROSSING_WINDOW: Duration = Duration.ofMinutes(5)

    /** Delays shorter than this are noise in real-time data. / 比这更短的晚点只是实时数据的噪声。 */
    const val MIN_DELAY_MINUTES = 2

    /**
     * The first train counts as held when it leaves between this long before and [HOLD_AFTER] after the opposing train;
     * the two are a few seconds apart in practice, either way round.
     * 第一班车在对向车之前这么久到之后 [HOLD_AFTER] 之内开，才算在等它；实际两者只差几秒，先后都有可能。
     */
    val HOLD_BEFORE: Duration = Duration.ofMinutes(1)

    val HOLD_AFTER: Duration = Duration.ofMinutes(2)

    /**
     * The reason for [option]'s late first train given the [departures] from its first stop, or null when it is not late
     * or no late opposing train explains it.
     * 根据第一站的 [departures] 说明 [option] 第一班车晚点的原因；没晚点或没有晚点的对向车能解释时返回 null。
     */
    fun hint(option: TripOption, departures: List<BoardDeparture>): OpposingHint? {
        val first = option.legs.firstOrNull() ?: return null
        // A rail replacement bus keeps mode `rail` but never waits at a crossing. / 铁路替代巴士的 mode 仍是 `rail`，但不会等会车。
        if (first.mode != RAIL || first.replacementBus || first.cancelled || !first.realtime || first.line.isEmpty()) return null
        if (first.expectedDeparture - first.aimedDeparture < MIN_DELAY_MINUTES * MINUTE) return null
        val window = CROSSING_WINDOW.toMillis()
        val held = departures.filter {
            it.mode == RAIL && !it.replacementBus && !it.cancelled && it.realtime && it.line == first.line &&
                it.frontText != first.frontText && differentPlatform(it.platform, first.platform) &&
                kotlin.math.abs(it.aimed - first.aimedDeparture) <= window &&
                it.expected - it.aimed >= MIN_DELAY_MINUTES * MINUTE &&
                first.expectedDeparture - it.expected in -HOLD_BEFORE.toMillis()..HOLD_AFTER.toMillis()
        }
        val reason = held.minByOrNull { kotlin.math.abs(first.expectedDeparture - it.expected) } ?: return null
        return OpposingHint(reason.line, Math.floorDiv(reason.expected - reason.aimed, MINUTE).toInt())
    }

    /** Without both platforms a different sign could just be a branch of the same direction. / 缺一个站台时，终点牌不同也可能只是同方向的另一条支线。 */
    private fun differentPlatform(a: String, b: String) = a.isNotEmpty() && b.isNotEmpty() && a != b

    private const val MINUTE = 60_000L

    private const val RAIL = "rail"
}
