package io.github.lonemoonspace.dayloom.feature.news

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.json.AppJson
import io.github.lonemoonspace.dayloom.feature.news.data.LlmApi
import io.github.lonemoonspace.dayloom.feature.news.data.MinifluxApi
import io.github.lonemoonspace.dayloom.feature.news.data.NewsSync
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleFilter
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleStore
import io.github.lonemoonspace.dayloom.feature.news.domain.MergeResult
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

    private fun entries(vararg ids: Long, starred: Boolean = false) = """{"total":${ids.size},"entries":[""" +
        ids.joinToString(",") {
            """{"id":$it,"status":"unread","title":"T$it","url":"https://example.org/$it","content":"<p>B$it</p>",
               "published_at":"2026-10-09T08:00:00+02:00","starred":$starred,"reading_time":4,"feed":{"title":"Feed"}}"""
        } + "]}"

    @Test
    fun `entries are fetched with the token header, newest first, limited`() = runTest {
        server.enqueue(MockResponse().setBody(entries(7)))
        val list = MinifluxApi(OkHttpClient()).unread(base, "tok")
        val request = server.takeRequest()
        assertEquals("tok", request.getHeader("X-Auth-Token"))
        assertEquals("/reader/v1/entries?status=unread&limit=200&order=published_at&direction=desc", request.path)
        val entry = list.single()
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

        server.enqueue(MockResponse().setBody("""{"choices":[]}"""))
        assertTrue(runCatching { LlmApi(OkHttpClient()).summarize(base, "key", "m", "T", "X", "en") }.exceptionOrNull() is AppError.BadData)
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        assertEquals(401, (runCatching { LlmApi(OkHttpClient()).summarize(base, "key", "m", "T", "X", "en") }.exceptionOrNull() as AppError.Http).code)
    }

    private class MemoryStore : ArticleStore {
        val rows = MutableStateFlow<Map<Long, StoredArticle>>(emptyMap())
        override suspend fun all() = rows.value.values.toList()
        override suspend fun apply(result: MergeResult) {
            rows.value = rows.value - result.delete.toSet() + result.upsert.associateBy { it.id }
        }
        override fun observe(filter: ArticleFilter): Flow<List<StoredArticle>> = rows.map { it.values.toList() }
        override fun observe(id: Long): Flow<StoredArticle?> = rows.map { it[id] }
        override suspend fun markRead(id: Long, read: Boolean) = edit(id) { it.copy(read = read, pendingRead = read) }
        override suspend fun markStarred(id: Long, starred: Boolean) = edit(id) { it.copy(starred = starred, pendingStarred = starred) }
        override suspend fun confirmRead(ids: List<Long>) = ids.forEach { id -> edit(id) { it.copy(pendingRead = null) } }
        override suspend fun saveSummary(id: Long, summary: String, language: String) = edit(id) { it.copy(summary = summary, summaryLanguage = language) }
        private fun edit(id: Long, f: (StoredArticle) -> StoredArticle) {
            rows.value[id]?.let { rows.value = rows.value + (id to f(it)) }
        }
    }

    @Test
    fun `a sync downloads, pushes local changes and keeps them pending when the push fails`() = runTest {
        val store = MemoryStore()
        val sync = NewsSync(store, MinifluxApi(OkHttpClient()))
        server.enqueue(MockResponse().setBody(entries(1, 2)))
        server.enqueue(MockResponse().setBody(entries()))
        val first = sync.sync(base, "tok", nowMillis = java.time.Instant.parse("2026-10-09T12:00:00Z").toEpochMilli())
        assertEquals(2, first.unread)

        sync.markRead(1, true)
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
}
