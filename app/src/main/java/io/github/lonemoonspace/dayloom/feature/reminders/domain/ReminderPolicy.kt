package io.github.lonemoonspace.dayloom.feature.reminders.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

/**
 * One thing that expires: a monthly pass, a parking permit, a passport. [until] is a wall-clock time in the app's time zone,
 * stored as `yyyy-MM-ddTHH:mm`; the item is still valid during that whole minute.
 * 一件会到期的东西：月票、停车证、护照。[until] 是应用时区下的墙上时间，存为 `yyyy-MM-ddTHH:mm`；截止那一分钟内仍有效。
 */
@Serializable
data class ReminderItem(
    /** `item<n>`; never derived from the name, so renaming keeps notification state. / `item<n>`；不由名称生成，改名不影响通知状态。 */
    val id: String = "",
    val name: String = "",
    val until: String = "",
    /** Warn from this many calendar days before the last day (3 = the last three days). / 截止日前多少个自然日开始提醒（3 = 最后三天）。 */
    val warnDays: Int = ReminderPolicy.DEFAULT_WARN_DAYS,
)

/**
 * Validity and reminder decisions for expiring items; the logic of the original `TicketPolicy`, generalized from two fixed
 * tickets to any list. Pure, no Android, `now` as a parameter.
 * 到期物品的有效期判定与提醒；移植自原项目 `TicketPolicy`，从两张固定车票推广到任意列表。纯逻辑、无 Android，`now` 作参数。
 */
object ReminderPolicy {

    enum class State {
        ACTIVE,

        /** The last day is fewer than `warnDays` calendar days away and the item has not expired. / 截止日距今少于 `warnDays` 个自然日且尚未过期。 */
        EXPIRING,
        EXPIRED,
    }

    data class Status(
        val item: ReminderItem,
        val state: State,
        val until: LocalDateTime,
        /** Calendar days to the last day: 0 today, 1 tomorrow, negative once past. / 截止日距今的自然日数：今天 0，明天 1，已过则为负。 */
        val daysUntil: Long,
    )

    const val DEFAULT_WARN_DAYS = 3
    val WARN_DAYS_RANGE = 1..60

    /** A missing time means the end of the day. / 未填时间即当天结束。 */
    val DEFAULT_TIME: LocalTime = LocalTime.of(23, 59)

    /** Reminders only during the day, so a background refresh never wakes anyone. / 只在白天提醒，后台刷新不会半夜吵醒人。 */
    const val NOTIFY_FROM_HOUR = 8
    const val NOTIFY_UNTIL_HOUR = 21

    /** How long after expiry the "expired" reminder may still be sent. / 过期后多久之内仍可发送「已过期」提醒。 */
    val EXPIRED_NOTICE_WINDOW: Duration = Duration.ofDays(1)

    private val STORE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    /** Null for blank or malformed input; a bare date means 23:59. / 空白或格式错误返回 null；只有日期时按 23:59。 */
    fun parse(raw: String): LocalDateTime? {
        val value = raw.trim().takeIf { it.isNotEmpty() } ?: return null
        val parsed = runCatching { LocalDateTime.parse(value) }.getOrNull()
            ?: runCatching { LocalDate.parse(value).atTime(DEFAULT_TIME) }.getOrNull()
        return parsed?.truncatedTo(ChronoUnit.MINUTES)
    }

    fun format(until: LocalDateTime): String = until.truncatedTo(ChronoUnit.MINUTES).format(STORE_FORMAT)

    fun status(item: ReminderItem, now: LocalDateTime): Status? {
        val until = parse(item.until) ?: return null
        val daysUntil = ChronoUnit.DAYS.between(now.toLocalDate(), until.toLocalDate())
        val state = when {
            // Valid during the whole last minute: at 23:59:30 an item until 23:59 has not expired. / 截止那一分钟内仍有效。
            now.truncatedTo(ChronoUnit.MINUTES).isAfter(until) -> State.EXPIRED
            daysUntil < item.warnDays.coerceIn(WARN_DAYS_RANGE) -> State.EXPIRING
            else -> State.ACTIVE
        }
        return Status(item, state, until, daysUntil)
    }

    /** Valid items, soonest first. / 有效条目，最早到期的在前。 */
    fun statuses(items: List<ReminderItem>, now: LocalDateTime): List<Status> =
        items.mapNotNull { status(it, now) }.sortedWith(compareBy({ it.until }, { it.item.name }))

    /** Whether the card should move to the top. / 卡片是否应该置顶。 */
    fun needsAttention(items: List<ReminderItem>, now: LocalDateTime): Boolean =
        statuses(items, now).any { it.state != State.ACTIVE }

    /** A fresh id: `item<n>` with the smallest unused n. / 新 id：`item<n>`，n 取最小未用的数。 */
    fun newId(items: List<ReminderItem>): String {
        val used = items.mapTo(mutableSetOf()) { it.id }
        return generateSequence(1) { it + 1 }.map { "item$it" }.first { it !in used }
    }

    /** The number in `item<n>`, used to give each item its own notification id; 0 for anything else. / `item<n>` 里的数字，用来给每个条目分配通知 id；其他格式为 0。 */
    fun idNumber(id: String): Int = id.removePrefix("item").toIntOrNull() ?: 0

    enum class Stage { SOON, LAST_DAY, EXPIRED }

    /** Keys of reminders already sent. / 已发送提醒的键。 */
    @Serializable
    data class NotifiedState(val keys: Set<String> = emptySet())

    data class Reminder(val status: Status, val stage: Stage)

    data class Decision(val reminders: List<Reminder>, val newState: NotifiedState)

    /**
     * Each item and expiry time is reminded once per stage: when it enters its warning days, on the last day, and once it
     * has expired. Keys include the expiry time, so entering a new time after renewing starts over, and keys that no longer
     * match a current item are dropped. Stages already passed are marked as sent, so a first check on the last day sends one
     * reminder, not two.
     * 每个条目、每个截止时间每档只提醒一次：进入提醒期、截止当天、已过期。键里带截止时间，续期后填入新时间会重新计提醒；
     * 不再对应当前条目的旧键被清理。已经越过的档位记为已发送，所以第一次检查就在截止当天时只发一条，不会连发两条。「已过期」提醒只在过期后 [EXPIRED_NOTICE_WINDOW] 之内发送。
     */
    fun evaluate(items: List<ReminderItem>, now: LocalDateTime, previous: NotifiedState): Decision {
        val statuses = statuses(items, now)
        val liveKeys = statuses.flatMap { s -> Stage.entries.map { key(s, it) } }.toSet()
        val kept = previous.keys.filterTo(mutableSetOf()) { it in liveKeys }
        if (now.hour !in NOTIFY_FROM_HOUR until NOTIFY_UNTIL_HOUR) return Decision(emptyList(), NotifiedState(kept))
        val reminders = mutableListOf<Reminder>()
        for (s in statuses) {
            val stage = when {
                s.state == State.EXPIRED -> Stage.EXPIRED
                s.state == State.EXPIRING && s.daysUntil == 0L -> Stage.LAST_DAY
                s.state == State.EXPIRING -> Stage.SOON
                else -> continue
            }
            if (key(s, stage) in kept) continue
            Stage.entries.filter { it.ordinal <= stage.ordinal }.forEach { kept += key(s, it) }
            // Only a fresh expiry is news; an item long expired (an old date typed in) stays quiet and just shows red.
            // 只有刚过期才值得通知；早已过期的条目（例如录入了一个旧日期）不打扰，只在卡片上显示为红色。
            if (stage == Stage.EXPIRED && now.isAfter(s.until.plus(EXPIRED_NOTICE_WINDOW))) continue
            reminders += Reminder(s, stage)
        }
        return Decision(reminders, NotifiedState(kept))
    }

    private fun key(s: Status, stage: Stage) = "${s.item.id}:${format(s.until)}:${stage.name}"
}
