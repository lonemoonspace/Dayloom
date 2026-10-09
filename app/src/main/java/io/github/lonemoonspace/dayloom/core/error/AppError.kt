package io.github.lonemoonspace.dayloom.core.error

import io.github.lonemoonspace.dayloom.core.i18n.UiText
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/**
 * The single error type of the data layer. User-facing text is produced only in `core/ui/ErrorText.kt`; messages here are for logs.
 * [service] is a proper name shown to the user as-is (e.g. "MET Norway"), so it is never translated.
 * 数据层统一的错误类型。面向用户的文字只在 `core/ui/ErrorText.kt` 生成；这里的 message 只供日志。
 * [service] 是原样展示给用户的专有名称（如「MET Norway」），不翻译。
 */
sealed class AppError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /**
     * An automatic refresh was skipped because the device is offline.
     * 设备离线，自动刷新被跳过。
     */
    class Offline : AppError("offline")

    class Network(cause: Throwable) : AppError(cause.message ?: "network", cause)

    /**
     * [detail] is a response excerpt for display, possibly empty.
     * [detail] 是用于展示的响应片段，可以为空。
     */
    class Http(val service: String, val code: Int, val detail: String = "") :
        AppError("$service HTTP $code" + if (detail.isNotEmpty()) ": $detail" else "")

    /**
     * Something the user still has to set up; [what] names it, e.g. "Home location".
     * 用户还需要设置的内容；[what] 是它的名称，比如「家的位置」。
     */
    class NotConfigured(val what: UiText) : AppError("not configured: $what")

    /**
     * A stored credential could not be decrypted (Keystore key lost, device restore) and must be re-entered.
     * 已存凭据无法解密（Keystore 密钥丢失、设备恢复），需要重新输入。
     */
    class SecretUnreadable(val what: UiText) : AppError("secret unreadable: $what")

    /**
     * The service answered, but with data we cannot use (parse failure, empty result).
     * 服务有响应，但数据不可用（解析失败、结果为空）。
     */
    class BadData(val service: String, detail: String, cause: Throwable? = null) : AppError(detail, cause)

    class Unexpected(cause: Throwable) : AppError(cause.message ?: cause.javaClass.simpleName, cause)
}

/**
 * Converts any failure at a data-layer boundary. [CancellationException] is rethrown so cancellation is never swallowed as an error.
 * [service] is needed to build [AppError.BadData]; without it a parse failure becomes [AppError.Unexpected].
 * 数据层边界统一转换。[CancellationException] 会被重新抛出，取消不能被当作普通错误吞掉。
 * 构造 [AppError.BadData] 需要 [service]；没有时解析失败退化为 [AppError.Unexpected]。
 */
fun Throwable.toAppError(service: String? = null): AppError = when (this) {
    is CancellationException -> throw this
    is AppError -> this
    is IOException -> AppError.Network(this)
    is SerializationException ->
        if (service != null) AppError.BadData(service, message ?: "parse error", this) else AppError.Unexpected(this)
    else -> AppError.Unexpected(this)
}
