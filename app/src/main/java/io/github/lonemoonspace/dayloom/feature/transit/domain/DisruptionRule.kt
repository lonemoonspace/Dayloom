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
 * Commute disruptions: tells the user when the next option is cancelled or heavily delayed, in the daytime and when it
 * leaves soon ([TransitPolicy.isAlertable]). Only acts on a commute snapshot refreshed in this round, so stale data never
 * notifies.
 * 通勤异常：下一个方案被取消或严重延误时通知用户，只在白天且它即将发车时（[TransitPolicy.isAlertable]）。只在本轮刷新成功
 * 的通勤快照上判断，过期数据绝不触发通知。
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
        val next = TransitPolicy.visibleOptions(snapshot.current, input.now, 1).firstOrNull()
        // A trip that is not alertable yet counts as no trip; once it comes within reach it is told once.
        // 还不该提醒的班次视同没有；进入范围后提醒一次。
        val option = next?.takeIf { TransitPolicy.isAlertable(it, input.now) }
        if (option == null && previous != null) {
            // No fetchedAt: the rule only runs right after this round refreshed the snapshot, so nothing past is unconfirmed
            // beyond the time the round itself took.
            // 不传 fetchedAt：规则只在本轮刷新完快照之后才运行，除了这一轮本身花的时间，不会有过点却未确认的车。
            val upcoming = TransitPolicy.visibleOptions(snapshot.current, input.now, Int.MAX_VALUE)
            // Nothing to decide, but the trip told about may have left: then its alert is withdrawn and the state cleared.
            // 没有可判定的方案，但提醒过的那班车可能已经开走：那就撤回通知并清空状态。
            return if (DisruptionPolicy.stillAhead(upcoming, input.now.zone, previous)) RuleDecision(previous)
            else RuleDecision(null, cancel = listOf(notificationId))
        }
        val decision = DisruptionPolicy.evaluate(option, input.now.zone, previous)
        // The notification stays one line, so it names the worst leg: a cancelled connection matters more than an earlier
        // leg's delay, which may also be the part the user was already told about.
        // 通知只有一行，所以说最严重的那一段：换乘段被取消比前一段的延误更要紧，而那个延误可能已经通知过了。
        val (leg, status) = TransitPolicy.worst(TripOption(decision.disrupted)) ?: return RuleDecision(
            decision.newFingerprint,
            // The disruption told about is over (or that trip has left and the next is fine): its alert must not linger.
            // 提醒过的异常已经结束（或那班车已开走、下一班正常）：它的通知不能一直留着。
            cancel = if (previous != null && decision.newFingerprint == null) listOf(notificationId) else emptyList(),
        )
        val at = Instant.ofEpochMilli(leg.aimedDeparture).atZone(input.now.zone).format(TIME)
        val body = if (status.state == LegState.CANCELLED) {
            UiText.Res(R.string.transit_notify_cancelled, listOf(leg.line, at))
        } else {
            UiText.Plural(R.plurals.transit_notify_delayed, status.delayMinutes, listOf(leg.line, at, status.delayMinutes))
        }
        val title = UiText.Res(if (snapshot.mode == CommuteMode.OUTBOUND) R.string.transit_notify_to_work else R.string.transit_notify_back_home)
        return RuleDecision(
            decision.newFingerprint,
            // A new fingerprint is a new disruption (another trip, or a delay that grew), so it rings even over the last one.
            // 新指纹就是新的异常（另一班车，或延误加重），即使上一条还在也要响铃。
            listOf(AppNotification(id = notificationId, channelId = channelId, title = title, body = body, deepLink = deepLink, alertOnce = false)),
        )
    }

    private companion object {
        /** Numeric time reads the same in every language. / 数字时刻在各语言里写法一致。 */
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
