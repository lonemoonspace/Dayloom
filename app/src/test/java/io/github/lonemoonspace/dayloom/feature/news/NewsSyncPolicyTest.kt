package io.github.lonemoonspace.dayloom.feature.news

import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.RemoteEntry
import io.github.lonemoonspace.dayloom.feature.news.domain.StoredArticle
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsSyncPolicyTest {

    private val now = 1_800_000_000_000L
    private val day = Duration.ofDays(1).toMillis()

    private fun remote(id: Long, read: Boolean = false, starred: Boolean = false, age: Long = day) =
        RemoteEntry(id, "Feed", "Title $id", "https://example.org/$id", "", "<p>Body $id</p>", now - age, read, starred, 3)

    private fun stored(id: Long, read: Boolean = false, starred: Boolean = false, age: Long = day, pendingRead: Boolean? = null, pendingStarred: Boolean? = null, summary: String = "") =
        StoredArticle(id, "Feed", "Title $id", publishedAt = now - age, read = read, starred = starred, pendingRead = pendingRead, pendingStarred = pendingStarred, summary = summary)

    @Test
    fun `the push plan sends pending reads and only the stars that differ from the server`() {
        val local = listOf(
            stored(1, pendingRead = true),
            stored(2, pendingRead = false),
            stored(3, pendingStarred = true),
            stored(4, pendingStarred = true),
            stored(5),
        )
        val plan = NewsSyncPolicy.pushPlan(local, remoteStarred = setOf(4))
        assertEquals(listOf(1L), plan.markRead)
        assertEquals(listOf(2L), plan.markUnread)
        assertEquals("4 is starred on the server already", listOf(3L), plan.toggleStar)
    }

    @Test
    fun `remote entries refresh the text but keep the summary, and pending changes win`() {
        val local = listOf(stored(1, summary = "Short", pendingRead = true))
        val result = NewsSyncPolicy.merge(local, unread = listOf(remote(1)), starred = emptyList(), pushed = false, nowMillis = now)
        val row = result.upsert.single()
        assertEquals("<p>Body 1</p>", row.contentHtml)
        assertEquals("Short", row.summary)
        assertTrue("read here, not yet pushed", row.read)
        assertEquals(true, row.pendingRead)

        val pushed = NewsSyncPolicy.merge(local, unread = listOf(remote(1)), starred = emptyList(), pushed = true, nowMillis = now).upsert.single()
        assertTrue(pushed.read)
        assertNull("confirmed by the push", pushed.pendingRead)
    }

    @Test
    fun `an article that left a complete unread list was read elsewhere`() {
        val local = listOf(stored(1), stored(2, pendingRead = false))
        val result = NewsSyncPolicy.merge(local, unread = emptyList(), starred = emptyList(), pushed = false, nowMillis = now)
        assertEquals(listOf(1L), result.upsert.filter { it.read }.map { it.id })
        assertTrue("the local 'unread' still waits to be sent", result.upsert.none { it.id == 2L })
    }

    @Test
    fun `a truncated list proves nothing about the rest`() {
        val full = (1L..NewsSyncPolicy.FETCH_LIMIT).map { remote(it + 1000) }
        val result = NewsSyncPolicy.merge(listOf(stored(1)), unread = full, starred = emptyList(), pushed = true, nowMillis = now)
        assertTrue(result.upsert.none { it.id == 1L })
    }

    @Test
    fun `old read articles go unless starred or pending`() {
        val old = 30 * day
        val local = listOf(stored(1, read = true, age = old), stored(2, read = true, starred = true, age = old), stored(3, read = true, age = old, pendingStarred = false))
        val result = NewsSyncPolicy.merge(local, unread = emptyList(), starred = listOf(remote(2, read = true, starred = true, age = old)), pushed = false, nowMillis = now)
        assertEquals(listOf(1L), result.delete)
    }

    @Test
    fun `addresses must be https and lose the trailing slash`() {
        assertEquals("https://reader.example.org", NewsSyncPolicy.normalizeBaseUrl("  https://reader.example.org/ "))
        assertEquals("https://api.example.org/v1", NewsSyncPolicy.normalizeBaseUrl("https://api.example.org/v1/"))
        assertNull(NewsSyncPolicy.normalizeBaseUrl("http://reader.example.org"))
        assertNull(NewsSyncPolicy.normalizeBaseUrl("reader.example.org"))
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
}
