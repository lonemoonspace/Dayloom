package io.github.lonemoonspace.dayloom.feature.transit.domain

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.notify.AppNotification
import io.github.lonemoonspace.dayloom.core.notify.NotificationRule
import io.github.lonemoonspace.dayloom.core.notify.RuleDecision
import io.github.lonemoonspace.dayloom.core.notify.RuleInput
import io.github.lonemoonspace.dayloom.core.notify.StateCodec
import io.github.lonemoonspace.dayloom.core.notify.StringStateCodec
import io.github.lonemoonspace.dayloom.core.refresh.RefreshReport
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Commute disruptions: inside a daily window, tells the user when the next option is cancelled or heavily delayed. Only acts
 * on a commute snapshot refreshed in this round, so stale data never notifies.
 * 通勤异常：在日常时间窗内，下一个方案被取消或严重延误时通知用户。只在本轮刷新成功的通勤快照上判断，过期数据绝不触发通知。
 */
class DisruptionRule(
    /** One rule per commute route, e.g. `train_disruption`. / 每条通勤路线一条规则，如 `train_disruption`。 */
    override val name: String,
    private val enabled: suspend () -> Boolean,
    private val refreshed: (RefreshReport) -> Boolean,
    private val trips: suspend () -> CommuteTrips?,
    private val channelId: String,
    private val deepLink: String,
    private val notificationId: Int,
) : NotificationRule<String?> {

    override val codec: StateCodec<String?> = StringStateCodec

    override suspend fun isEnabled(): Boolean = enabled()

    override suspend fun evaluate(input: RuleInput, previous: String?): RuleDecision<String?> {
        if (!refreshed(input.report)) return RuleDecision(previous)
        val snapshot = trips() ?: return RuleDecision(previous)
        val option = when (snapshot.mode) {
            CommuteMode.OUTBOUND -> TransitPolicy.visibleOptions(snapshot.outbound, input.now, 1).firstOrNull()
            CommuteMode.INBOUND -> TransitPolicy.visibleOptions(snapshot.inbound, input.now, 1).firstOrNull()
            CommuteMode.BOTH -> null
        }
        val decision = DisruptionPolicy.evaluate(option, input.now.zone, previous)
        // The notification stays one line, so it names the worst leg: a cancelled connection matters more than an earlier
        // leg's delay, which may also be the part the user was already told about.
        // 通知只有一行，所以说最严重的那一段：换乘段被取消比前一段的延误更要紧，而那个延误可能已经通知过了。
        val (leg, status) = TransitPolicy.worst(TripOption(decision.disrupted)) ?: return RuleDecision(decision.newFingerprint)
        val at = Instant.ofEpochMilli(leg.aimedDeparture).atZone(input.now.zone).format(TIME)
        val body = if (status.state == LegState.CANCELLED) {
            UiText.Res(R.string.transit_notify_cancelled, listOf(leg.line, at))
        } else {
            UiText.Plural(R.plurals.transit_notify_delayed, status.delayMinutes, listOf(leg.line, at, status.delayMinutes))
        }
        val title = UiText.Res(if (snapshot.mode == CommuteMode.OUTBOUND) R.string.transit_notify_to_work else R.string.transit_notify_back_home)
        return RuleDecision(
            decision.newFingerprint,
            listOf(AppNotification(id = notificationId, channelId = channelId, title = title, body = body, deepLink = deepLink)),
        )
    }

    private companion object {
        /** Numeric time reads the same in every language. / 数字时刻在各语言里写法一致。 */
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
