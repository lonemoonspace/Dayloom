package io.github.lonemoonspace.dayloom.core.refresh

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.module.ModuleIds
import java.time.Instant

/**
 * `<moduleId>.<name>`, e.g. `weather.forecast`; also names the snapshot file. Frozen from v1.0.0.
 * `<模块id>.<名称>`，如 `weather.forecast`；也决定快照文件名。从 v1.0.0 起冻结。
 */
@JvmInline
value class SourceId(val value: String) {
    init {
        require(ModuleIds.SCOPED_NAME.matches(value)) { "invalid source id: $value" }
    }

    val moduleId: String get() = value.substringBefore('.')

    /** `snapshot_weather_forecast`. */
    val snapshotFileName: String get() = "snapshot_" + value.replace('.', '_')

    override fun toString(): String = value
}

enum class Trigger {
    /**
     * The user asked: goes out even when the device looks offline, because connectivity checks misjudge and the user must be able to retry.
     * 用户主动刷新：即使看起来离线也照样发请求，因为联网判断会误判，用户必须能自己重试。
     */
    USER,
    AUTO,

    /** Inputs of a source changed (settings, credentials). / 来源的输入变了（设置、凭据）。 */
    INPUTS_CHANGED,

    /** Background worker: silent, never shows a spinner or records failures. / 后台任务：静默，不显示转圈、不记录失败。 */
    BACKGROUND,

    /**
     * Polling while a screen is open (home cards, live scores): silent like [BACKGROUND].
     * 页面打开期间的轮询（首页卡片、实时比分）：与 [BACKGROUND] 一样静默。
     */
    LIVE_POLL,
    ;

    val visible: Boolean get() = this != BACKGROUND && this != LIVE_POLL
}

/**
 * What a source needs before it can fetch. [Ready.key] fingerprints the parameters (a snapshot only matches the same key);
 * [Ready.refreshKey] is compared to decide whether a change should trigger a refresh, and may include more than [Ready.key]
 * (e.g. a credential fingerprint — never the credential itself).
 * 来源抓取前需要的输入。[Ready.key] 是参数指纹（快照只匹配相同的 key）；[Ready.refreshKey] 用来判断变化是否该触发刷新，
 * 可以比 [Ready.key] 包含更多（例如凭据指纹——绝不能是凭据本身）。
 */
sealed interface SourceInput<out P> {
    data class Ready<P>(val params: P, val key: String, val refreshKey: Any? = key) : SourceInput<P>

    /** [what] names what is missing, e.g. "Home location". / [what] 是缺少的内容，如「家的位置」。 */
    data class Missing(val what: UiText) : SourceInput<Nothing>
}

sealed interface SourceResult {
    data class Success(val fetchedAt: Instant) : SourceResult
    data class Failed(val error: AppError) : SourceResult
    data class Skipped(val reason: SkipReason) : SourceResult
}

enum class SkipReason { OFFLINE, NOT_CONFIGURED }

data class RefreshReport(val results: Map<SourceId, SourceResult>, val at: Instant) {
    fun succeeded(id: SourceId): Boolean = results[id] is SourceResult.Success
}

data class SourceStatus(val refreshing: Boolean = false, val lastError: AppError? = null)
