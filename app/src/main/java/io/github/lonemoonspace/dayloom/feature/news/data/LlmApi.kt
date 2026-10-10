package io.github.lonemoonspace.dayloom.feature.news.data

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.json.AppJson
import io.github.lonemoonspace.dayloom.core.network.decodeOrBadData
import io.github.lonemoonspace.dayloom.core.network.executeOrAppError
import io.github.lonemoonspace.dayloom.feature.news.domain.SummaryPrompt
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Any OpenAI-compatible chat completions endpoint the user configures (`<base>/chat/completions`, bearer key). Models can
 * take a while to answer, so this call gets a longer read timeout than the shared client's.
 * 用户配置的任意兼容 OpenAI 的对话补全接口（`<base>/chat/completions`，Bearer Key）。模型回答可能较慢，所以读取超时比共享客户端的长。
 */
class LlmApi(http: OkHttpClient, private val io: CoroutineContext = Dispatchers.IO) {
    private val client = http.newBuilder().readTimeout(90, TimeUnit.SECONDS).build()

    suspend fun summarize(baseUrl: String, apiKey: String, model: String, title: String, text: String, language: String): String =
        withContext(io) {
            val url = "$baseUrl/chat/completions".toHttpUrlOrNull() ?: throw AppError.BadData(SERVICE, "invalid server address")
            val payload = buildJsonObject {
                put("model", model)
                put("temperature", 0.3)
                // Some compatible servers stream by default; the answer is read as one JSON body.
                // 有些兼容接口默认流式返回；这里按一个完整的 JSON 读取回答。
                put("stream", false)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", SummaryPrompt.system(language))
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", SummaryPrompt.user(title, text))
                    }
                }
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody(JSON))
                .build()
            client.newCall(request).executeOrAppError().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) throw AppError.Http(SERVICE, response.code, body.take(200))
                val answer = decodeOrBadData(SERVICE, "chat completion") { AppJson.standard.decodeFromString(CompletionDto.serializer(), body) }
                answer.choices.firstOrNull()?.message?.content?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw AppError.BadData(SERVICE, "empty answer")
            }
        }

    companion object {
        const val SERVICE = "AI summary"
        private val JSON = "application/json".toMediaType()
    }
}

@Serializable
internal data class CompletionDto(val choices: List<Choice> = emptyList()) {
    @Serializable
    data class Choice(val message: Message = Message())

    @Serializable
    data class Message(val content: String? = null)
}
