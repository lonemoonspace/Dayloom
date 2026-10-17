package io.github.lonemoonspace.dayloom.core.notify

import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.refresh.RefreshReport
import java.time.ZonedDateTime

/**
 * Persists a rule's state as a string. [decode] must fall back to the initial state on corrupt input.
 * 把规则状态存成字符串。[decode] 遇到损坏内容必须回退为初始状态。
 */
interface StateCodec<S> {
    fun decode(raw: String?): S
    fun encode(state: S): String
}

/**
 * A notification rule owned by a module. It reads only its own module's sources and settings (the module passes them in
 * when it creates the rule); there is no global view of other modules' data.
 * 某个模块拥有的通知规则。它只读本模块的来源与设置（模块创建规则时传入），没有其他模块数据的全局视图。
 */
interface NotificationRule<S> {
    /** Local state name; stored as `<moduleId>.<name>`. Frozen from v1.0.0. / 本地状态名，存为 `<模块id>.<名称>`。从 v1.0.0 起冻结。 */
    val name: String
    val codec: StateCodec<S>

    /** Usually the module's opt-in setting. / 通常是模块里的「选择加入」开关。 */
    suspend fun isEnabled(): Boolean

    suspend fun evaluate(input: RuleInput, previous: S): RuleDecision<S>
}

/**
 * The refresh round that just finished. A rule should only use sources that succeeded in [report].
 * 刚结束的这一轮刷新。规则只应使用在 [report] 里成功的来源。
 */
data class RuleInput(val report: RefreshReport, val now: ZonedDateTime)

/**
 * [cancel] lists ids whose notification no longer holds (the delay is over), taken down before [notifications] go out.
 * [cancel] 列出不再成立的通知 id（延误已经结束），在发送 [notifications] 之前撤下。
 */
data class RuleDecision<S>(
    val newState: S,
    val notifications: List<AppNotification> = emptyList(),
    val cancel: List<Int> = emptyList(),
)

/**
 * A notification ready to send. Text is [UiText] and resolved in the app language at send time.
 * 待发送的通知。文字是 [UiText]，发送时按应用语言解析。
 */
data class AppNotification(
    val id: Int,
    val channelId: String,
    val title: UiText,
    val body: UiText,
    val deepLink: String,
    /**
     * True when this notification only updates the one already shown under [id] (kick-off → final score) and must not ring
     * again. False for a new event that reuses its id, such as a reminder's next stage, which would otherwise replace the
     * earlier one silently while it is still in the shade.
     * 为 true 表示它只是更新 [id] 下已显示的通知（开赛 → 终场），不再响铃。复用 id 的新事件（如提醒的下一档）为 false，
     * 否则前一条还留在通知栏时会被静默替换。
     */
    val alertOnce: Boolean = true,
)

/** Rule state storage; keys are `<moduleId>.<rule>`. / 规则状态存储；键为 `<模块id>.<规则>`。 */
interface NotificationStateStore {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
}

interface Notifier {
    fun send(notification: AppNotification)

    /**
     * Removes a shown notification, e.g. when the thing it is about was deleted. No-op when nothing is shown under [id].
     * 撤下已显示的通知，比如它所说的东西已被删除。[id] 下没有通知时什么也不做。
     */
    fun cancel(id: Int)

    /**
     * Removes every shown notification whose id is in [ids], e.g. a module's whole range when the module is turned off.
     * 撤下 id 落在 [ids] 里的所有已显示通知，比如模块被关闭时撤下它的整个号段。
     */
    fun cancelShown(ids: IntRange)
}
