package io.github.lonemoonspace.dayloom.feature.transit.domain

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The transport line of the morning brief: the next trip to work, with the worst status of its legs, named by line when
 * that is not the first leg ("R1 at 07:42 · L2: 6 min late").
 * 早间简报里的公共交通一行：下一个去上班的方案，附各段中最差的状态；不是第一段时注明线路（「R1 07:42 · L2：晚 6 分」）。
 */
object TransitBrief {

    /**
     * Train and bus each say their line when they have something, joined into the module's one line.
     * 火车与公交有内容时各说一句，合成本模块的一行。
     */
    fun lines(trips: List<CommuteTrips>, now: ZonedDateTime): UiText? {
        val parts = trips.mapNotNull { line(it, now) }
        return when (parts.size) {
            0 -> null
            1 -> parts.single()
            else -> UiText.Res(R.string.transit_brief_joined, parts.take(2))
        }
    }

    fun line(trips: CommuteTrips, now: ZonedDateTime): UiText? {
        val option = TransitPolicy.visibleOptions(trips.outbound, now, 1).firstOrNull() ?: return null
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
