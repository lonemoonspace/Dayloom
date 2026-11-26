package io.github.lonemoonspace.dayloom.feature.weather.domain

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.notify.AppNotification
import io.github.lonemoonspace.dayloom.core.notify.JsonStateCodec
import io.github.lonemoonspace.dayloom.core.notify.NotificationRule
import io.github.lonemoonspace.dayloom.core.notify.RuleDecision
import io.github.lonemoonspace.dayloom.core.notify.RuleInput
import io.github.lonemoonspace.dayloom.core.notify.StateCodec
import io.github.lonemoonspace.dayloom.core.refresh.RefreshReport
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/**
 * New MET warnings for the weather place, once each (see [WarningPolicy.key]); opt-in. Only acts on a snapshot refreshed in
 * this round. When every warning told about is over, the notification is taken down.
 * 天气地点的新 MET 预警，每条只通知一次（见 [WarningPolicy.key]）；需用户开启。只在本轮刷新成功的快照上判断。说过的预警全部结束后，
 * 撤下通知。
 */
class WarningRule(
    private val enabled: suspend () -> Boolean,
    private val refreshed: (RefreshReport) -> Boolean,
    private val warnings: suspend () -> WeatherWarnings?,
    private val channelId: String,
    private val deepLink: String,
    private val notificationId: Int,
) : NotificationRule<Set<String>> {

    override val name = "warnings"

    override val codec: StateCodec<Set<String>> = JsonStateCodec(SetSerializer(String.serializer()), emptySet())

    override suspend fun isEnabled(): Boolean = enabled()

    override suspend fun evaluate(input: RuleInput, previous: Set<String>): RuleDecision<Set<String>> {
        if (!refreshed(input.report)) return RuleDecision(previous)
        val snapshot = warnings() ?: return RuleDecision(previous)
        val (fresh, keys) = WarningPolicy.toNotify(snapshot.warnings, previous, input.now)
        if (fresh.isEmpty()) {
            return RuleDecision(keys, cancel = if (keys.isEmpty() && previous.isNotEmpty()) listOf(notificationId) else emptyList())
        }
        val notification = AppNotification(
            id = notificationId,
            channelId = channelId,
            title = if (fresh.size == 1) WarningText.title(fresh.first()) else UiText.Res(R.string.weather_warning_notify_title),
            body = fresh.singleOrNull()?.let { WarningText.span(WarningPolicy.window(it, input.now), input.now) }
                ?: UiText.Lines(fresh.map { WarningText.line(it, input.now) }),
            deepLink = deepLink,
            // A new warning is news even while an older one is still in the shade. / 即使旧预警还在通知栏，新预警也是新消息。
            alertOnce = false,
        )
        return RuleDecision(keys, listOf(notification))
    }
}
