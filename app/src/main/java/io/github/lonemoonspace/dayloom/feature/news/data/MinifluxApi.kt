package io.github.lonemoonspace.dayloom.feature.news.data

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.json.AppJson
import io.github.lonemoonspace.dayloom.core.network.decodeOrBadData
import io.github.lonemoonspace.dayloom.core.network.executeOrAppError
import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.RemoteEntry
import io.github.lonemoonspace.dayloom.feature.news.domain.RemoteList
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

    suspend fun unread(baseUrl: String, token: String): RemoteList = entries(baseUrl, token, "status" to "unread")

    suspend fun starred(baseUrl: String, token: String): RemoteList = entries(baseUrl, token, "starred" to "true")

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

    /**
     * The original article's full text as Miniflux scrapes it. `update_content` makes Miniflux keep it too, so later syncs
     * send the full text instead of the feed's excerpt.
     * Miniflux 抓取的原文全文。`update_content` 让 Miniflux 同时保存它，之后的同步就会发来全文而不是订阅源的摘要。
     */
    suspend fun fetchContent(baseUrl: String, token: String, id: Long): String = withContext(io) {
        val url = url(baseUrl, "v1/entries/$id/fetch-content").newBuilder().addQueryParameter("update_content", "true").build()
        http.newCall(Request.Builder().url(url).header(TOKEN_HEADER, token).build()).executeOrAppError().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw AppError.Http(SERVICE, response.code, body.take(200))
            decodeOrBadData(SERVICE, "content response") { AppJson.standard.decodeFromString(ContentDto.serializer(), body) }.content
        }
    }

    /**
     * Pages through the entries until the server has none left or [NewsSyncPolicy.FETCH_LIMIT] is reached. An entry published
     * while paging shifts the pages by one, so the same entry may come twice; it is kept once. The list is complete only
     * when the server said it had no more, never judged by how many entries were kept.
     * 逐页取条目，直到服务器没有更多或达到 [NewsSyncPolicy.FETCH_LIMIT]。翻页期间新发布的条目会让后面的页错开一条，同一条目可能出现
     * 两次，只保留一次。只有服务器表明没有更多时列表才算完整，绝不按保留下来的条数判断。
     */
    private suspend fun entries(baseUrl: String, token: String, filter: Pair<String, String>): RemoteList {
        val all = LinkedHashMap<Long, RemoteEntry>()
        var offset = 0
        while (offset < NewsSyncPolicy.FETCH_LIMIT) {
            val limit = minOf(NewsSyncPolicy.PAGE_SIZE, NewsSyncPolicy.FETCH_LIMIT - offset)
            val page = page(baseUrl, token, filter, offset, limit)
            page.entries.forEach { all.putIfAbsent(it.id, it.toRemote()) }
            offset += page.entries.size
            // A short page or reaching `total` only means there is no next page. The list is complete only when the distinct
            // entries add up to `total`: entries shifting between pages, or a page cut short by a proxy, would otherwise let
            // missing articles look read. Without `total`, a short page is taken as the end.
            // 不满一页或到达 `total` 只说明没有下一页了。只有去重后的条数够 `total` 才算完整：翻页时条目移动、或代理截断了一页，
            // 否则会让缺的文章看起来已读。没有 `total` 时，不满一页就视为到头。
            if (page.entries.size < limit || (page.total > 0 && offset >= page.total)) {
                return RemoteList(all.values.toList(), complete = page.total <= 0 || all.size >= page.total)
            }
        }
        return RemoteList(all.values.toList(), complete = false)
    }

    private suspend fun page(baseUrl: String, token: String, filter: Pair<String, String>, offset: Int, limit: Int): EntriesDto = withContext(io) {
        val url = url(baseUrl, "v1/entries").newBuilder()
            .addQueryParameter(filter.first, filter.second)
            .addQueryParameter("limit", limit.toString())
            .apply { if (offset > 0) addQueryParameter("offset", offset.toString()) }
            .addQueryParameter("order", "published_at")
            .addQueryParameter("direction", "desc")
            .build()
        val request = Request.Builder().url(url).header(TOKEN_HEADER, token).build()
        http.newCall(request).executeOrAppError().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw AppError.Http(SERVICE, response.code, body.take(200))
            decodeOrBadData(SERVICE, "entries response") { AppJson.standard.decodeFromString(EntriesDto.serializer(), body) }
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

@Serializable
internal data class ContentDto(val content: String = "")
