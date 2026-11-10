package io.github.lonemoonspace.dayloom.feature.news

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.feature.news.data.FeedApi
import io.github.lonemoonspace.dayloom.feature.news.data.FeedFinder
import io.github.lonemoonspace.dayloom.feature.news.data.FeedSync
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleFilter
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleOwner
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleStore
import io.github.lonemoonspace.dayloom.feature.news.domain.Feed
import io.github.lonemoonspace.dayloom.feature.news.domain.FeedError
import io.github.lonemoonspace.dayloom.feature.news.domain.FeedFetcher
import io.github.lonemoonspace.dayloom.feature.news.domain.FeedPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.FetchResult
import io.github.lonemoonspace.dayloom.feature.news.domain.ListedArticle
import io.github.lonemoonspace.dayloom.feature.news.domain.MergeResult
import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.StoredArticle
import io.github.lonemoonspace.dayloom.feature.news.domain.SyncSummary
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedSyncTest {

    private val now = Instant.parse("2026-11-09T12:00:00Z").toEpochMilli()
    private val a = Feed("https://a.example.org/rss", "A")
    private val b = Feed("https://b.example.org/rss", "B")

    private fun rss(vararg keys: String) = (
        "<rss><channel><title>Site</title>" +
            keys.joinToString("") { "<item><guid>$it</guid><title>T $it</title><link>https://example.org/$it</link><pubDate>Mon, 09 Nov 2026 10:00:00 GMT</pubDate></item>" } +
            "</channel></rss>"
        ).toByteArray()

    private class Calls(val url: String, val etag: String, val lastModified: String)

    private class FakeFetcher(val answers: Map<String, () -> FetchResult>) : FeedFetcher {
        val calls = mutableListOf<Calls>()
        override suspend fun fetch(url: String, etag: String, lastModified: String): FetchResult {
            synchronized(calls) { calls += Calls(url, etag, lastModified) }
            return answers.getValue(url)()
        }
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
        override suspend fun summary() = NewsSyncPolicy.summary(all())
        override suspend fun clear() {
            rows.value = emptyMap()
        }
        override suspend fun markFullTextTried(id: Long, at: Long) = Unit
        override suspend fun apply(result: MergeResult) {
            val kept = (rows.value - result.delete.toSet()).mapValues { (id, a) ->
                if (id in result.clearPending) a.copy(pendingRead = null, pendingStarred = null) else a
            }
            rows.value = kept + result.upsert.associateBy { it.id }
        }
        override fun observe(filter: ArticleFilter): Flow<List<ListedArticle>> =
            rows.map { all -> all.values.map { ListedArticle(it.copy(contentHtml = ""), it.contentHtml.length) } }
        override suspend fun texts(ids: List<Long>) = ids.mapNotNull { id -> rows.value[id]?.let { id to it.contentHtml } }.toMap()
        override fun observe(id: Long): Flow<StoredArticle?> = rows.map { it[id] }
        override fun observeSummary(): Flow<SyncSummary> = rows.map { NewsSyncPolicy.summary(it.values.toList()) }
        override suspend fun markRead(id: Long, read: Boolean, readAt: Long) = Unit
        override suspend fun markStarred(id: Long, starred: Boolean) = Unit
        override suspend fun saveSummary(id: Long, summary: String, language: String) = Unit
        override suspend fun saveContent(id: Long, html: String) = Unit
    }

    @Test
    fun `one broken feed marks itself and the rest still land`() = runTest {
        val store = MemoryStore()
        val owner = MemoryOwner()
        val fetcher = FakeFetcher(
            mapOf(
                a.url to { FetchResult.Fetched(rss("1", "2"), etag = "\"e1\"") },
                b.url to { throw AppError.Http(FeedApi.SERVICE, 404) },
            ),
        )
        val summary = FeedSync(store, fetcher, owner, Mutex()).sync(listOf(a, b), emptyList(), now)
        assertEquals(2, summary.unread)
        assertEquals(FeedPolicy.OWNER, owner.value)
        val states = summary.feeds.associateBy { it.url }
        assertEquals("\"e1\"", states.getValue(a.url).etag)
        assertEquals(now, states.getValue(a.url).okAt)
        assertEquals(FeedError.HTTP, states.getValue(b.url).error)
        assertEquals(404, states.getValue(b.url).code)
    }

    @Test
    fun `a page that is not a feed is marked as such`() = runTest {
        val fetcher = FakeFetcher(mapOf(a.url to { FetchResult.Fetched("<html></html>".toByteArray()) }))
        val summary = FeedSync(MemoryStore(), fetcher, MemoryOwner(), Mutex()).sync(listOf(a), emptyList(), now)
        assertEquals(FeedError.NOT_A_FEED, summary.feeds.single().error)
    }

    @Test
    fun `the sync fails only when every feed lacked the network`() = runTest {
        val fetcher = FakeFetcher(mapOf(a.url to { throw AppError.Network(IOException("down")) }, b.url to { throw AppError.Network(IOException("down")) }))
        val failure = runCatching { FeedSync(MemoryStore(), fetcher, MemoryOwner(), Mutex()).sync(listOf(a, b), emptyList(), now) }.exceptionOrNull()
        assertTrue(failure is AppError.Network)
    }

    @Test
    fun `validators go out only while the feed's articles are here, and a 304 keeps them`() = runTest {
        val store = MemoryStore()
        val owner = MemoryOwner()
        var answer: () -> FetchResult = { FetchResult.Fetched(rss("1"), etag = "\"e1\"", lastModified = "Mon, 09 Nov 2026 10:00:00 GMT") }
        val fetcher = FakeFetcher(mapOf(a.url to { answer() }))
        val sync = FeedSync(store, fetcher, owner, Mutex())
        val first = sync.sync(listOf(a), emptyList(), now)
        answer = { FetchResult.NotModified }
        val second = sync.sync(listOf(a), first.feeds, now + 1000)
        assertEquals("\"e1\"", fetcher.calls[1].etag)
        assertEquals("Mon, 09 Nov 2026 10:00:00 GMT", fetcher.calls[1].lastModified)
        assertEquals(1, second.unread)
        assertEquals("\"e1\"", second.feeds.single().etag)

        // After a switch away and back the articles are gone, so a 304 would leave the list empty.
        // 切走再切回来之后文章已经没了，此时 304 会让列表空着。
        owner.value = "https://reader.example.org"
        store.clear()
        answer = { FetchResult.Fetched(rss("1")) }
        sync.sync(listOf(a), second.feeds, now + 2000)
        assertEquals("", fetcher.calls[2].etag)
    }

    @Test
    fun `articles from another source are dropped when feeds take over`() = runTest {
        val store = MemoryStore()
        store.apply(MergeResult(listOf(StoredArticle(id = 7, title = "Miniflux article")), emptyList()))
        val owner = MemoryOwner("https://reader.example.org")
        val fetcher = FakeFetcher(mapOf(a.url to { FetchResult.Fetched(rss("1")) }))
        FeedSync(store, fetcher, owner, Mutex()).sync(listOf(a), emptyList(), now)
        assertEquals(listOf(FeedPolicy.articleId(a.url, "1")), store.all().map { it.id })
    }

    @Test
    fun `many feeds are merged batch by batch and an oversized one says so`() = runTest {
        val feeds = (1..9).map { Feed("https://f$it.example.org/rss", "F$it") }
        val answers = feeds.associate { f -> f.url to { FetchResult.Fetched(rss("${f.title}-1", "${f.title}-2")) as FetchResult } } +
            (feeds[4].url to { FetchResult.TooLarge })
        val summary = FeedSync(MemoryStore(), FakeFetcher(answers), MemoryOwner(), Mutex()).sync(feeds, emptyList(), now)
        assertEquals(16, summary.unread)
        assertEquals(FeedError.TOO_LARGE, summary.feeds.single { it.url == feeds[4].url }.error)
    }

    @Test
    fun `no feeds deletes nothing`() = runTest {
        val store = MemoryStore()
        store.apply(MergeResult(listOf(StoredArticle(id = FeedPolicy.articleId(a.url, "1"), title = "kept")), emptyList()))
        FeedSync(store, FakeFetcher(emptyMap()), MemoryOwner(FeedPolicy.OWNER), Mutex()).sync(emptyList(), emptyList(), now)
        assertEquals(1, store.all().size)
    }

    @Test
    fun `without network the other source's articles stay`() = runTest {
        val store = MemoryStore()
        store.apply(MergeResult(listOf(StoredArticle(id = 7, title = "Miniflux article")), emptyList()))
        val owner = MemoryOwner("https://reader.example.org")
        val fetcher = FakeFetcher(mapOf(a.url to { throw AppError.Network(IOException("down")) }))
        runCatching { FeedSync(store, fetcher, owner, Mutex()).sync(listOf(a), emptyList(), now) }
        assertEquals(listOf(7L), store.all().map { it.id })
        assertEquals("https://reader.example.org", owner.value)
    }

    @Test
    fun `a page's dead feed link is skipped and the next one is used`() = runTest {
        val page = "https://site.example.org/"
        val html = """<html><head><link rel="alternate" type="application/rss+xml" href="/dead.xml">
            <link rel="alternate" type="application/atom+xml" href="https://site.example.org/live.xml"></head></html>"""
        val fetcher = FakeFetcher(
            mapOf(
                page to { FetchResult.Fetched(html.toByteArray(), finalUrl = page) },
                "https://site.example.org/dead.xml" to { throw AppError.Http(FeedApi.SERVICE, 404) },
                "https://site.example.org/live.xml" to { FetchResult.Fetched(rss("1")) },
            ),
        )
        val (url, feed) = FeedFinder(fetcher).find(page)!!
        assertEquals("https://site.example.org/live.xml", url)
        assertEquals("Site", feed.title)
        assertEquals(null, FeedFinder(FakeFetcher(mapOf(page to { FetchResult.Fetched("<html></html>".toByteArray()) }))).find(page))
    }

    @Test
    fun `the HTTP fetcher sends validators, understands 304 and caps the body`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val url = server.url("/rss").toString()
            server.enqueue(MockResponse().setResponseCode(304))
            assertEquals(FetchResult.NotModified, FeedApi(OkHttpClient()).fetch(url, "\"e\"", "Mon, 09 Nov 2026 10:00:00 GMT"))
            val request = server.takeRequest()
            assertEquals("\"e\"", request.getHeader("If-None-Match"))
            assertEquals("Mon, 09 Nov 2026 10:00:00 GMT", request.getHeader("If-Modified-Since"))

            server.enqueue(MockResponse().setBody("<rss/>").setHeader("ETag", "\"n\""))
            val fetched = FeedApi(OkHttpClient()).fetch(url, "", "") as FetchResult.Fetched
            assertEquals("\"n\"", fetched.etag)
            assertEquals(null, server.takeRequest().getHeader("If-None-Match"))

            server.enqueue(MockResponse().setResponseCode(500))
            assertEquals(500, (runCatching { FeedApi(OkHttpClient()).fetch(url, "", "") }.exceptionOrNull() as AppError.Http).code)

            server.enqueue(MockResponse().setBody(okio.Buffer().write(ByteArray((FeedApi.MAX_BYTES + 10).toInt()))))
            assertEquals(FetchResult.TooLarge, FeedApi(OkHttpClient()).fetch(url, "", ""))
        } finally {
            server.shutdown()
        }
    }
}
