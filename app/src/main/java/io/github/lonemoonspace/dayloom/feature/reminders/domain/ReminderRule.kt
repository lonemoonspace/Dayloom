package io.github.lonemoonspace.dayloom.feature.reminders.domain

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.notify.AppNotification
import io.github.lonemoonspace.dayloom.core.notify.JsonStateCodec
import io.github.lonemoonspace.dayloom.core.notify.NotificationRule
import io.github.lonemoonspace.dayloom.core.notify.RuleDecision
import io.github.lonemoonspace.dayloom.core.notify.RuleInput
import io.github.lonemoonspace.dayloom.core.notify.StateCodec
import java.time.LocalDateTime
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
        return AppNotification(
            id = notificationId(status.item),
            channelId = channelId,
            title = headline(status),
            // Numeric date and time read the same in every language. / 数字日期时间在各语言里写法一致。
            body = UiText.Res(R.string.reminders_notify_body, listOf(status.until.format(BODY_FORMAT))),
            deepLink = deepLink,
        )
    }

    private companion object {
        val BODY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}

/**
 * "Monthly pass expires tomorrow"; the notification title and the morning brief line. The stage follows from the status the
 * same way as in [ReminderPolicy.evaluate].
 * 「月票明天到期」；用作通知标题与早间简报的一行。档位由状态推出，与 [ReminderPolicy.evaluate] 的判定一致。
 */
internal fun headline(status: ReminderPolicy.Status): UiText {
    val name = status.item.name
    val days = status.daysUntil.toInt()
    return when {
        status.state == ReminderPolicy.State.EXPIRED -> UiText.Res(R.string.reminders_notify_expired, listOf(name))
        days <= 0 -> UiText.Res(R.string.reminders_notify_today, listOf(name))
        days == 1 -> UiText.Res(R.string.reminders_notify_tomorrow, listOf(name))
        else -> UiText.Plural(R.plurals.reminders_notify_days, days, listOf(name, days))
    }
}

/** The morning brief line: the soonest item, and how many more there are. / 早间简报的一行：最早到期的条目，以及另外还有几项。 */
internal fun briefLine(items: List<ReminderItem>, now: LocalDateTime): UiText? {
    val due = ReminderPolicy.briefItems(items, now)
    val first = headline(due.firstOrNull() ?: return null)
    val more = due.size - 1
    return if (more == 0) first else UiText.Plural(R.plurals.reminders_brief_more, more, listOf(first, more))
}
