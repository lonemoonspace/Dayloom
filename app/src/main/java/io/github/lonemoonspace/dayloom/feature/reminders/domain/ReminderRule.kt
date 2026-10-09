package io.github.lonemoonspace.dayloom.feature.reminders.domain

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.notify.AppNotification
import io.github.lonemoonspace.dayloom.core.notify.JsonStateCodec
import io.github.lonemoonspace.dayloom.core.notify.NotificationRule
import io.github.lonemoonspace.dayloom.core.notify.RuleDecision
import io.github.lonemoonspace.dayloom.core.notify.RuleInput
import io.github.lonemoonspace.dayloom.core.notify.StateCodec
import java.time.format.DateTimeFormatter

/**
 * Expiry reminders. Reads only the module's own item list; needs no data source. Text stays [UiText] until it is sent.
 * 到期提醒。只读本模块自己的条目列表，不依赖任何数据来源。文字一直是 [UiText]，发送时才解析。
 */
class ReminderRule(
    private val enabled: suspend () -> Boolean,
    private val items: suspend () -> List<ReminderItem>,
    private val channelId: String,
    private val deepLink: String,
    private val notificationId: (ReminderItem) -> Int,
) : NotificationRule<ReminderPolicy.NotifiedState> {

    override val name = "expiry"

    override val codec: StateCodec<ReminderPolicy.NotifiedState> =
        JsonStateCodec(ReminderPolicy.NotifiedState.serializer(), ReminderPolicy.NotifiedState())

    override suspend fun isEnabled(): Boolean = enabled()

    override suspend fun evaluate(
        input: RuleInput,
        previous: ReminderPolicy.NotifiedState,
    ): RuleDecision<ReminderPolicy.NotifiedState> {
        val decision = ReminderPolicy.evaluate(items(), input.now.toLocalDateTime(), previous)
        return RuleDecision(decision.newState, decision.reminders.map(::notification))
    }

    private fun notification(reminder: ReminderPolicy.Reminder): AppNotification {
        val status = reminder.status
        val name = status.item.name
        val title = when (reminder.stage) {
            ReminderPolicy.Stage.EXPIRED -> UiText.Res(R.string.reminders_notify_expired, listOf(name))
            ReminderPolicy.Stage.LAST_DAY -> UiText.Res(R.string.reminders_notify_today, listOf(name))
            ReminderPolicy.Stage.SOON -> if (status.daysUntil == 1L) {
                UiText.Res(R.string.reminders_notify_tomorrow, listOf(name))
            } else {
                UiText.Plural(R.plurals.reminders_notify_days, status.daysUntil.toInt(), listOf(name, status.daysUntil.toInt()))
            }
        }
        return AppNotification(
            id = notificationId(status.item),
            channelId = channelId,
            title = title,
            // Numeric date and time read the same in every language. / 数字日期时间在各语言里写法一致。
            body = UiText.Res(R.string.reminders_notify_body, listOf(status.until.format(BODY_FORMAT))),
            deepLink = deepLink,
        )
    }

    private companion object {
        val BODY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
