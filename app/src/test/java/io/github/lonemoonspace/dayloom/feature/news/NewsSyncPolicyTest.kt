package io.github.lonemoonspace.dayloom.feature.news

import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.RemoteEntry
import io.github.lonemoonspace.dayloom.feature.news.domain.RemoteList
import io.github.lonemoonspace.dayloom.feature.news.domain.StoredArticle
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsSyncPolicyTest {

    private val now = 1_800_000_000_000L
    private val day = Duration.ofDays(1).toMillis()

    private fun remote(id: Long, read: Boolean = false, starred: Boolean = false, age: Long = day) =
        RemoteEntry(id, "Feed", "Title $id", "https://example.org/$id", "", "<p>Body $id</p>", now - age, read, starred, 3)

    /** A server list that was fetched in full. / 完整取到的服务器列表。 */
    private fun whole(vararg entries: RemoteEntry) = RemoteList(entries.toList(), complete = true)

    private fun stored(id: Long, read: Boolean = false, starred: Boolean = false, age: Long = day, pendingRead: Boolean? = null, pendingStarred: Boolean? = null, summary: String = "") =
        StoredArticle(id, "Feed", "Title $id", publishedAt = now - age, read = read, starred = starred, pendingRead = pendingRead, pendingStarred = pendingStarred, summary = summary)

    @Test
    fun `the push plan sends pending reads and only the stars that differ from a known server state`() {
        val local = listOf(
            stored(1, pendingRead = true),
            stored(2, pendingRead = false),
            stored(3, pendingStarred = true),
            stored(4, pendingStarred = true),
            stored(5),
            stored(6, pendingStarred = false),
        )
        val plan = NewsSyncPolicy.pushPlan(local, unread = whole(remote(6, starred = true)), starred = whole(remote(4, starred = true)))
        assertEquals(listOf(1L), plan.markRead)
        assertEquals(listOf(2L), plan.markUnread)
        assertEquals("4 is starred on the server already; 6 is listed as starred among the unread", listOf(3L, 6L), plan.toggleStar.sorted())
        assertEquals(mapOf(1L to true, 2L to false), plan.sentRead)
        assertEquals(mapOf(3L to true, 4L to true, 6L to false), plan.sentStarred)
    }

    @Test
    fun `a star whose server state is unknown waits`() {
        val full = (1L..3L).map { remote(it + 1000, starred = true) }
        val plan = NewsSyncPolicy.pushPlan(listOf(stored(1, pendingStarred = true)), unread = whole(), starred = RemoteList(full, complete = false))
        assertTrue(plan.toggleStar.isEmpty())
        assertTrue("not sent, so it stays pending", plan.sentStarred.isEmpty())
    }

    @Test
    fun `remote entries refresh the text but keep the summary, and pending changes win`() {
        val local = listOf(stored(1, summary = "Short", pendingRead = true))
        val result = NewsSyncPolicy.merge(local, unread = whole(remote(1)), starred = whole(), sentRead = emptyMap(), sentStarred = emptyMap(), nowMillis = now)
        val row = result.upsert.single()
        assertEquals("<p>Body 1</p>", row.contentHtml)
        assertEquals("Short", row.summary)
        assertTrue("read here, not yet pushed", row.read)
        assertEquals(true, row.pendingRead)
        assertEquals("read now", now, row.readAt)

        val pushed = NewsSyncPolicy.merge(local, whole(remote(1)), whole(), sentRead = mapOf(1L to true), sentStarred = emptyMap(), nowMillis = now).upsert.single()
        assertTrue(pushed.read)
        assertNull("confirmed by the push", pushed.pendingRead)
    }

    @Test
    fun `a change made during the sync survives it`() {
        // The plan sent "read"; meanwhile the user marked it unread again. / 计划发的是「已读」；其间用户又标回了未读。
        val local = listOf(stored(1, pendingRead = false))
        val row = NewsSyncPolicy.merge(local, whole(remote(1, read = true)), whole(), sentRead = mapOf(1L to true), sentStarred = emptyMap(), nowMillis = now).upsert.single()
        assertEquals(false, row.read)
        assertEquals(false, row.pendingRead)
    }

    @Test
    fun `stars come from the entry itself, not only from the capped starred list`() {
        val row = NewsSyncPolicy.merge(emptyList(), whole(remote(1, starred = true)), whole(), emptyMap(), emptyMap(), now).upsert.single()
        assertTrue(row.starred)
    }

    @Test
    fun `an article that left a complete unread list was read elsewhere`() {
        val local = listOf(stored(1), stored(2, pendingRead = false))
        val result = NewsSyncPolicy.merge(local, unread = whole(), starred = whole(), sentRead = emptyMap(), sentStarred = emptyMap(), nowMillis = now)
        assertEquals(listOf(1L), result.upsert.filter { it.read }.map { it.id })
        assertTrue("the local 'unread' still waits to be sent", result.upsert.none { it.id == 2L })
    }

    @Test
    fun `a truncated list proves nothing about the rest`() {
        val full = (1L..3L).map { remote(it + 1000) }
        val result = NewsSyncPolicy.merge(listOf(stored(1)), unread = RemoteList(full, complete = false), starred = whole(), sentRead = emptyMap(), sentStarred = emptyMap(), nowMillis = now)
        assertTrue(result.upsert.none { it.id == 1L })
    }

    @Test
    fun `read articles go two weeks after they were read, not after they were published`() {
        val old = 30 * day
        val local = listOf(
            stored(1, read = true, age = old).copy(readAt = now - 20 * day),
            stored(2, read = true, starred = true, age = old).copy(readAt = now - 20 * day),
            stored(3, read = true, age = old).copy(readAt = now - day),
            stored(4, read = true, age = old, pendingStarred = false).copy(readAt = now - 20 * day),
        )
        val result = NewsSyncPolicy.merge(local, unread = whole(), starred = whole(remote(2, read = true, starred = true, age = old)), sentRead = emptyMap(), sentStarred = emptyMap(), nowMillis = now)
        assertEquals(listOf(1L), result.delete)
        // An old article read just now is kept. / 刚读过的旧文章保留。
        val fresh = NewsSyncPolicy.merge(listOf(stored(5, age = old)), whole(), whole(), emptyMap(), emptyMap(), now)
        assertTrue(fresh.delete.isEmpty())
        assertEquals(now, fresh.upsert.single().readAt)
    }

    @Test
    fun `addresses must be https and lose the trailing slash`() {
        assertEquals("https://reader.example.org", NewsSyncPolicy.normalizeBaseUrl("  https://reader.example.org/ "))
        assertEquals("https://api.example.org/v1", NewsSyncPolicy.normalizeBaseUrl("https://api.example.org/v1/"))
        assertNull(NewsSyncPolicy.normalizeBaseUrl("http://reader.example.org"))
        // Without a scheme people mean https. / 没写协议时指的就是 https。
        assertEquals("https://reader.example.org/miniflux", NewsSyncPolicy.normalizeBaseUrl("reader.example.org/miniflux/"))
        assertTrue(NewsSyncPolicy.isPlainHttp(" http://192.168.1.2:8080"))
        assertNull(NewsSyncPolicy.normalizeBaseUrl("ftp://reader.example.org"))
        assertNull(NewsSyncPolicy.normalizeBaseUrl("   "))
        assertNull(NewsSyncPolicy.normalizeBaseUrl("https://"))
    }

    @Test
    fun `plain text for the summarizer drops markup and scripts and is capped`() {
        val html = "<h1>Head</h1><p>One &amp; two<br>three</p><script>alert(1)</script><style>p{}</style>"
        assertEquals("Head\nOne & two\nthree", NewsSyncPolicy.plainText(html))
        assertEquals(10, NewsSyncPolicy.plainText("x".repeat(50), maxChars = 10).length)
    }

    @Test
    fun `the summary counts unread and starred and lists the newest unread`() {
        val summary = NewsSyncPolicy.summary(listOf(stored(1, age = 3 * day), stored(2, age = day), stored(3, read = true, starred = true)))
        assertEquals(2, summary.unread)
        assertEquals(1, summary.starred)
        assertEquals(listOf(2L, 1L), summary.latest.map { it.id })
    }

    @Test
    fun `excerpts are fetched newest first, unread or starred, not again during the retry wait, never when known short`() {
        val long = "<p>" + "word ".repeat(40) + "</p>"
        val articles = listOf(
            stored(1, age = 3 * day),
            stored(2, age = day),
            stored(3, read = true),
            stored(4, read = true, starred = true, age = 2 * day),
            stored(5).copy(contentHtml = long),
        )
        assertEquals(listOf(2L, 4L, 1L), NewsSyncPolicy.fullTextDue(articles, nowMillis = now).map { it.id })
        // 30 minutes written out, so shrinking the constant fails the test. / 30 分钟写成字面量，改小常量会让测试失败。
        val wait = 30 * 60_000L
        val tried = articles.map {
            when (it.id) {
                2L -> it.copy(fullTextTriedAt = now - wait + 1)
                1L -> it.copy(fullTextTriedAt = now - wait)
                4L -> it.copy(fullTextTriedAt = NewsSyncPolicy.FULL_TEXT_SHORT)
                else -> it
            }
        }
        assertEquals(listOf(1L), NewsSyncPolicy.fullTextDue(tried, now).map { it.id })
        assertEquals(1, NewsSyncPolicy.fullTextDue(articles, now, limit = 1).size)
    }

    @Test
    fun `opening an excerpt fetches its full text unless the server said it is that short or it just failed`() {
        val excerpt = stored(1)
        val retry = NewsSyncPolicy.FULL_TEXT_RETRY.toMillis()
        assertTrue(NewsSyncPolicy.fetchOnOpen(excerpt, now))
        assertFalse("a fresh failure waits", NewsSyncPolicy.fetchOnOpen(excerpt.copy(fullTextTriedAt = now - retry + 1), now))
        assertTrue("an old failure is retried", NewsSyncPolicy.fetchOnOpen(excerpt.copy(fullTextTriedAt = now - retry), now))
        assertFalse(NewsSyncPolicy.fetchOnOpen(excerpt.copy(fullTextTriedAt = NewsSyncPolicy.FULL_TEXT_SHORT), now))
        assertFalse(NewsSyncPolicy.fetchOnOpen(excerpt.copy(contentHtml = "<p>" + "word ".repeat(40) + "</p>"), now))
    }

    @Test
    fun `list items carry a one-line preview and whether the full text is here, and search covers title, source and text`() {
        val excerpt = NewsSyncPolicy.listItem(stored(1).copy(contentHtml = "<p>Short</p>"))
        val full = NewsSyncPolicy.listItem(stored(2).copy(feedTitle = "Tech Site", contentHtml = "<p>First line</p><p>" + "Rocket launch. ".repeat(10) + "</p>"))
        assertEquals(false, excerpt.fullText)
        assertEquals(true, full.fullText)
        assertTrue("no line breaks in the preview", '\n' !in full.preview)
        assertTrue(NewsSyncPolicy.matches(full, "rocket"))
        assertTrue(NewsSyncPolicy.matches(full, "tech"))
        assertTrue(NewsSyncPolicy.matches(full, "title 2"))
        assertTrue(NewsSyncPolicy.matches(full, "  "))
        assertFalse(NewsSyncPolicy.matches(excerpt, "rocket"))
    }

    @Test
    fun `a sync keeps full text fetched here when the feed sends only an excerpt`() {
        val long = "<p>" + "word ".repeat(40) + "</p>"
        val local = listOf(stored(1).copy(contentHtml = long))
        val kept = NewsSyncPolicy.merge(local, whole(remote(1)), whole(), emptyMap(), emptyMap(), now).upsert.single()
        assertEquals(long, kept.contentHtml)
        val longer = remote(1).copy(contentHtml = "<p>" + "new ".repeat(40) + "</p>")
        assertEquals(longer.contentHtml, NewsSyncPolicy.merge(local, whole(longer), whole(), emptyMap(), emptyMap(), now).upsert.single().contentHtml)
    }
}
