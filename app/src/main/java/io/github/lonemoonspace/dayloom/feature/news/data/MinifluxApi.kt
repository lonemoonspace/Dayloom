package io.github.lonemoonspace.dayloom.feature.news.data

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.json.AppJson
import io.github.lonemoonspace.dayloom.core.network.decodeOrBadData
import io.github.lonemoonspace.dayloom.core.network.executeOrAppError
import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.RemoteEntry
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The user's own Miniflux server (API v1), authenticated with an API token in `X-Auth-Token`. [baseUrl] is what the user
 * typed, already normalized to https.
 * 用户自己的 Miniflux 服务器（API v1），用 `X-Auth-Token` 头里的 API 令牌认证。[baseUrl] 是用户输入并已规范为 https 的地址。
 */
class MinifluxApi(private val http: OkHttpClient, private val io: CoroutineContext = Dispatchers.IO) {

    suspend fun unread(baseUrl: String, token: String): List<RemoteEntry> = entries(baseUrl, token, "status" to "unread")

    suspend fun starred(baseUrl: String, token: String): List<RemoteEntry> = entries(baseUrl, token, "starred" to "true")

    suspend fun setStatus(baseUrl: String, token: String, ids: List<Long>, read: Boolean) = withContext(io) {
        if (ids.isEmpty()) return@withContext
        val body = buildJsonObject {
            putJsonArray("entry_ids") { ids.forEach { add(it) } }
            put("status", if (read) "read" else "unread")
        }
        send(token, Request.Builder().url(url(baseUrl, "v1/entries")).put(body.toString().toRequestBody(JSON)))
    }

    /** Miniflux's bookmark call toggles the star. / Miniflux 的收藏接口是切换式的。 */
    suspend fun toggleStar(baseUrl: String, token: String, id: Long) = withContext(io) {
        send(token, Request.Builder().url(url(baseUrl, "v1/entries/$id/bookmark")).put(ByteArray(0).toRequestBody(null)))
    }

    private suspend fun entries(baseUrl: String, token: String, filter: Pair<String, String>): List<RemoteEntry> = withContext(io) {
        val url = url(baseUrl, "v1/entries").newBuilder()
            .addQueryParameter(filter.first, filter.second)
            .addQueryParameter("limit", NewsSyncPolicy.FETCH_LIMIT.toString())
            .addQueryParameter("order", "published_at")
            .addQueryParameter("direction", "desc")
            .build()
        val request = Request.Builder().url(url).header(TOKEN_HEADER, token).build()
        http.newCall(request).executeOrAppError().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw AppError.Http(SERVICE, response.code, body.take(200))
            decodeOrBadData(SERVICE, "entries response") { AppJson.standard.decodeFromString(EntriesDto.serializer(), body) }
                .entries.map { it.toRemote() }
        }
    }

    private fun send(token: String, builder: Request.Builder) {
        http.newCall(builder.header(TOKEN_HEADER, token).build()).executeOrAppError().use { response ->
            if (!response.isSuccessful) throw AppError.Http(SERVICE, response.code, response.body.string().take(200))
        }
    }

    private fun url(baseUrl: String, path: String): HttpUrl =
        "$baseUrl/$path".toHttpUrlOrNull() ?: throw AppError.BadData(SERVICE, "invalid server address")

    companion object {
        const val SERVICE = "Miniflux"
        const val TOKEN_HEADER = "X-Auth-Token"
        private val JSON = "application/json".toMediaType()

        internal fun parseMillis(iso: String?): Long = try {
            iso?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() } ?: 0
        } catch (_: DateTimeParseException) {
            0
        }
    }
}

@Serializable
internal data class EntriesDto(val total: Int = 0, val entries: List<EntryDto> = emptyList())

@Serializable
internal data class EntryDto(
    val id: Long = 0,
    val status: String = "",
    val title: String = "",
    val url: String = "",
    val author: String = "",
    val content: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val starred: Boolean = false,
    @SerialName("reading_time") val readingTime: Int = 0,
    val feed: FeedDto = FeedDto(),
) {
    fun toRemote() = RemoteEntry(
        id = id,
        feedTitle = feed.title,
        title = title,
        url = url,
        author = author,
        contentHtml = content,
        publishedAt = MinifluxApi.parseMillis(publishedAt),
        read = status == "read",
        starred = starred,
        readingMinutes = readingTime,
    )
}

@Serializable
internal data class FeedDto(val title: String = "")
