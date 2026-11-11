package io.github.lonemoonspace.dayloom.feature.news.domain

import java.io.ByteArrayInputStream
import java.io.StringReader
import java.net.URI
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException

/**
 * Reads RSS 2.0, RSS 1.0 (RDF) and Atom, OPML subscription lists, and the feed links a web page announces. Uses the
 * platform's DOM parser on the raw bytes, so the encoding the document declares (GBK, ISO-8859-1…) is honoured; external
 * entities and DTDs are never fetched.
 * 读取 RSS 2.0、RSS 1.0（RDF）与 Atom、OPML 订阅列表，以及网页声明的订阅源链接。直接把原始字节交给平台的 DOM 解析器，文档自己
 * 声明的编码（GBK、ISO-8859-1……）因此能正确识别；从不加载外部实体和 DTD。
 */
object FeedParser {
    /** Items read from one feed; enough for any real feed, and a bound for a broken one. / 每个订阅源最多读的条数；对真实订阅源足够，也给异常的源设了上限。 */
    const val MAX_ITEMS = 200

    /** The feed, or null when the bytes are not well-formed RSS, RDF or Atom. / 订阅源；字节不是格式正确的 RSS、RDF 或 Atom 时为 null。 */
    fun parse(bytes: ByteArray): ParsedFeed? {
        val root = document(bytes) ?: return null
        return when (root.local().lowercase()) {
            "rss" -> rss(root)
            "rdf" -> rdf(root)
            "feed" -> atom(root)
            else -> null
        }
    }

    /** Every outline with a feed address, in file order; null when the bytes are not XML. / 所有带订阅地址的 outline，按文件顺序；不是 XML 时为 null。 */
    fun opml(bytes: ByteArray): List<Feed>? {
        val root = document(bytes) ?: return null
        return root.descendants("outline").mapNotNull { outline ->
            val url = outline.getAttribute("xmlUrl").trim()
            if (url.isEmpty()) return@mapNotNull null
            val title = outline.getAttribute("title").ifBlank { outline.getAttribute("text") }.trim()
            Feed(url, title)
        }
    }

    /**
     * Feed addresses a web page links to with `<link rel="alternate" type="application/rss+xml|atom+xml|rdf+xml">`,
     * resolved against [pageUrl], in page order.
     * 网页用 `<link rel="alternate" type="application/rss+xml|atom+xml|rdf+xml">` 指向的订阅地址，按 [pageUrl] 解析为绝对地址，按页面顺序。
     */
    fun discover(html: String, pageUrl: String): List<String> = LINK_TAG.findAll(html).mapNotNull { tag ->
        val attrs = ATTRIBUTE.findAll(tag.value).associate { m ->
            m.groupValues[1].lowercase() to (m.groupValues[3].ifEmpty { m.groupValues[4] }.ifEmpty { m.groupValues[5] })
        }
        val rel = attrs["rel"].orEmpty().lowercase().split(' ')
        val type = attrs["type"].orEmpty().lowercase().trim()
        val href = attrs["href"]?.replace("&amp;", "&")?.trim().orEmpty()
        if ("alternate" !in rel || type !in FEED_TYPES || href.isEmpty()) return@mapNotNull null
        try {
            URI(pageUrl).resolve(href).toString()
        } catch (_: IllegalArgumentException) {
            null
        }
    }.distinct().toList()

    /**
     * The text of a web page for [discover]: in the charset its `<meta>` names, else UTF-8. The tag is ASCII in every
     * charset a page is likely to use, so the first bytes are read as Latin-1 to find it.
     * 给 [discover] 用的网页文本：按 `<meta>` 声明的编码解码，没有声明则按 UTF-8。这个标签在网页常用的各种编码里都是 ASCII，
     * 所以先把开头的字节按 Latin-1 读出来找它。
     */
    fun pageText(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, META_SCAN_BYTES), Charsets.ISO_8859_1)
        val charset = META_CHARSET.find(head)?.groupValues?.get(1)?.let { name ->
            try {
                Charset.forName(name)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        return String(bytes, charset ?: Charsets.UTF_8)
    }

    private val META_CHARSET = Regex("(?i)<meta[^>]*charset\\s*=\\s*[\"']?\\s*([a-z0-9_.:-]+)")
    private const val META_SCAN_BYTES = 4096

    /**
     * Epoch millis of an RSS (RFC 822, often loosely written) or Atom (ISO 8601) date; 0 when it cannot be read. A time
     * without a zone is taken as UTC.
     * RSS（RFC 822，常常写得不规范）或 Atom（ISO 8601）日期的 epoch 毫秒；读不出来时为 0。没有时区的时间按 UTC 处理。
     */
    fun parseDate(text: String): Long {
        val t = text.trim()
        if (t.isEmpty()) return 0
        attempt { OffsetDateTime.parse(t).toInstant().toEpochMilli() }?.let { return it }
        attempt { LocalDateTime.parse(t).toInstant(ZoneOffset.UTC).toEpochMilli() }?.let { return it }
        attempt { LocalDate.parse(t).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }?.let { return it }
        attempt { ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }?.let { return it }
        // Loose RFC 822: drop the weekday, turn a zone name into an offset, allow one-digit days and missing seconds.
        // 不规范的 RFC 822：去掉星期，把时区名换成偏移量，允许一位数的日期和省略秒。
        val words = t.substringAfter(',').trim().split(Regex("\\s+"))
        if (words.size < 4) return 0
        val zone = words.getOrNull(4)?.let { ZONES[it.uppercase()] ?: it.replace(":", "") } ?: "+0000"
        val loose = listOf(words[0], words[1].take(3), words[2], words[3], zone).joinToString(" ")
        return attempt { ZonedDateTime.parse(loose, LOOSE_RFC_822).toInstant().toEpochMilli() } ?: 0
    }

    private fun rss(root: Element): ParsedFeed? {
        val channel = root.child("channel") ?: return null
        val items = channel.children("item").take(MAX_ITEMS).map { item(it) }
        return ParsedFeed(channel.text("title"), items)
    }

    /** RSS 1.0 keeps its items next to the channel, not inside it. / RSS 1.0 的条目与 channel 并列，而不是放在里面。 */
    private fun rdf(root: Element): ParsedFeed {
        val items = root.children("item").take(MAX_ITEMS).map { item(it) }
        return ParsedFeed(root.child("channel")?.text("title").orEmpty(), items)
    }

    private fun item(el: Element): FeedItem {
        // An RSS link is the element's text; Atom links inside RSS (atom:link) are only about the feed itself.
        // RSS 的链接是元素的文本；RSS 里的 Atom 链接（atom:link）说的是订阅源本身，不是这一条。
        val link = el.children("link").firstOrNull { it.namespaceURI != ATOM_NS }?.textContent?.trim().orEmpty()
        val guid = el.text("guid").ifEmpty { el.getAttributeNS(RDF_NS, "about").trim() }
        val description = el.text("description")
        val content = el.child("encoded", CONTENT_NS)?.textContent?.trim().orEmpty()
        return FeedItem(
            key = guid.ifEmpty { link }.ifEmpty { el.text("title") },
            title = el.text("title"),
            link = link,
            author = el.child("creator", DC_NS)?.textContent?.trim() ?: el.text("author"),
            html = content.ifEmpty { description },
            publishedAt = parseDate(el.text("pubDate").ifEmpty { el.child("date", DC_NS)?.textContent.orEmpty() }),
        )
    }

    private fun atom(root: Element): ParsedFeed {
        val items = root.children("entry").take(MAX_ITEMS).map { entry ->
            val link = entry.children("link").firstOrNull { it.getAttribute("rel").let { rel -> rel.isEmpty() || rel == "alternate" } }
                ?.getAttribute("href")?.trim().orEmpty()
            FeedItem(
                key = entry.text("id").ifEmpty { link }.ifEmpty { entry.text("title") },
                title = entry.text("title"),
                link = link,
                author = entry.child("author")?.text("name").orEmpty(),
                html = entry.child("content")?.let(::atomText).orEmpty().ifEmpty { entry.child("summary")?.let(::atomText).orEmpty() },
                publishedAt = parseDate(entry.text("published").ifEmpty { entry.text("updated") }),
            )
        }
        return ParsedFeed(root.text("title"), items)
    }

    /**
     * An Atom text construct as HTML: `type="xhtml"` carries real elements, which `textContent` would flatten into one run
     * of text, so they are written back out as markup; `text` and `html` are already a string.
     * 把 Atom 的文本结构转成 HTML：`type="xhtml"` 里是真正的元素，`textContent` 会把它们压成一串文字，所以重新写回标记；`text`
     * 与 `html` 本来就是字符串。
     */
    private fun atomText(el: Element): String {
        if (el.getAttribute("type") != "xhtml") return el.textContent.trim()
        // The spec wraps xhtml content in one div; its children are the content. / 规范要求 xhtml 内容外包一层 div，里面才是内容。
        val root = el.children("div").singleOrNull() ?: el
        return buildString { root.childNodes.let { nodes -> for (i in 0 until nodes.length) markup(nodes.item(i), this) } }.trim()
    }

    private fun markup(node: Node, out: StringBuilder) {
        when (node.nodeType) {
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> out.append(escape(node.nodeValue.orEmpty()))
            Node.ELEMENT_NODE -> {
                val el = node as Element
                val name = el.local()
                out.append('<').append(name)
                val attrs = el.attributes
                for (i in 0 until attrs.length) {
                    val attr = attrs.item(i)
                    if (attr.nodeName.startsWith("xmlns")) continue
                    out.append(' ').append(attr.localName ?: attr.nodeName).append("=\"").append(escape(attr.nodeValue.orEmpty())).append('"')
                }
                if (name.lowercase() in VOID_ELEMENTS) {
                    out.append(" />")
                    return
                }
                out.append('>')
                val nodes = el.childNodes
                for (i in 0 until nodes.length) markup(nodes.item(i), out)
                out.append("</").append(name).append('>')
            }
        }
    }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun document(bytes: ByteArray): Element? = try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Not every platform parser knows every feature; the entity resolver below blocks fetching either way.
            // 并非每个平台的解析器都认识所有特性；无论如何，下面的实体解析器都会阻止外部加载。
            for ((name, value) in SAFE_FEATURES) {
                try {
                    setFeature(name, value)
                } catch (_: Exception) {
                    // Unsupported here. / 这个平台不支持。
                }
            }
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
            setErrorHandler(QuietErrors)
        }
        builder.parse(InputSource(ByteArrayInputStream(bytes))).documentElement
    } catch (_: Exception) {
        null
    } catch (_: StackOverflowError) {
        // Absurdly deep nesting; not worth the process. / 嵌套深得离谱；不值得为它让进程崩溃。
        null
    }

    /** Fails on broken XML without printing to the log, which the default handler does. / XML 损坏时直接失败，不像默认处理器那样往日志里打印。 */
    private object QuietErrors : ErrorHandler {
        override fun warning(e: SAXParseException) = Unit
        override fun error(e: SAXParseException) = Unit
        override fun fatalError(e: SAXParseException) = throw e
    }

    private fun Element.local(): String = localName ?: nodeName.substringAfter(':')

    private fun Element.children(name: String): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { i -> (nodes.item(i) as? Element)?.takeIf { it.local() == name } }
    }

    private fun Element.child(name: String, namespace: String? = null): Element? =
        children(name).firstOrNull { namespace == null || it.namespaceURI == namespace }

    private fun Element.text(name: String): String = child(name)?.textContent?.trim().orEmpty()

    private fun Element.descendants(name: String): List<Element> {
        val found = mutableListOf<Element>()
        fun walk(node: Node) {
            val nodes = node.childNodes
            for (i in 0 until nodes.length) {
                val child = nodes.item(i) as? Element ?: continue
                if (child.local() == name) found += child
                walk(child)
            }
        }
        walk(this)
        return found
    }

    private inline fun attempt(block: () -> Long): Long? = try {
        block()
    } catch (_: DateTimeParseException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

    private const val ATOM_NS = "http://www.w3.org/2005/Atom"
    private const val RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    private const val CONTENT_NS = "http://purl.org/rss/1.0/modules/content/"
    private const val DC_NS = "http://purl.org/dc/elements/1.1/"

    private val SAFE_FEATURES = listOf(
        XMLConstants.FEATURE_SECURE_PROCESSING to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
        "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
    )

    private val VOID_ELEMENTS = setOf("br", "hr", "img", "wbr", "source", "col", "area", "input", "meta", "link")
    private val FEED_TYPES = setOf("application/rss+xml", "application/atom+xml", "application/rdf+xml")
    private val LINK_TAG = Regex("(?is)<link\\b[^>]*>")
    private val ATTRIBUTE = Regex("([a-zA-Z-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
    private val LOOSE_RFC_822 = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM yyyy H:mm[:ss] xx").toFormatter(Locale.ENGLISH)
    private val ZONES = mapOf(
        "GMT" to "+0000", "UT" to "+0000", "UTC" to "+0000", "Z" to "+0000",
        "EST" to "-0500", "EDT" to "-0400", "CST" to "-0600", "CDT" to "-0500",
        "MST" to "-0700", "MDT" to "-0600", "PST" to "-0800", "PDT" to "-0700",
        "CET" to "+0100", "CEST" to "+0200",
    )
}
