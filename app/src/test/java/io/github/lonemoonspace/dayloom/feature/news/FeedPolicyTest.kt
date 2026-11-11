package io.github.lonemoonspace.dayloom.feature.news

import io.github.lonemoonspace.dayloom.feature.news.domain.Feed
import io.github.lonemoonspace.dayloom.feature.news.domain.FeedError
import io.github.lonemoonspace.dayloom.feature.news.domain.FeedItem
import io.github.lonemoonspace.dayloom.feature.news.domain.FeedPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.FeedState
import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSourceKind
import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.ParsedFeed
import io.github.lonemoonspace.dayloom.feature.news.domain.StarterFeeds
import io.github.lonemoonspace.dayloom.feature.news.domain.StarterLanguage
import io.github.lonemoonspace.dayloom.feature.news.domain.StoredArticle
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedPolicyTest {

    private val now = Instant.parse("2026-11-09T12:00:00Z").toEpochMilli()
    private val hour = Duration.ofHours(1).toMillis()
    private val a = Feed("https://a.example.org/rss", "A")
    private val b = Feed("https://b.example.org/rss", "B")

    private fun item(key: String, ageHours: Long = 1, title: String = "T $key") =
        FeedItem(key = key, title = title, link = "https://a.example.org/$key", author = "", html = "<p>$key</p>", publishedAt = now - ageHours * hour)

    private fun stored(feed: Feed, key: String, ageHours: Long = 1, read: Boolean = false, starred: Boolean = false, readAt: Long = 0) =
        StoredArticle(
            id = FeedPolicy.articleId(feed.url, key), feedTitle = feed.title, title = "T $key", publishedAt = now - ageHours * hour,
            read = read, starred = starred, readAt = readAt, fullTextTriedAt = NewsSyncPolicy.FULL_TEXT_SHORT,
        )

    @Test
    fun `feed addresses are https only and keep their path and query`() {
        assertEquals("https://example.org/rss?x=1", FeedPolicy.normalizeFeedUrl("  example.org/rss?x=1 "))
        assertEquals("https://example.org/feed/", FeedPolicy.normalizeFeedUrl("HTTPS://example.org/feed/")?.lowercase())
        assertEquals("the host is lower-cased, the path is not", "https://news.example.org/Feed?A=1", FeedPolicy.normalizeFeedUrl("https://News.Example.org/Feed?A=1"))
        assertNull(FeedPolicy.normalizeFeedUrl("http://example.org/rss"))
        assertNull(FeedPolicy.normalizeFeedUrl("ftp://example.org/rss"))
        assertNull(FeedPolicy.normalizeFeedUrl("not a url"))
        assertNull(FeedPolicy.normalizeFeedUrl("localhost"))
        assertNull(FeedPolicy.normalizeFeedUrl(""))
    }

    @Test
    fun `adding skips duplicates and unusable addresses and stops at the limit`() {
        val added = FeedPolicy.add(listOf(a), listOf(a, Feed("http://plain.example.org/rss", "P"), Feed("c.example.org/rss", "")))
        assertEquals(listOf(a.url, "https://c.example.org/rss"), added.map { it.url })
        assertEquals("a feed without a name is named by its host", "c.example.org", added.last().title)
        val many = (1..80).map { Feed("https://example.org/$it", "F$it") }
        assertEquals(FeedPolicy.MAX_FEEDS, FeedPolicy.add(emptyList(), many).size)
    }

    @Test
    fun `article ids are stable, positive and tell their feed`() {
        val id = FeedPolicy.articleId(a.url, "guid-1")
        assertEquals(id, FeedPolicy.articleId(a.url, "guid-1"))
        assertTrue(id > 0)
        assertEquals(FeedPolicy.feedTag(a.url), FeedPolicy.feedTagOf(id))
        assertNotEquals(id, FeedPolicy.articleId(b.url, "guid-1"))
        assertNotEquals(FeedPolicy.feedTag(a.url), FeedPolicy.feedTag(b.url))
    }

    @Test
    fun `articles of a feed that was not read this round are never written back`() {
        // They reach the merge without their text, so writing one back would erase it. / 它们进入合并时不带正文，写回去就会把正文抹掉。
        val other = stored(b, "kept").copy(pendingRead = true)
        val result = FeedPolicy.merge(listOf(other), mapOf(a.url to ParsedFeed("A", listOf(item("new")))), listOf(a, b), now)
        assertEquals(listOf(FeedPolicy.articleId(a.url, "new")), result.upsert.map { it.id })
        assertEquals(listOf(other.id), result.clearPending)
        assertTrue(result.delete.isEmpty())
    }

    @Test
    fun `new items come in unread, known ones keep their state and take the feed's new text`() {
        val known = stored(a, "1", read = true, starred = true, readAt = now - hour).copy(summary = "S", summaryLanguage = "en", pendingRead = true)
        val result = FeedPolicy.merge(
            local = listOf(known),
            fetched = mapOf(a.url to ParsedFeed("A site", listOf(item("1", title = "<b>New</b> title"), item("2")))),
            feeds = listOf(a),
            nowMillis = now,
        )
        val byId = result.upsert.associateBy { it.id }
        val updated = byId.getValue(known.id)
        assertTrue(updated.read && updated.starred)
        assertEquals("S", updated.summary)
        assertEquals("New title", updated.title)
        assertNull("nothing to push for feeds", updated.pendingRead)
        val fresh = byId.getValue(FeedPolicy.articleId(a.url, "2"))
        assertTrue(!fresh.read && !fresh.starred)
        assertEquals("A", fresh.feedTitle)
        assertEquals("never asks for a full text", NewsSyncPolicy.FULL_TEXT_SHORT, fresh.fullTextTriedAt)
        assertTrue(result.delete.isEmpty())
    }

    @Test
    fun `old items are not added, and each feed contributes at most its newest items`() {
        val old = item("old", ageHours = FeedPolicy.MAX_AGE.toHours() + 1)
        val many = (1..50).map { item("n$it", ageHours = it.toLong()) }
        val result = FeedPolicy.merge(emptyList(), mapOf(a.url to ParsedFeed("", listOf(old) + many)), listOf(a), now)
        assertEquals(FeedPolicy.ITEMS_PER_FEED, result.upsert.size)
        assertTrue(result.upsert.none { it.id == FeedPolicy.articleId(a.url, "old") })
        assertTrue(result.upsert.any { it.id == FeedPolicy.articleId(a.url, "n1") })
    }

    @Test
    fun `an undated item keeps the date it was first seen`() {
        val first = FeedPolicy.merge(emptyList(), mapOf(a.url to ParsedFeed("", listOf(item("x").copy(publishedAt = 0)))), listOf(a), now)
        assertEquals(now, first.upsert.single().publishedAt)
        val later = FeedPolicy.merge(first.upsert, mapOf(a.url to ParsedFeed("", listOf(item("x").copy(publishedAt = 0)))), listOf(a), now + hour)
        assertTrue("unchanged, so nothing to write", later.upsert.isEmpty())
    }

    @Test
    fun `articles go when their feed is dropped, read long ago or too old unread, unless starred`() {
        val dropped = stored(b, "1")
        val droppedStarred = stored(b, "2", starred = true)
        val readLongAgo = stored(a, "3", ageHours = 2, read = true, readAt = now - NewsSyncPolicy.KEEP_READ.toMillis() - 1)
        val readRecently = stored(a, "4", read = true, readAt = now - hour)
        val staleUnread = stored(a, "5", ageHours = FeedPolicy.MAX_AGE.toHours() + 1)
        val staleStarred = stored(a, "6", ageHours = FeedPolicy.MAX_AGE.toHours() + 1, starred = true)
        val fresh = stored(a, "7")
        val fresher = stored(a, "8")
        val result = FeedPolicy.merge(
            listOf(dropped, droppedStarred, readLongAgo, readRecently, fresh, fresher, staleUnread, staleStarred),
            fetched = emptyMap(),
            feeds = listOf(a),
            nowMillis = now,
        )
        assertEquals(setOf(dropped.id, readLongAgo.id, staleUnread.id), result.delete.toSet())
        assertTrue("a feed that failed this time leaves its articles alone", result.upsert.isEmpty())
    }

    @Test
    fun `an old unread article still in the feed goes and is not added back, a read one stays while listed`() {
        val stale = item("s", ageHours = FeedPolicy.MAX_AGE.toHours() + 1)
        val recent = (1..FeedPolicy.KEEP_LATEST).map { item("r$it", ageHours = it.toLong()) }
        val kept = (1..FeedPolicy.KEEP_LATEST).map { stored(a, "r$it", ageHours = it.toLong()) }
        val unread = stored(a, "s", ageHours = FeedPolicy.MAX_AGE.toHours() + 1)
        val listing = mapOf(a.url to ParsedFeed("", recent + stale))
        val first = FeedPolicy.merge(kept + unread, listing, listOf(a), now)
        assertEquals(listOf(unread.id), first.delete)
        assertTrue(FeedPolicy.merge(kept, listing, listOf(a), now).upsert.none { it.id == unread.id })

        val read = stored(a, "s", ageHours = FeedPolicy.MAX_AGE.toHours() + 1, read = true, readAt = now - NewsSyncPolicy.KEEP_READ.toMillis() - 1)
        val listed = FeedPolicy.merge(kept + read, listing, listOf(a), now)
        assertTrue("deleting it would bring it back unread", listed.delete.isEmpty())
    }

    @Test
    fun `a feed's newest articles are kept whatever their age`() {
        val months = 24L * 90
        val items = (1..5).map { item("m$it", ageHours = months + it) }
        val added = FeedPolicy.merge(emptyList(), mapOf(a.url to ParsedFeed("", items)), listOf(a), now)
        assertEquals((1..FeedPolicy.KEEP_LATEST).map { FeedPolicy.articleId(a.url, "m$it") }, added.upsert.map { it.id })

        // The next sync finds the feed unchanged; what was kept must not be cleaned up again.
        // 下一次同步时订阅源没有变化；刚才保留的不能又被清掉。
        val unchanged = FeedPolicy.merge(added.upsert, fetched = emptyMap(), feeds = listOf(a), nowMillis = now + hour)
        assertTrue(unchanged.delete.isEmpty())

        // Read long ago, they still stay: once deleted, the next sync that reads the feed would add them again as unread.
        // 很久以前读过的也留着：一旦删掉，下一次读到这个源时又会把它们当未读加回来。
        val later = now + NewsSyncPolicy.KEEP_READ.toMillis() + 2 * hour
        val read = added.upsert.map { it.copy(read = true, readAt = now + hour) }
        assertTrue(FeedPolicy.merge(read, fetched = emptyMap(), feeds = listOf(a), nowMillis = later).delete.isEmpty())
        val again = FeedPolicy.merge(read, mapOf(a.url to ParsedFeed("", items)), listOf(a), later)
        assertTrue("still listed and still read", again.delete.isEmpty() && again.upsert.none { !it.read })
    }

    @Test
    fun `undated items count as new when the newest are picked`() {
        val dated = (1..40).map { item("d$it", ageHours = it.toLong()) }
        val undated = item("u").copy(publishedAt = 0)
        val kept = FeedPolicy.newest(ParsedFeed("", dated + undated), now).items
        assertEquals(FeedPolicy.ITEMS_PER_FEED, kept.size)
        assertEquals("u", kept.first().key)
    }

    @Test
    fun `the same feed listed twice is merged once`() {
        val result = FeedPolicy.merge(emptyList(), mapOf(a.url to ParsedFeed("", listOf(item("1")))), listOf(a, a), now)
        assertEquals(1, result.upsert.size)
    }

    @Test
    fun `failing feeds are named in list order`() {
        val states = listOf(FeedState(b.url, error = FeedError.HTTP, code = 404), FeedState(a.url, okAt = now))
        assertEquals(listOf("B"), FeedPolicy.failing(listOf(a, b), states))
    }

    @Test
    fun `suggestions put the app language first and are all https`() {
        assertEquals(StarterLanguage.CHINESE, StarterFeeds.grouped("zh").first().first)
        assertEquals(StarterLanguage.NORWEGIAN, StarterFeeds.grouped("nb").first().first)
        assertEquals(StarterLanguage.ENGLISH, StarterFeeds.grouped("en").first().first)
        assertEquals(StarterFeeds.all.size, StarterFeeds.grouped("en").sumOf { it.second.size })
        assertTrue(StarterFeeds.all.all { FeedPolicy.normalizeFeedUrl(it.url) == it.url })
    }

    @Test
    fun `someone who already set up Miniflux stays on it until choosing`() {
        assertEquals(NewsSourceKind.RSS, NewsSettings().kind())
        assertEquals(NewsSourceKind.MINIFLUX, NewsSettings(serverUrl = "https://reader.example.org").kind())
        assertEquals(NewsSourceKind.RSS, NewsSettings(serverUrl = "https://reader.example.org", source = NewsSourceKind.RSS).kind())
    }
}
