package io.github.lonemoonspace.dayloom.feature.news

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.json.AppJson
import io.github.lonemoonspace.dayloom.feature.news.data.LlmApi
import io.github.lonemoonspace.dayloom.feature.news.data.MinifluxApi
import io.github.lonemoonspace.dayloom.feature.news.data.NewsSync
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleFilter
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleOwner
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleStore
import io.github.lonemoonspace.dayloom.feature.news.domain.MergeResult
import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.StoredArticle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NewsApiTest {

    private lateinit var server: MockWebServer
    private val base get() = server.url("/reader").toString().trimEnd('/')

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Full-length bodies by default, so a sync fetches no full text; [excerpt] sends feed excerpts instead. / 默认是完整正文，同步时不抓全文；[excerpt] 改发订阅源摘要。 */
    private fun entries(vararg ids: Long, starred: Boolean = false, excerpt: Boolean = false) = """{"total":${ids.size},"entries":[""" +
        ids.joinToString(",") {
            val body = if (excerpt) "B$it" else "Body $it. ".repeat(20)
            """{"id":$it,"status":"unread","title":"T$it","url":"https://example.org/$it","content":"<p>$body</p>",
               "published_at":"2026-10-09T08:00:00+02:00","starred":$starred,"reading_time":4,"feed":{"title":"Feed"}}"""
        } + "]}"

    @Test
    fun `entries are fetched with the token header, newest first, limited`() = runTest {
        server.enqueue(MockResponse().setBody(entries(7)))
        val list = MinifluxApi(OkHttpClient()).unread(base, "tok")
        val request = server.takeRequest()
        assertEquals("tok", request.getHeader("X-Auth-Token"))
        assertEquals("/reader/v1/entries?status=unread&limit=200&order=published_at&direction=desc", request.path)
        assertTrue(list.complete)
        val entry = list.entries.single()
        assertEquals("Feed", entry.feedTitle)
        assertEquals(4, entry.readingMinutes)
        assertEquals(java.time.Instant.parse("2026-10-09T06:00:00Z").toEpochMilli(), entry.publishedAt)
    }

    @Test
    fun `status changes go in one request and stars toggle per entry`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        val api = MinifluxApi(OkHttpClient())
        api.setStatus(base, "tok", listOf(1, 2), read = true)
        api.toggleStar(base, "tok", 3)
        val status = server.takeRequest()
        assertEquals("PUT", status.method)
        val body = AppJson.standard.parseToJsonElement(status.body.readUtf8()).jsonObject
        assertEquals(listOf("1", "2"), body.getValue("entry_ids").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("read", body.getValue("status").jsonPrimitive.content)
        assertEquals("/reader/v1/entries/3/bookmark", server.takeRequest().path)
    }

    @Test
    fun `the summary prompt caps the title like the text`() {
        val prompt = io.github.lonemoonspace.dayloom.feature.news.domain.SummaryPrompt.user("x".repeat(5_000), "body")
        assertEquals(io.github.lonemoonspace.dayloom.feature.news.domain.SummaryPrompt.TITLE_CHARS + "\n\nbody".length, prompt.length)
    }

    @Test
    fun `the summarizer gets the app language's prompt and a bearer key`() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"  • Point  "}}]}"""))
        val answer = LlmApi(OkHttpClient()).summarize(base, "key", "model-x", "Title", "Text", "zh")
        assertEquals("• Point", answer)
        val request = server.takeRequest()
        assertEquals("/reader/chat/completions", request.path)
        assertEquals("Bearer key", request.getHeader("Authorization"))
        val body = AppJson.standard.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("model-x", body.getValue("model").jsonPrimitive.content)
        val system = body.getValue("messages").jsonArray.first().jsonObject.getValue("content").jsonPrimitive.content
        assertTrue(system.contains("简体中文"))
        assertEquals("one JSON answer, not a stream", "false", body.getValue("stream").jsonPrimitive.content)

        server.enqueue(MockResponse().setBody("""{"choices":[]}"""))
        assertTrue(runCatching { LlmApi(OkHttpClient()).summarize(base, "key", "m", "T", "X", "en") }.exceptionOrNull() is AppError.BadData)
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":null}}]}"""))
        assertTrue(runCatching { LlmApi(OkHttpClient()).summarize(base, "key", "m", "T", "X", "en") }.exceptionOrNull() is AppError.BadData)
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        assertEquals(401, (runCatching { LlmApi(OkHttpClient()).summarize(base, "key", "m", "T", "X", "en") }.exceptionOrNull() as AppError.Http).code)
    }

    private class MemoryOwner(var value: String = "") : ArticleOwner {
        override suspend fun get() = value
        override suspend fun set(baseUrl: String) {
            value = baseUrl
        }
    }

    private class MemoryStore : ArticleStore {
        val rows = MutableStateFlow<Map<Long, StoredArticle>>(emptyMap())
        override suspend fun all() = rows.value.values.toList()
        override suspend fun pending() = all().filter { it.pendingRead != null || it.pendingStarred != null }
        override suspend fun summary() = io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy.summary(all())
        override suspend fun clear() {
            rows.value = emptyMap()
        }
        override suspend fun markFullTextTried(id: Long, at: Long) = edit(id) { it.copy(fullTextTriedAt = at) }
        override suspend fun apply(result: MergeResult) {
            rows.value = rows.value - result.delete.toSet() + result.upsert.associateBy { it.id }
        }
        override fun observe(filter: ArticleFilter): Flow<List<StoredArticle>> = rows.map { it.values.toList() }
        override fun observe(id: Long): Flow<StoredArticle?> = rows.map { it[id] }
        override fun observeSummary(): Flow<io.github.lonemoonspace.dayloom.feature.news.domain.SyncSummary> =
            rows.map { io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy.summary(it.values.toList()) }
        override suspend fun markRead(id: Long, read: Boolean, readAt: Long) = edit(id) { it.copy(read = read, pendingRead = read, readAt = readAt) }
        override suspend fun markStarred(id: Long, starred: Boolean) = edit(id) { it.copy(starred = starred, pendingStarred = starred) }
        override suspend fun saveSummary(id: Long, summary: String, language: String) = edit(id) { it.copy(summary = summary, summaryLanguage = language) }
        override suspend fun saveContent(id: Long, html: String) = edit(id) { it.copy(contentHtml = html) }
        private fun edit(id: Long, f: (StoredArticle) -> StoredArticle) {
            rows.value[id]?.let { rows.value = rows.value + (id to f(it)) }
        }
    }

    @Test
    fun `excerpts get their full text after a sync, and a failure waits before the next try`() = runTest {
        val store = MemoryStore()
        val sync = NewsSync(store, MinifluxApi(OkHttpClient()), MemoryOwner())
        val full = "<p>" + "Full text. ".repeat(20) + "</p>"
        server.enqueue(MockResponse().setBody(entries(1, 2, excerpt = true)))
        server.enqueue(MockResponse().setBody(entries()))
        // Newest first; both were published at the same time, so either order is fine. / 新的在前；两篇发布时间相同，顺序不限。
        server.enqueue(MockResponse().setBody("""{"content":${kotlinx.serialization.json.JsonPrimitive(full)}}"""))
        server.enqueue(MockResponse().setResponseCode(502))
        val now = java.time.Instant.parse("2026-10-09T12:00:00Z").toEpochMilli()

        sync.sync(base, "tok", nowMillis = now)

        val requests = (1..4).map { server.takeRequest() }
        assertTrue(requests.drop(2).all { it.path!!.contains("/fetch-content?update_content=true") })
        val fetched = requests[2].path!!.substringAfter("entries/").substringBefore("/").toLong()
        assertEquals(full, store.all().single { it.id == fetched }.contentHtml)
        val failed = store.all().single { it.id != fetched }
        assertEquals("the excerpt stays", "<p>B${failed.id}</p>", failed.contentHtml)

        // Within the retry wait only the downloads go out. / 等待期内只有下载请求。
        server.enqueue(MockResponse().setBody(entries(1, 2, excerpt = true)))
        server.enqueue(MockResponse().setBody(entries()))
        sync.sync(base, "tok", nowMillis = now + 60_000)
        assertEquals(6, server.requestCount)
        assertEquals("the fetched full text survives an excerpt-only sync", full, store.all().single { it.id == fetched }.contentHtml)
    }

    @Test
    fun `a sync downloads, pushes local changes and keeps them pending when the push fails`() = runTest {
        val store = MemoryStore()
        val sync = NewsSync(store, MinifluxApi(OkHttpClient()), MemoryOwner())
        server.enqueue(MockResponse().setBody(entries(1, 2)))
        server.enqueue(MockResponse().setBody(entries()))
        val first = sync.sync(base, "tok", nowMillis = java.time.Instant.parse("2026-10-09T12:00:00Z").toEpochMilli())
        assertEquals(2, first.unread)

        sync.markRead(1, true, nowMillis = 0)
        sync.markStarred(2, true)
        server.enqueue(MockResponse().setBody(entries(1, 2)))
        server.enqueue(MockResponse().setBody(entries()))
        server.enqueue(MockResponse().setResponseCode(500))
        sync.sync(base, "tok", nowMillis = java.time.Instant.parse("2026-10-09T12:05:00Z").toEpochMilli())
        assertEquals("first sync: two downloads", listOf("GET", "GET"), (1..2).map { server.takeRequest().method })
        assertEquals("second sync: two downloads, then the failed push", listOf("GET", "GET", "PUT"), (1..3).map { server.takeRequest().method })
        assertEquals(true, store.rows.value.getValue(1).pendingRead)
        assertTrue("still read here", store.rows.value.getValue(1).read)

        server.enqueue(MockResponse().setBody(entries(2)))
        server.enqueue(MockResponse().setBody(entries()))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        sync.sync(base, "tok", nowMillis = java.time.Instant.parse("2026-10-09T12:10:00Z").toEpochMilli())
        assertNull(store.rows.value.getValue(1).pendingRead)
        assertNull(store.rows.value.getValue(2).pendingStarred)
        assertTrue(store.rows.value.getValue(2).starred)
    }

    @Test
    fun `read states go out right away, once, and stay pending until a sync confirms them`() = runTest {
        val store = MemoryStore()
        val sync = NewsSync(store, MinifluxApi(OkHttpClient()), MemoryOwner())
        server.enqueue(MockResponse().setBody(entries(1)))
        server.enqueue(MockResponse().setBody(entries()))
        sync.sync(base, "tok", nowMillis = 0)
        repeat(2) { server.takeRequest() }

        sync.markRead(1, true, nowMillis = 5)
        server.enqueue(MockResponse().setResponseCode(204))
        sync.pushReadStates(base, "tok")
        assertEquals("PUT", server.takeRequest().method)
        sync.pushReadStates(base, "tok")
        assertEquals("nothing new to send", 1 + 2, server.requestCount)
        assertEquals(true, store.rows.value.getValue(1).pendingRead)
    }

    @Test
    fun `a sync with another server drops the old articles and never sends their changes there`() = runTest {
        val store = MemoryStore()
        val owner = MemoryOwner()
        val sync = NewsSync(store, MinifluxApi(OkHttpClient()), owner)
        server.enqueue(MockResponse().setBody(entries(1, 2)))
        server.enqueue(MockResponse().setBody(entries()))
        sync.sync(base, "tok", nowMillis = 0)
        assertEquals(base, owner.value)
        sync.markRead(1, true, nowMillis = 5)
        repeat(2) { server.takeRequest() }

        val other = server.url("/other").toString().trimEnd('/')
        sync.pushReadStates(other, "tok")
        assertEquals("nothing goes to a server the articles did not come from", 2, server.requestCount)

        server.enqueue(MockResponse().setBody(entries(7)))
        server.enqueue(MockResponse().setBody(entries()))
        val summary = sync.sync(other, "tok", nowMillis = 10)
        assertEquals("only downloads, no push of the old read state", listOf("GET", "GET"), (1..2).map { server.takeRequest().method })
        assertEquals(setOf(7L), store.rows.value.keys)
        assertEquals(other, owner.value)
        assertEquals(1, summary.unread)
    }

    @Test
    fun `a server that does not answer keeps the articles of the one before`() = runTest {
        val store = MemoryStore()
        val owner = MemoryOwner()
        val sync = NewsSync(store, MinifluxApi(OkHttpClient()), owner)
        server.enqueue(MockResponse().setBody(entries(1, 2)))
        server.enqueue(MockResponse().setBody(entries()))
        sync.sync(base, "tok", nowMillis = 0)
        sync.markRead(1, true, nowMillis = 5)

        val other = server.url("/other").toString().trimEnd('/')
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(runCatching { sync.sync(other, "bad", nowMillis = 10) }.exceptionOrNull() is AppError.Http)
        server.enqueue(MockResponse().setBody("<html>Not Miniflux</html>"))
        assertTrue(runCatching { sync.sync(other, "tok", nowMillis = 20) }.exceptionOrNull() is AppError.BadData)

        assertEquals(base, owner.value)
        assertEquals(setOf(1L, 2L), store.rows.value.keys)
        assertEquals("the unsent change survives too", true, store.rows.value.getValue(1).pendingRead)
    }

    @Test
    fun `entries are fetched page by page up to the limit, each entry once`() = runTest {
        val total = NewsSyncPolicy.PAGE_SIZE + 3
        fun page(ids: LongRange) = entries(*ids.toList().toLongArray()).replaceFirst("\"total\":${ids.count()}", "\"total\":$total")
        server.enqueue(MockResponse().setBody(page(1L..NewsSyncPolicy.PAGE_SIZE)))
        // A new entry shifted the second page by one, so its first entry repeats. / 新条目让第二页错开一条，第一条重复出现。
        server.enqueue(MockResponse().setBody(page(NewsSyncPolicy.PAGE_SIZE.toLong()..total.toLong())))
        val list = MinifluxApi(OkHttpClient()).unread(base, "tok")
        assertEquals((1L..total).toList(), list.entries.map { it.id })
        assertTrue(list.complete)
        server.takeRequest()
        assertEquals(
            "/reader/v1/entries?status=unread&limit=${NewsSyncPolicy.PAGE_SIZE}&offset=${NewsSyncPolicy.PAGE_SIZE}&order=published_at&direction=desc",
            server.takeRequest().path,
        )
        assertEquals("stops once the server has no more", 2, server.requestCount)
    }

    @Test
    fun `a short page that falls below the stated total leaves the list incomplete, without a total it ends the list`() = runTest {
        server.enqueue(MockResponse().setBody(entries(1, 2, 3).replaceFirst("\"total\":3", "\"total\":500")))
        val cut = MinifluxApi(OkHttpClient()).unread(base, "tok")
        assertEquals(listOf(1L, 2L, 3L), cut.entries.map { it.id })
        assertFalse(cut.complete)
        assertEquals(1, server.requestCount)

        server.enqueue(MockResponse().setBody(entries(1, 2, 3).replaceFirst("\"total\":3", "\"total\":0")))
        assertTrue(MinifluxApi(OkHttpClient()).unread(base, "tok").complete)
    }

    @Test
    fun `a list whose distinct entries fall short of the total is incomplete`() = runTest {
        // 203 entries; while paging, the second page comes back short and repeats two, under a total that grew to 204.
        // 共 203 条；翻页期间第二页不满一页、重复两条，而 total 涨到了 204。
        server.enqueue(MockResponse().setBody(entries(*(1L..200L).toList().toLongArray()).replaceFirst("\"total\":200", "\"total\":203")))
        server.enqueue(MockResponse().setBody(entries(199, 200, 201, 202).replaceFirst("\"total\":4", "\"total\":204")))
        val list = MinifluxApi(OkHttpClient()).unread(base, "tok")
        assertEquals(202, list.entries.size)
        assertFalse(list.complete)
    }

    @Test
    fun `a list cut off at the limit is incomplete even when a repeated entry leaves it short`() = runTest {
        val size = NewsSyncPolicy.PAGE_SIZE.toLong()
        val pages = (NewsSyncPolicy.FETCH_LIMIT / NewsSyncPolicy.PAGE_SIZE)
        fun page(ids: LongRange) = entries(*ids.toList().toLongArray()).replaceFirst("\"total\":${ids.count()}", "\"total\":${NewsSyncPolicy.FETCH_LIMIT + 50}")
        // Every page after the first starts with the previous page's last entry. / 第一页之后每页都以前一页的最后一条开头。
        for (i in 0 until pages) server.enqueue(MockResponse().setBody(page(i * (size - 1) + 1..i * (size - 1) + size)))
        val list = MinifluxApi(OkHttpClient()).unread(base, "tok")
        assertEquals(NewsSyncPolicy.FETCH_LIMIT - (pages - 1), list.entries.size)
        assertFalse("the server has more, so nothing may be taken as read elsewhere", list.complete)
        assertEquals(pages, server.requestCount)
    }

    @Test
    fun `a refused push is reported and the change stays pending`() = runTest {
        val store = MemoryStore()
        val sync = NewsSync(store, MinifluxApi(OkHttpClient()), MemoryOwner())
        server.enqueue(MockResponse().setBody(entries(1)))
        server.enqueue(MockResponse().setBody(entries()))
        assertFalse(sync.sync(base, "tok", nowMillis = 0).pushRejected)
        sync.markRead(1, true, nowMillis = 5)
        server.enqueue(MockResponse().setBody(entries(1)))
        server.enqueue(MockResponse().setBody(entries()))
        server.enqueue(MockResponse().setResponseCode(403))
        assertTrue(sync.sync(base, "tok", nowMillis = 10).pushRejected)
        assertEquals(true, store.rows.value.getValue(1).pendingRead)
    }

    @Test
    fun `an article that is short on the server too is not fetched again`() = runTest {
        val store = MemoryStore()
        val sync = NewsSync(store, MinifluxApi(OkHttpClient()), MemoryOwner())
        server.enqueue(MockResponse().setBody(entries(1, excerpt = true)))
        server.enqueue(MockResponse().setBody(entries()))
        server.enqueue(MockResponse().setBody("""{"content":"<p>Short note.</p>"}"""))
        sync.sync(base, "tok", nowMillis = 0)
        assertEquals(NewsSyncPolicy.FULL_TEXT_SHORT, store.rows.value.getValue(1).fullTextTriedAt)
        server.enqueue(MockResponse().setBody(entries(1, excerpt = true)))
        server.enqueue(MockResponse().setBody(entries()))
        sync.sync(base, "tok", nowMillis = 2 * 60 * 60_000L)
        assertEquals("downloads only, long after the retry wait", 5, server.requestCount)
    }
}
