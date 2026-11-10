package io.github.lonemoonspace.dayloom.feature.news

import io.github.lonemoonspace.dayloom.feature.news.domain.FeedParser
import java.nio.charset.Charset
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedParserTest {

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `RSS 2_0 prefers content encoded, reads dc creator and ignores atom self links`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:dc="http://purl.org/dc/elements/1.1/"
                 xmlns:atom="http://www.w3.org/2005/Atom">
              <channel>
                <title>Example News</title>
                <atom:link href="https://example.org/rss" rel="self"/>
                <item>
                  <title>First &amp; foremost</title>
                  <atom:link href="https://example.org/wrong"/>
                  <link>https://example.org/a</link>
                  <guid isPermaLink="false">item-1</guid>
                  <description>Short</description>
                  <content:encoded><![CDATA[<p>Long <b>body</b></p>]]></content:encoded>
                  <dc:creator>Kari</dc:creator>
                  <pubDate>Mon, 09 Nov 2026 08:00:00 GMT</pubDate>
                </item>
                <item>
                  <title>Second</title>
                  <link>https://example.org/b</link>
                  <description>Only a description</description>
                </item>
              </channel>
            </rss>""".trimIndent()
        val feed = FeedParser.parse(xml.toByteArray())!!
        assertEquals("Example News", feed.title)
        val (first, second) = feed.items
        assertEquals("item-1", first.key)
        assertEquals("First & foremost", first.title)
        assertEquals("https://example.org/a", first.link)
        assertEquals("<p>Long <b>body</b></p>", first.html)
        assertEquals("Kari", first.author)
        assertEquals(millis("2026-11-09T08:00:00Z"), first.publishedAt)
        assertEquals("without a guid the link is the key", "https://example.org/b", second.key)
        assertEquals("Only a description", second.html)
        assertEquals(0L, second.publishedAt)
    }

    @Test
    fun `Atom reads the alternate link, content over summary and published over updated`() {
        val xml = """<feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Site</title>
              <entry>
                <title type="html">Hello</title>
                <link rel="self" href="https://example.org/self"/>
                <link rel="alternate" href="https://example.org/post"/>
                <id>urn:uuid:1</id>
                <updated>2026-11-09T10:00:00Z</updated>
                <published>2026-11-09T09:00:00+01:00</published>
                <summary>Sum</summary>
                <content type="html">&lt;p&gt;Body&lt;/p&gt;</content>
                <author><name>Ola</name></author>
              </entry>
            </feed>"""
        val feed = FeedParser.parse(xml.toByteArray())!!
        assertEquals("Atom Site", feed.title)
        val entry = feed.items.single()
        assertEquals("urn:uuid:1", entry.key)
        assertEquals("https://example.org/post", entry.link)
        assertEquals("<p>Body</p>", entry.html)
        assertEquals("Ola", entry.author)
        assertEquals(millis("2026-11-09T08:00:00Z"), entry.publishedAt)
    }

    @Test
    fun `Atom xhtml content keeps its markup, and an item with neither guid nor link is keyed by its title`() {
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom"><title>X</title><entry><id>1</id><title>T</title>
              <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>A <b>bold</b> &amp; <a href="https://e.org/?a=1&amp;b=2">link</a><br/>end</p></div></content>
            </entry></feed>"""
        assertEquals(
            "<p>A <b>bold</b> &amp; <a href=\"https://e.org/?a=1&amp;b=2\">link</a><br />end</p>",
            FeedParser.parse(atom.toByteArray())!!.items.single().html,
        )
        val rss = "<rss><channel><title>R</title><item><title>Only a title</title><description>D</description></item></channel></rss>"
        assertEquals("Only a title", FeedParser.parse(rss.toByteArray())!!.items.single().key)
    }

    @Test
    fun `RSS 1_0 items sit next to the channel and use dc date and rdf about`() {
        val xml = """<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns="http://purl.org/rss/1.0/"
                     xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel rdf:about="https://example.org/"><title>RDF Site</title></channel>
              <item rdf:about="https://example.org/x">
                <title>X</title><link>https://example.org/x</link><description>D</description><dc:date>2026-11-08T12:00:00Z</dc:date>
              </item>
            </rdf:RDF>"""
        val feed = FeedParser.parse(xml.toByteArray())!!
        assertEquals("RDF Site", feed.title)
        val item = feed.items.single()
        assertEquals("https://example.org/x", item.key)
        assertEquals(millis("2026-11-08T12:00:00Z"), item.publishedAt)
    }

    @Test
    fun `the encoding the document declares is honoured`() {
        val xml = """<?xml version="1.0" encoding="GBK"?><rss><channel><title>新闻</title><item><title>标题</title><link>https://example.org/1</link></item></channel></rss>"""
        val feed = FeedParser.parse(xml.toByteArray(Charset.forName("GBK")))!!
        assertEquals("新闻", feed.title)
        assertEquals("标题", feed.items.single().title)
    }

    @Test
    fun `web pages, broken XML and other documents are not feeds`() {
        assertNull(FeedParser.parse("<html><head><title>x</title></head><body></body></html>".toByteArray()))
        assertNull(FeedParser.parse("<rss><channel><title>oops</channel></rss>".toByteArray()))
        assertNull(FeedParser.parse("""{"json":true}""".toByteArray()))
        assertNull(FeedParser.parse("<opml/>".toByteArray()))
    }

    @Test
    fun `external entities are never read`() {
        val secret = kotlin.io.path.createTempFile().toFile().apply { writeText("TOP-SECRET") }
        val xml = """<?xml version="1.0"?><!DOCTYPE rss [<!ENTITY x SYSTEM "${secret.toURI()}">]>
            <rss><channel><title>&x;</title></channel></rss>"""
        val feed = FeedParser.parse(xml.toByteArray())
        assertFalse(feed?.title.orEmpty().contains("TOP-SECRET"))
        secret.delete()
    }

    @Test
    fun `dates in the forms feeds really use`() {
        val expected = millis("2026-11-09T08:05:00Z")
        assertEquals(expected, FeedParser.parseDate("Mon, 09 Nov 2026 08:05:00 GMT"))
        assertEquals(expected, FeedParser.parseDate("Mon, 9 Nov 2026 08:05:00 +0000"))
        assertEquals("missing seconds, no weekday", expected, FeedParser.parseDate("9 Nov 2026 08:05 UT"))
        assertEquals("zone names", expected, FeedParser.parseDate("Mon, 09 Nov 2026 03:05:00 EST"))
        assertEquals("offset with a colon", expected, FeedParser.parseDate("Mon, 09 Nov 2026 09:05:00 +01:00"))
        assertEquals("lower-case month", expected, FeedParser.parseDate("Mon, 09 nov 2026 08:05:00 GMT"))
        assertEquals(expected, FeedParser.parseDate("2026-11-09T09:05:00+01:00"))
        assertEquals("no zone means UTC", expected, FeedParser.parseDate("2026-11-09T08:05:00"))
        assertEquals(millis("2026-11-09T00:00:00Z"), FeedParser.parseDate("2026-11-09"))
        assertEquals(0L, FeedParser.parseDate("yesterday"))
        assertEquals(0L, FeedParser.parseDate(""))
    }

    @Test
    fun `a web page's feed links are found and made absolute`() {
        val html = """<html><head>
            <link rel="stylesheet" href="/style.css">
            <link rel="alternate" type="application/rss+xml" title="All" href="/feed.xml?lang=en&amp;x=1">
            <LINK TYPE='application/atom+xml' REL='alternate' HREF='https://other.example.org/atom'>
            <link rel="alternate" hreflang="nb" href="/nb/">
            </head></html>"""
        assertEquals(
            listOf("https://example.org/feed.xml?lang=en&x=1", "https://other.example.org/atom"),
            FeedParser.discover(html, "https://example.org/news/"),
        )
    }

    @Test
    fun `a page is read in the charset its meta tag names`() {
        val html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=gbk\"><title>新闻</title></head></html>"
        assertTrue(FeedParser.pageText(html.toByteArray(charset("GBK"))).contains("新闻"))
        assertTrue(FeedParser.pageText("<meta charset=\"utf-8\"><p>新闻</p>".toByteArray()).contains("新闻"))
        assertTrue("an unknown charset falls back to UTF-8", FeedParser.pageText("<meta charset=\"nope-1\"><p>新闻</p>".toByteArray()).contains("新闻"))
    }

    @Test
    fun `OPML outlines with a feed address are read, nested ones too`() {
        val xml = """<opml version="2.0"><body>
            <outline text="Tech"><outline type="rss" text="A" title="Site A" xmlUrl="https://a.example.org/rss"/></outline>
            <outline type="rss" text="B" xmlUrl=" https://b.example.org/feed "/>
            <outline text="folder only"/>
            </body></opml>"""
        val feeds = FeedParser.opml(xml.toByteArray())!!
        assertEquals(listOf("https://a.example.org/rss", "https://b.example.org/feed"), feeds.map { it.url })
        assertEquals(listOf("Site A", "B"), feeds.map { it.title })
        assertTrue(FeedParser.opml("not xml".toByteArray()) == null)
    }
}
