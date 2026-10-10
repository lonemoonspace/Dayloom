package io.github.lonemoonspace.dayloom.core.storage

import kotlinx.serialization.Serializable

/**
 * App-wide settings in the `app_settings` file. Every field needs a default so older files keep decoding.
 * The UI language is not stored here: the system keeps the per-app language itself.
 * `app_settings` 文件里的全局设置。每个字段都要有默认值，旧文件才能继续解码。
 * 界面语言不存这里：按应用语言由系统自己保存。
 */
@Serializable
data class AppSettings(
    /** IANA zone id; empty = follow the device. / IANA 时区 id；空 = 跟随设备。 */
    val timeZoneOverride: String = "",
    /**
     * Explicit on/off choices per module id; a module missing here uses its own default, so modules added
     * in later versions get their default instead of "off".
     * 每个模块 id 的显式开关；不在这里的模块用自己的默认值，以后版本新增的模块因此是默认状态而不是「关闭」。
     */
    val moduleEnabled: Map<String, Boolean> = emptyMap(),
    /**
     * Home card keys (`<moduleId>.<card>`) in the user's order; unknown keys are ignored, missing ones appended.
     * 用户排好的首页卡片键（`<模块id>.<卡片>`）；未知的键忽略，缺的追加在后面。
     */
    val cardOrder: List<String> = emptyList(),
    /** Opt-in like every notification (design §7.4). / 与所有通知一样需要用户主动打开（设计文档 §7.4）。 */
    val morningBrief: Boolean = false,
    /** When the morning brief goes out, minutes after midnight. / 早间简报的发送时间，午夜起的分钟数。 */
    val morningBriefMinute: Int = 7 * 60,
    /** False until the first-run onboarding is finished or skipped. / 首次启动引导完成或跳过之前为 false。 */
    val onboardingDone: Boolean = false,
)
