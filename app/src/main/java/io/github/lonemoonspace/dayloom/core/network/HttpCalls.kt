package io.github.lonemoonspace.dayloom.core.network

import io.github.lonemoonspace.dayloom.core.error.AppError
import java.io.IOException
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.Response

/**
 * Connection and timeout failures become [AppError.Network].
 * 连接与超时等失败统一转换为 [AppError.Network]。
 */
fun Call.executeOrAppError(): Response = try {
    execute()
} catch (e: IOException) {
    throw AppError.Network(e)
}

/**
 * Response parse failures become [AppError.BadData], keeping the original exception as the cause.
 * 响应解析失败统一转换为 [AppError.BadData]，并保留原异常作为 cause。
 */
inline fun <T> decodeOrBadData(service: String, detail: String, block: () -> T): T = try {
    block()
} catch (e: SerializationException) {
    throw AppError.BadData(service, detail, e)
}
