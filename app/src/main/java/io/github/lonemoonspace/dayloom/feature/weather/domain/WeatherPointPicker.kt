package io.github.lonemoonspace.dayloom.feature.weather.domain

import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * Picks the "current" point from MET's time series. MET normally has one point per hour, so the window `now-45min .. now+90min`
 * always hits; the window only tolerates clock skew and hour boundaries.
 * When it misses (MET stopped updating, a truncated series) the point **closest to now** is used, not the first one: the
 * series is ascending, so the first point may be hours old and would show a normal-looking card with a stale reading.
 * 从 MET 的逐时序列里挑出「当前」那个数据点。MET 正常每小时一个点，所以 `now-45min .. now+90min` 的窗口必然命中；
 * 窗口只是为了容忍设备时钟偏差与整点边界。
 * 窗口命中不了（数据断更、序列被截断）时取**时间上最接近现在**的点，而不是第一个：序列是升序的，第一个点可能是几小时前的，
 * 卡片看起来正常、数值却是旧的。
 */
object WeatherPointPicker {

    const val WINDOW_BEFORE_MINUTES = 45L

    /** The next hour's point still counts as current. / 下一个整点的预报点仍算「当前」。 */
    const val WINDOW_AFTER_MINUTES = 90L

    /**
     * [items] in ascending time order; [timeOf] returns null when a point's time cannot be read, and a series without any
     * readable time falls back to its first item.
     * [items] 按时间升序；[timeOf] 读不出时刻时返回 null，整条序列都读不出时刻时退回第一个点。
     */
    fun <T> pick(items: List<T>, now: ZonedDateTime, timeOf: (T) -> ZonedDateTime?): T? {
        val timed = items.mapNotNull { item -> timeOf(item)?.let { at -> item to at } }
        if (timed.isEmpty()) return items.firstOrNull()
        timed.firstOrNull { (_, at) ->
            !at.isBefore(now.minusMinutes(WINDOW_BEFORE_MINUTES)) && !at.isAfter(now.plusMinutes(WINDOW_AFTER_MINUTES))
        }?.let { return it.first }
        // On a tie minByOrNull keeps the earlier point, so the result does not depend on ordering noise.
        // 同距时 minByOrNull 取先出现的（更早的）点，结果确定、不随顺序抖动。
        return timed.minByOrNull { (_, at) -> abs(Duration.between(now, at).toMinutes()) }?.first
    }
}
