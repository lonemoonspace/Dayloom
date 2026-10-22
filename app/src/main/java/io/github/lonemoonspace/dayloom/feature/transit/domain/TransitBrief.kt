package io.github.lonemoonspace.dayloom.feature.transit.domain

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The morning-brief line of a commute (train or bus): the next trip to work, with the worst status of its legs, named by line when
 * that is not the first leg ("R1 at 07:42 · L2: 6 min late").
 * 早间简报里一条通勤（火车或公交）的一行：下一个去上班的方案，附各段中最差的状态；不是第一段时注明线路（「R1 07:42 · L2：晚 6 分」）。
 */
object TransitBrief {

    /**
     * Only trips still ahead by the clock, unlike the card: the brief is sent once, without the card's "updated X min ago",
     * so a train kept on the card because no fetch confirmed it left must not be announced as the next one.
     * 和卡片不同，只看按时钟还没发车的方案：简报只发一次，没有卡片上的「X 分钟前更新」，卡片因为还没确认开走而保留的车不能
     * 被播报成下一班。
     */
    fun line(trips: CommuteTrips, now: ZonedDateTime): UiText? {
        // The direction the snapshot was fetched for: after noon that is the way home, and outbound is empty.
        // 用快照抓取时的方向：中午以后是回家方向，去程列表是空的。
        val option = TransitPolicy.visibleOptions(trips.current, now, 1).firstOrNull() ?: return null
        val first = option.legs.first()
        val (worstLeg, status) = TransitPolicy.worst(option) ?: return null
        val statusText = statusText(status)
        val shown = if (worstLeg !== first && status.state != LegState.ON_TIME) {
            UiText.Res(R.string.transit_line_status, listOf(worstLeg.line, statusText))
        } else {
            statusText
        }
        val at = Instant.ofEpochMilli(first.aimedDeparture).atZone(now.zone).format(TIME)
        return UiText.Res(R.string.transit_brief, listOf(first.line, at, shown))
    }

    private fun statusText(status: LegStatus): UiText = when (status.state) {
        LegState.ON_TIME -> UiText.Res(R.string.transit_on_time)
        LegState.DELAYED -> UiText.Plural(R.plurals.transit_late, status.delayMinutes)
        LegState.NO_REALTIME -> UiText.Res(R.string.transit_no_realtime)
        LegState.CANCELLED -> UiText.Res(R.string.transit_cancelled)
    }

    /** Numeric time reads the same in every language. / 数字时刻在各语言里写法一致。 */
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
}
