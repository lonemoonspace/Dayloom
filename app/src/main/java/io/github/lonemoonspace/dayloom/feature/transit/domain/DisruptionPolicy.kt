package io.github.lonemoonspace.dayloom.feature.transit.domain

import java.time.Instant
import java.time.ZoneId

/**
 * Whether a commute disruption is worth a notification; ported from the original `CommuteDisruptionPolicy`.
 * The background refresh runs often, but the same delay must not be pushed again every time, so the current disruption is
 * reduced to a stable fingerprint and only a changed fingerprint notifies. When the disruption clears the fingerprint is
 * reset, so the same problem appearing again later counts as new.
 * 通勤异常是否值得通知；移植自原项目 `CommuteDisruptionPolicy`。
 * 后台刷新很频繁，但同一个延误不能每次都重推，所以把当前异常压成稳定的指纹，只有指纹变化才通知。异常恢复时指纹清空，
 * 之后同样的问题再出现会被当作新异常。
 */
object DisruptionPolicy {

    data class Decision(
        /** The legs to tell the user about; empty means no notification. / 要告诉用户的各段；为空表示不通知。 */
        val disrupted: List<TransitLeg>,
        /** Store this for the next call; null = no active disruption. / 存起来供下次调用；null = 当前没有异常。 */
        val newFingerprint: String?,
    )

    /**
     * [option] is the next option of the window under way, null outside the windows: then nothing is decided and the
     * fingerprint passes through untouched.
     * [option] 是正在进行的时间窗里的下一个方案；时间窗外为 null：此时不做判定，指纹原样透传。
     */
    fun evaluate(option: TripOption?, zone: ZoneId, previousFingerprint: String?): Decision {
        if (option == null) return Decision(emptyList(), previousFingerprint)
        val disrupted = option.legs.filter(::isDisrupted)
        if (disrupted.isEmpty()) return Decision(emptyList(), null)
        val fingerprint = fingerprintOf(disrupted, zone)
        if (fingerprint == previousFingerprint) return Decision(emptyList(), fingerprint)
        return Decision(disrupted, fingerprint)
    }

    /**
     * Cancelled, or a known delay of at least [TransitPolicy.DISRUPTION_DELAY_MINUTES]. Without real-time data the delay is
     * unknown, so only a cancellation counts: better to stay quiet than to alarm on unreliable data.
     * 已取消，或已知延误至少 [TransitPolicy.DISRUPTION_DELAY_MINUTES] 分钟。没有实时数据时延误未知，只看取消：宁可不说，
     * 也不拿不可靠的数据打扰人。
     */
    fun isDisrupted(leg: TransitLeg): Boolean {
        val status = TransitPolicy.legStatus(leg)
        return status.state == LegState.CANCELLED ||
            (status.state == LegState.DELAYED && status.delayMinutes >= TransitPolicy.DISRUPTION_DELAY_MINUTES)
    }

    /**
     * Date + line + from + cancelled + delay bucket. Only the date, not the time: the expected time moves with the delay and
     * would change the fingerprint on every refresh. Buckets, not minutes: 6 and 8 minutes are the same event, 6 growing to 20
     * is worth telling again.
     * 日期 + 线路 + 起点 + 是否取消 + 延误档位。只取日期不取时刻：预计时刻会随延误后移，每次刷新都会改变指纹。取档位而不是
     * 分钟数：6 分钟和 8 分钟是同一件事，6 分钟涨到 20 分钟才值得再提醒。
     */
    private fun fingerprintOf(legs: List<TransitLeg>, zone: ZoneId): String = legs.joinToString("|") { leg ->
        val day = Instant.ofEpochMilli(leg.aimedDeparture).atZone(zone).toLocalDate()
        val status = TransitPolicy.legStatus(leg)
        val delay = if (status.state == LegState.DELAYED) delayBucket(status.delayMinutes) else -1
        "$day#${leg.line}#${leg.fromName}#${leg.cancelled}#$delay"
    }

    private fun delayBucket(minutes: Int): Int = when {
        minutes < 5 -> 0
        minutes < 10 -> 5
        minutes < 20 -> 10
        minutes < 30 -> 20
        minutes < 45 -> 30
        minutes < 60 -> 45
        else -> 60
    }
}
