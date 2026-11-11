package io.github.lonemoonspace.dayloom.core.notify

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException

/**
 * Names the shell itself uses for notifications. Module ranges must stay clear of [RESERVED_IDS] (checked with the registry).
 * Frozen from v1.0.0 like the module ones.
 * 外壳自己用的通知名称。模块号段不能落进 [RESERVED_IDS]（与注册表一起检查）。与模块的一样，从 v1.0.0 起冻结。
 */
object CoreNotifications {
    val RESERVED_IDS = 1..999
    const val BRIEF_ID = 1
    const val BRIEF_STATE_KEY = "core.morning_brief"
    const val BRIEF_DEEP_LINK = "dayloom://home"

    /** Channel `core.brief`; the id is `<owner>.<name>` like the module channels. / 渠道 `core.brief`；id 与模块渠道一样是 `<所属>.<名称>`。 */
    val BRIEF_CHANNEL = ChannelSpec("brief", R.string.brief_channel, R.string.brief_channel_description)
    const val BRIEF_CHANNEL_ID = "core.brief"
}

/**
 * When the morning brief goes out: on the first background round at or after the chosen time, once a day. A round runs
 * every 15 minutes, so it arrives within a quarter of an hour of that time, later if the phone was offline; no exact alarm
 * is worth the battery for that. After [LATE_LIMIT_MINUTES] it is no longer news and the day is skipped. Deduplicated by
 * date, not by content: the brief is a daily habit even when nothing changed.
 * 早间简报的发送时机：到达设定时间后的第一次后台刷新，每天一次。后台每 15 分钟跑一轮，所以会在设定时间后一刻钟内送达，
 * 手机离线时顺延；为这点精度不值得耗电用精确闹钟。超过 [LATE_LIMIT_MINUTES] 就不再是「早间」消息，当天跳过。按日期去重
 * 而不是按内容：哪怕什么都没变，简报也是每天一条。
 */
object MorningBriefPolicy {
    const val LATE_LIMIT_MINUTES = 3 * 60
    private const val MINUTES_PER_DAY = 24 * 60

    fun isValidMinute(minute: Int): Boolean = minute in 0 until MINUTES_PER_DAY

    /** The day whose brief is due at [now], or null when none is. / [now] 时应发的那一天的简报日期；不该发时为 null。 */
    fun dueDay(briefMinute: Int, now: ZonedDateTime, lastSent: LocalDate?): LocalDate? {
        if (!isValidMinute(briefMinute)) return null
        val minute = now.hour * 60 + now.minute
        // The window ends at midnight at the latest, so a late brief time never spills into the next day.
        // 时间窗最晚到午夜结束，设得很晚的简报时间也不会跨到第二天。
        if (minute < briefMinute || minute >= minOf(briefMinute + LATE_LIMIT_MINUTES, MINUTES_PER_DAY)) return null
        return now.toLocalDate().takeIf { it != lastSent }
    }

    /**
     * Whether the brief wants fresh data in this background round: from [LEAD_MINUTES] before the chosen time until it has
     * gone out (or the day is given up). Outside that window nothing is fetched just for the brief. The lead-up to a
     * brief just after midnight starts the evening before. While no module has a line the brief keeps asking until the
     * day is given up, because a later round can still send it; each source's own cadence bounds what that costs.
     * 这一轮后台刷新里简报是否需要新数据：从设定时间前 [LEAD_MINUTES] 分钟起，到简报发出（或当天放弃）为止。这个时间窗之外不会
     * 专门为简报去取数据。设在午夜刚过的简报，提前量从前一天晚上算起。所有模块都没有内容时，简报会一直要数据到当天放弃为止，
     * 因为稍后的一轮仍可能发得出去；代价以每个来源自己的刷新间隔为上限。
     */
    fun needsData(briefMinute: Int, now: ZonedDateTime, lastSent: LocalDate?): Boolean {
        if (!isValidMinute(briefMinute)) return false
        if (dueDay(briefMinute, now, lastSent) != null) return true
        val minute = now.hour * 60 + now.minute
        if (Math.floorMod(briefMinute - minute, MINUTES_PER_DAY) !in 1..LEAD_MINUTES) return false
        val briefDay = if (minute < briefMinute) now.toLocalDate() else now.toLocalDate().plusDays(1)
        return briefDay != lastSent
    }

    /** Two background rounds before the brief, so one failed fetch does not leave a line out. / 简报前留出两轮后台刷新，一次抓取失败不至于少一行。 */
    const val LEAD_MINUTES = 45
}

/**
 * The morning brief: one line from each enabled module with a [BriefContributor], in module order. When no module has
 * anything to say the day is not used up, so a later round can still send it.
 * 早间简报：每个提供 [BriefContributor] 的已开启模块一行，按模块顺序。没有任何模块有话说时不算发过，稍后的一轮仍可以发。
 */
class MorningBriefRule(
    private val enabled: suspend () -> Boolean,
    private val briefMinute: suspend () -> Int,
    private val contributors: suspend () -> List<BriefContributor>,
    private val onError: (Exception) -> Unit = {},
) : NotificationRule<LocalDate?> {

    override val name = "morning_brief"

    override val codec: StateCodec<LocalDate?> = LocalDateStateCodec

    override suspend fun isEnabled(): Boolean = enabled()

    override suspend fun evaluate(input: RuleInput, previous: LocalDate?): RuleDecision<LocalDate?> {
        val day = MorningBriefPolicy.dueDay(briefMinute(), input.now, previous) ?: return RuleDecision(previous)
        val lines = contributors().mapNotNull { contributor ->
            // One module failing drops its own line, not the brief. / 某个模块出错只少它那一行，不影响整条简报。
            try {
                contributor.line(input.now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
                null
            }
        }
        if (lines.isEmpty()) return RuleDecision(previous)
        val notification = AppNotification(
            id = CoreNotifications.BRIEF_ID,
            channelId = CoreNotifications.BRIEF_CHANNEL_ID,
            title = UiText.Res(R.string.brief_title),
            body = UiText.Lines(lines),
            deepLink = CoreNotifications.BRIEF_DEEP_LINK,
            // Each day's brief is news, even when yesterday's was never dismissed. / 每天的简报都是新消息，即使昨天那条没划掉。
            alertOnce = false,
        )
        return RuleDecision(day, listOf(notification))
    }
}
