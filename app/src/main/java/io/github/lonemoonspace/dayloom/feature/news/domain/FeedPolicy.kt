package io.github.lonemoonspace.dayloom.feature.news.domain

import java.net.URI
import java.net.URISyntaxException
import java.time.Duration

/**
 * Pure decisions for feeds read on the phone: which addresses are usable, how an item becomes a stored article, and when
 * articles go. Read and starred state lives only here, so nothing is ever pushed anywhere.
 * 手机直接读取订阅源时的纯判定：哪些地址可用、一条内容怎样成为本地文章、文章何时删除。已读与收藏只存在本机，从不推送到任何地方。
 */
object FeedPolicy {
    /** The owner recorded for articles that came from feeds; never a URL, so it cannot clash with a Miniflux server. / 来自订阅源的文章记录的归属；不是网址，不会与 Miniflux 服务器混淆。 */
    const val OWNER = "rss"

    const val MAX_FEEDS = 50

    /** Newest items kept from each feed per sync. / 每次同步每个订阅源最多保留的最新条数。 */
    const val ITEMS_PER_FEED = 30

    /** Feeds fetched at the same time. / 同时抓取的订阅源个数。 */
    const val PARALLEL_FETCHES = 4

    /** Unread, unstarred articles older than this go, and older items are not added: a feed is news, not an archive. / 早于这个时长的未读且未收藏的文章会删除，更早的条目也不再加入：订阅源是新闻，不是档案。 */
    val MAX_AGE: Duration = Duration.ofDays(14)

    /**
     * A feed's newest articles stay whatever their age, so a blog that posts once a month is not empty right after it is
     * added.
     * 每个订阅源最新的几篇不论多旧都保留，这样一个月才更新一次的博客刚订阅时不会是空的。
     */
    const val KEEP_LATEST = 3

    /**
     * A feed address the user typed or imported: trimmed, https only (Android blocks clear text and nothing else could
     * vouch for the content); an address without a scheme gets https. The host is lower-cased so one feed is not followed
     * twice; path and query are kept as typed, since servers may tell them apart. Null when unusable.
     * 用户输入或导入的订阅地址：去掉首尾空白，只接受 https（Android 会拦截明文连接）；没写协议的补上 https。主机名转成小写，同一个源
     * 不会被订阅两次；路径与查询参数原样保留，因为服务器可能区分大小写。无法使用时为 null。
     */
    fun normalizeFeedUrl(text: String): String? {
        val typed = text.trim()
        if (typed.isEmpty() || NewsSyncPolicy.isPlainHttp(typed)) return null
        val withScheme = if ("://" in typed) typed else "https://$typed"
        if (!withScheme.startsWith("https://", ignoreCase = true)) return null
        val uri = try {
            URI(withScheme)
        } catch (_: URISyntaxException) {
            return null
        }
        val host = uri.host ?: return null
        if (host.isBlank() || '.' !in host) return null
        val rest = withScheme.substring("https://".length)
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        return "https://" + rest.substring(0, authorityEnd).lowercase() + rest.substring(authorityEnd)
    }

    /**
     * Adds [incoming] to [current], skipping addresses that are unusable or already there and stopping at [MAX_FEEDS].
     * 把 [incoming] 加进 [current]：跳过不可用或已存在的地址，到 [MAX_FEEDS] 为止。
     */
    fun add(current: List<Feed>, incoming: List<Feed>): List<Feed> {
        val result = current.toMutableList()
        val known = current.mapTo(HashSet()) { it.url }
        for (feed in incoming) {
            if (result.size >= MAX_FEEDS) break
            val url = normalizeFeedUrl(feed.url) ?: continue
            if (known.add(url)) result += Feed(url, feed.title.trim().ifEmpty { hostOf(url) })
        }
        return result
    }

    /** The host shown under a feed's name. / 订阅源名称下面显示的主机名。 */
    fun hostOf(url: String): String = try {
        URI(url).host.orEmpty().removePrefix("www.")
    } catch (_: URISyntaxException) {
        ""
    }

    /**
     * Article id: the top 24 bits name the feed, the low 39 the item within it, so the articles of a removed feed can be
     * found without another column, and the same item keeps its id across syncs. Always positive.
     * 文章 id：高 24 位表示订阅源，低 39 位表示源里的这一条；不用另加一列就能找出被删订阅源的文章，同一条内容每次同步 id 都不变。总是正数。
     */
    fun articleId(feedUrl: String, itemKey: String): Long = (feedTag(feedUrl) shl ITEM_BITS) or (fnv(itemKey) and ITEM_MASK)

    fun feedTag(feedUrl: String): Long = fnv(feedUrl) ushr (64 - FEED_BITS)

    fun feedTagOf(articleId: Long): Long = articleId ushr ITEM_BITS

    /**
     * The newest [ITEMS_PER_FEED] items of a feed, the only ones [merge] can keep; an undated item counts as new, as merge
     * dates it now. Trimming right after parsing keeps fifty feeds' worth of article bodies out of memory.
     * 一个订阅源最新的 [ITEMS_PER_FEED] 条，也就是 [merge] 唯一可能保留的那些；没有日期的条目算作新的，因为合并时会把它记为现在。
     * 解析后立刻裁剪，五十个源的正文不会同时占着内存。
     */
    fun newest(feed: ParsedFeed, nowMillis: Long): ParsedFeed =
        feed.copy(items = feed.items.distinctBy { it.key }.sortedByDescending { it.publishedAt.takeIf { t -> t > 0 } ?: nowMillis }.take(ITEMS_PER_FEED))

    /**
     * Merges freshly read feeds into the stored articles. A known item keeps its read, starred and summary state and gets
     * the feed's current text; a new one comes in unread unless it is older than [MAX_AGE] and not among the feed's
     * [KEEP_LATEST] newest. Unstarred articles go: all of a feed no longer followed; unread ones older than [MAX_AGE], even
     * while the feed still lists them (they are not added back); read ones [NewsSyncPolicy.KEEP_READ] after reading once
     * the feed has dropped them, since deleting one it still lists would bring it back unread. A feed's [KEEP_LATEST]
     * newest stored articles stay either way, read or not: an old one deleted here would be added again, unread, by the
     * rule above. Feeds missing from [fetched] (failed or unchanged) are cleaned up by the same rules but get nothing
     * written. Pending flags are cleared: there is no server to send them to. Only the articles of the feeds in [fetched]
     * need their text in [local]; the others are never written back.
     * 把刚读到的订阅源合并进本地文章。已有的条目保留已读、收藏与摘要，正文换成订阅源当前的内容；新条目以未读加入，早于 [MAX_AGE]
     * 且不在该源最新 [KEEP_LATEST] 篇之内的除外。未收藏的文章会删除：不再订阅的源的全部文章；超过 [MAX_AGE] 的未读文章，即使
     * 订阅源还列着（之后也不会再加回来）；已读的在订阅源不再列出、且读过 [NewsSyncPolicy.KEEP_READ] 之后——订阅源还列着时删掉，
     * 下次会以未读的身份回来。每个源本地最新的 [KEEP_LATEST] 篇不论已读未读都留着：旧文章在这里删掉，会被上面那条规则当成
     * 新文章以未读加回来。不在 [fetched] 里的源（失败或没有变化）按同样的规则清理，但不写入任何内容。待发送标记一律清除：
     * 没有服务器可发。[local] 里只有 [fetched] 中那些源的文章需要带正文；其余的不会被写回。
     */
    fun merge(local: List<StoredArticle>, fetched: Map<String, ParsedFeed>, feeds: List<Feed>, nowMillis: Long): MergeResult {
        val byId = local.associateBy { it.id }
        val followed = feeds.mapTo(HashSet()) { feedTag(it.url) }
        val seen = HashSet<Long>()
        val upsert = mutableListOf<StoredArticle>()
        val delete = mutableListOf<Long>()
        val clearPending = mutableListOf<Long>()
        val maxAge = MAX_AGE.toMillis()

        for (feed in feeds) {
            val parsed = fetched[feed.url] ?: continue
            for ((rank, item) in newest(parsed, nowMillis).items.withIndex()) {
                val id = articleId(feed.url, item.key)
                if (id in seen) continue
                val old = byId[id]
                val published = item.publishedAt.takeIf { it > 0 } ?: old?.publishedAt ?: nowMillis
                // Too old to add, and an old unread one is left for the cleanup below. / 太旧的不加入；旧的未读文章交给下面的清理。
                if (rank >= KEEP_LATEST && nowMillis - published > maxAge && (old == null || (!old.read && !old.starred))) continue
                seen += id
                val article = StoredArticle(
                    id = id,
                    feedTitle = feed.title.ifBlank { parsed.title },
                    title = NewsSyncPolicy.plainText(item.title, TITLE_CHARS).replace('\n', ' '),
                    url = item.link,
                    author = item.author,
                    contentHtml = item.html,
                    publishedAt = published,
                    read = old?.read ?: false,
                    starred = old?.starred ?: false,
                    readAt = old?.readAt ?: 0,
                    summary = old?.summary.orEmpty(),
                    summaryLanguage = old?.summaryLanguage.orEmpty(),
                    // A feed has no server to fetch the full text from, so never ask. / 订阅源没有可以抓全文的服务器，所以从不请求。
                    fullTextTriedAt = NewsSyncPolicy.FULL_TEXT_SHORT,
                )
                if (article != old) upsert += article
            }
        }

        // The same exemption for feeds not read this round: without it the next unchanged sync would drop what was kept,
        // and the sync after that would add it again as unread.
        // 本轮没读到的源也按同样的规则豁免：否则下一次内容没变的同步会把刚才保留的删掉，再下一次又把它当未读加回来。
        val latest = local.groupBy { feedTagOf(it.id) }.values
            .flatMapTo(HashSet()) { articles -> articles.sortedByDescending { it.publishedAt }.take(KEEP_LATEST).map { it.id } }

        for (old in local) {
            if (old.id in seen) continue
            val unfollowed = feedTagOf(old.id) !in followed
            val readExpired = old.read && old.id !in latest && nowMillis - old.readAt > NewsSyncPolicy.KEEP_READ.toMillis()
            val unreadExpired = !old.read && old.id !in latest && nowMillis - old.publishedAt > maxAge
            when {
                !old.starred && (unfollowed || readExpired || unreadExpired) -> delete += old.id
                old.pendingRead != null || old.pendingStarred != null -> clearPending += old.id
            }
        }
        return MergeResult(upsert, delete, clearPending)
    }

    /** Names of the followed feeds whose last read failed, in list order. / 上一次读取失败的订阅源名称，按列表顺序。 */
    fun failing(feeds: List<Feed>, states: List<FeedState>): List<String> {
        val failed = states.filter { it.error != null }.mapTo(HashSet()) { it.url }
        return feeds.filter { it.url in failed }.map { it.title.ifBlank { hostOf(it.url) } }
    }

    /** 64-bit FNV-1a over UTF-8: stable across runs and devices, unlike [String.hashCode] widened. / UTF-8 上的 64 位 FNV-1a：跨运行、跨设备稳定。 */
    private fun fnv(text: String): Long {
        var hash = -0x340d631b7bdddcdbL
        for (b in text.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (b.toLong() and 0xff)
            hash *= 0x100000001b3L
        }
        return hash
    }

    private const val FEED_BITS = 24
    private const val ITEM_BITS = 39
    private const val ITEM_MASK = (1L shl ITEM_BITS) - 1
    private const val TITLE_CHARS = 500
}
