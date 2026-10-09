package io.github.lonemoonspace.dayloom.core.notify

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.routine.Routine
import io.github.lonemoonspace.dayloom.core.routine.RoutinePolicy
import io.github.lonemoonspace.dayloom.core.routine.WindowKind
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
 * When the morning brief goes out: on the first background round inside the to-work window, once a day. A round
 * runs every 15 minutes, so it arrives within a quarter of an hour of the window opening, later if the phone was offline;
 * no exact alarm is worth the battery for that. Deduplicated by the day the window belongs to, not by content: the brief is
 * a daily habit even when nothing changed.
 * 早间简报的发送时机：上班时间窗内的第一次后台刷新，每天一次。后台每 15 分钟跑一轮，所以会在时间窗开始后一刻钟内送达，
 * 手机离线时顺延；为这点精度不值得耗电用精确闹钟。按时间窗所属的日期去重而不是按内容：哪怕什么都没变，简报也是每天一条。
 */
object MorningBriefPolicy {

    /** The day whose brief is due at [now], or null when none is. / [now] 时应发的那一天的简报日期；不该发时为 null。 */
    fun dueDay(routine: Routine, now: ZonedDateTime, lastSent: LocalDate?): LocalDate? {
        val window = RoutinePolicy.active(routine, now)?.takeIf { it.kind == WindowKind.TO_WORK } ?: return null
        val day = window.start.toLocalDate()
        return day.takeIf { it != lastSent }
    }
}

/**
 * The morning brief: one line from each enabled module with a [BriefContributor], in module order. When no module has
 * anything to say the day is not used up, so a later round in the same window can still send it.
 * 早间简报：每个提供 [BriefContributor] 的已开启模块一行，按模块顺序。没有任何模块有话说时不算发过，同一时间窗里稍后的
 * 一轮仍可以发。
 */
class MorningBriefRule(
    private val enabled: suspend () -> Boolean,
    private val routine: suspend () -> Routine,
    private val contributors: suspend () -> List<BriefContributor>,
    private val onError: (Exception) -> Unit = {},
) : NotificationRule<LocalDate?> {

    override val name = "morning_brief"

    override val codec: StateCodec<LocalDate?> = LocalDateStateCodec

    override suspend fun isEnabled(): Boolean = enabled()

    override suspend fun evaluate(input: RuleInput, previous: LocalDate?): RuleDecision<LocalDate?> {
        val day = MorningBriefPolicy.dueDay(routine(), input.now, previous) ?: return RuleDecision(previous)
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
        )
        return RuleDecision(day, listOf(notification))
    }
}
