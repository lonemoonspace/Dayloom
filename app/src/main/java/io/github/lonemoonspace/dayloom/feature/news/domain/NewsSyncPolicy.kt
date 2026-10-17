package io.github.lonemoonspace.dayloom.feature.news.domain

import java.time.Duration

/**
 * What to tell Miniflux before merging, and exactly which values were sent: after a successful push a pending change is
 * cleared only if it still holds the value that went out, so a tap made during the sync survives it.
 * 合并之前要告诉 Miniflux 的修改，以及具体发出了哪些值：推送成功后，只有仍等于已发出值的待发送修改才会清除，同步期间的点按因此不会丢。
 */
data class PushPlan(
    val markRead: List<Long>,
    val markUnread: List<Long>,
    val toggleStar: List<Long>,
    val sentRead: Map<Long, Boolean>,
    val sentStarred: Map<Long, Boolean>,
)

data class MergeResult(val upsert: List<StoredArticle>, val delete: List<Long>)

/**
 * Pure decisions of the news sync: which local changes to send, and how the server's lists and the local rows combine.
 * 新闻同步的纯判定：要发送哪些本地修改，以及服务器的列表与本地记录如何合并。
 */
object NewsSyncPolicy {
    /**
     * Most unread (and most starred) entries fetched per sync, newest first. A list that reaches it counts as cut off, so the
     * older entries are neither shown nor taken as read elsewhere.
     * 每次同步最多取的未读（与收藏）条数，新的在前。达到这个数的列表视为被截断，更早的条目既不显示，也不会被当成已在别处读过。
     */
    const val FETCH_LIMIT = 1000

    /** Entries per Miniflux request; [FETCH_LIMIT] takes a few pages. / 每次请求 Miniflux 取的条数；取满 [FETCH_LIMIT] 要翻几页。 */
    const val PAGE_SIZE = 200

    /** Read, unstarred articles are kept this long after being read, so "All" still has something to show. / 已读且未收藏的文章在读过之后保留这么久，「全部」里才有东西看。 */
    val KEEP_READ: Duration = Duration.ofDays(14)

    /**
     * Read states go out as they are. Miniflux's bookmark call toggles, so a star is only sent when the server's state is
     * known and differs: from the entry itself when the server listed it, else from a complete starred list; a star whose
     * server state is unknown waits for a later sync.
     * 已读状态按原样发送。Miniflux 的收藏接口是切换式的，所以只有确知服务器状态且与想要的不同时才发送：服务器列出了该条目就看
     * 条目本身，否则看完整的收藏列表；服务器状态未知的收藏留到以后的同步。
     */
    fun pushPlan(local: List<StoredArticle>, unread: List<RemoteEntry>, starred: List<RemoteEntry>): PushPlan {
        val remote = (unread + starred).associate { it.id to it.starred }
        val starredComplete = starred.size < FETCH_LIMIT
        val reads = local.mapNotNull { a -> a.pendingRead?.let { a.id to it } }.toMap()
        val stars = local.mapNotNull { a ->
            val wanted = a.pendingStarred ?: return@mapNotNull null
            val server = remote[a.id] ?: if (starredComplete) false else return@mapNotNull null
            a.id to (wanted to server)
        }.toMap()
        return PushPlan(
            markRead = reads.filterValues { it }.keys.toList(),
            markUnread = reads.filterValues { !it }.keys.toList(),
            toggleStar = stars.filterValues { (wanted, server) -> wanted != server }.keys.toList(),
            sentRead = reads,
            sentStarred = stars.mapValues { it.value.first },
        )
    }

    /**
     * A local change wins until the value it holds has been pushed. Remote entries refresh the stored text but keep the
     * summary. An article that left a complete server list without a local change was read (or unstarred) elsewhere; a list
     * cut off at [FETCH_LIMIT] proves nothing about the rest. Read, unstarred articles go [KEEP_READ] after they were read.
     * 本地修改在它的值被推送之前一直优先。服务器的条目更新本地正文，但保留摘要。没有本地修改、却从完整的服务器列表里消失的文章，
     * 说明在别处读过（或取消了收藏）；被 [FETCH_LIMIT] 截断的列表对其余文章说明不了什么。已读且未收藏的文章在读过 [KEEP_READ] 后删除。
     */
    fun merge(
        local: List<StoredArticle>,
        unread: List<RemoteEntry>,
        starred: List<RemoteEntry>,
        sentRead: Map<Long, Boolean>,
        sentStarred: Map<Long, Boolean>,
        nowMillis: Long,
    ): MergeResult {
        val byId = local.associateBy { it.id }
        val remote = (unread + starred).associateBy { it.id }
        val unreadComplete = unread.size < FETCH_LIMIT
        val starredComplete = starred.size < FETCH_LIMIT
        val upsert = mutableListOf<StoredArticle>()
        val delete = mutableListOf<Long>()

        fun readAt(old: StoredArticle?, read: Boolean) = if (!read) 0 else old?.readAt?.takeIf { it > 0 } ?: nowMillis
        fun stillPending(id: Long, pending: Boolean?, sent: Map<Long, Boolean>) = pending.takeUnless { it != null && sent[id] == it }

        for (entry in remote.values) {
            val old = byId[entry.id]
            val read = old?.pendingRead ?: entry.read
            upsert += StoredArticle(
                id = entry.id,
                feedTitle = entry.feedTitle,
                title = entry.title,
                url = entry.url,
                author = entry.author,
                // Full text fetched here outlives a feed that only ever sends an excerpt. / 本机抓到的全文不会被只发摘要的订阅源覆盖。
                contentHtml = if (old != null && isStub(entry.contentHtml) && !isStub(old.contentHtml)) old.contentHtml else entry.contentHtml,
                publishedAt = entry.publishedAt,
                read = read,
                starred = old?.pendingStarred ?: entry.starred,
                readingMinutes = entry.readingMinutes,
                pendingRead = stillPending(entry.id, old?.pendingRead, sentRead),
                pendingStarred = stillPending(entry.id, old?.pendingStarred, sentStarred),
                readAt = readAt(old, read),
                summary = old?.summary.orEmpty(),
                summaryLanguage = old?.summaryLanguage.orEmpty(),
                fullTextTriedAt = old?.fullTextTriedAt ?: 0,
            )
        }

        for (old in local) {
            if (old.id in remote) continue
            val read = old.pendingRead ?: if (unreadComplete) true else old.read
            val starred = old.pendingStarred ?: if (starredComplete) false else old.starred
            val updated = old.copy(
                read = read,
                starred = starred,
                pendingRead = stillPending(old.id, old.pendingRead, sentRead),
                pendingStarred = stillPending(old.id, old.pendingStarred, sentStarred),
                readAt = readAt(old, read),
            )
            val expired = read && !starred && nowMillis - updated.readAt > KEEP_READ.toMillis()
            when {
                expired && updated.pendingRead == null && updated.pendingStarred == null -> delete += old.id
                updated != old -> upsert += updated
            }
        }
        return MergeResult(upsert, delete)
    }

    /** Newest unread headlines on the home card. / 首页卡片上最新的未读标题条数。 */
    const val HEADLINES = 3

    /** Articles the "All" list shows; its count is capped the same way, so the number matches the list. / 「全部」列表显示的篇数；它的计数也按这个封顶，数字与列表一致。 */
    const val RECENT_LIMIT = 300

    /** The home card's view of the stored articles. / 首页卡片眼中的本地文章。 */
    fun summary(articles: List<StoredArticle>, headlines: Int = HEADLINES): SyncSummary = SyncSummary(
        total = articles.size,
        unread = articles.count { !it.read },
        starred = articles.count { it.starred },
        latest = articles.filter { !it.read }.sortedByDescending { it.publishedAt }.take(headlines)
            .map { Headline(it.id, it.title, it.feedTitle, it.publishedAt) },
    )

    /**
     * A base URL the user typed: trimmed, without a trailing slash, https only (the token must not travel in clear text);
     * an address typed without a scheme gets https, since that is what people mean. Null when unusable.
     * 用户输入的基础地址：去掉首尾空白与末尾斜杠，只接受 https（令牌不能明文传输）；没写协议的地址补上 https，因为大家指的就是它。
     * 无法使用时为 null。
     */
    fun normalizeBaseUrl(text: String): String? {
        val typed = text.trim()
        if (typed.isEmpty() || isPlainHttp(typed)) return null
        // The scheme is looked for before the slashes are trimmed, or "https://" would turn into a host named "https:".
        // 先找协议再去掉斜杠，否则「https://」会变成名为「https:」的主机。
        val withScheme = (if ("://" in typed) typed else "https://$typed").trimEnd('/')
        if (!withScheme.startsWith("https://", ignoreCase = true)) return null
        val host = withScheme.substring("https://".length).substringBefore('/').substringBefore(':')
        if (host.isBlank() || host.contains(' ')) return null
        return withScheme
    }

    /** True for an http:// address, which gets its own explanation. / http:// 地址返回 true，界面单独说明原因。 */
    fun isPlainHttp(text: String): Boolean = text.trim().startsWith("http://", ignoreCase = true)

    /**
     * Plain text for the summarizer: tags dropped, entities decoded, whitespace collapsed, cut to [maxChars] so a long
     * article does not cost a fortune.
     * 给摘要用的纯文本：去掉标签、解码实体、合并空白，截到 [maxChars]，长文章不会花掉一大笔费用。
     */
    fun plainText(html: String, maxChars: Int = SUMMARY_INPUT_CHARS): String = html
        .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<br\\s*/?>|</p>|</h[1-6]>|</li>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        .replace(Regex("\\s*\\n\\s*"), "\n")
        .trim()
        .take(maxChars)

    const val SUMMARY_INPUT_CHARS = 12_000

    /** Shorter readable text than this is a feed's excerpt, not the article. / 可读文字短于这个长度的是订阅源的摘要，不是全文。 */
    const val STUB_TEXT_CHARS = 100

    /** Full texts fetched per sync, so a backlog of excerpts does not turn one sync into hundreds of requests. / 每次同步抓取的全文篇数，积压的摘要不会让一次同步变成几百个请求。 */
    const val FULL_TEXT_PER_SYNC = 10

    /** A failed full-text fetch waits this long before it is tried again. / 抓取全文失败后，等这么久再试。 */
    val FULL_TEXT_RETRY: Duration = Duration.ofMinutes(30)

    /**
     * [StoredArticle.fullTextTriedAt] when the server's full text came back no longer than the excerpt: the article really
     * is that short, so asking again would never help.
     * 服务器返回的全文并不比摘要长时的 [StoredArticle.fullTextTriedAt]：文章本来就这么短，再请求也没用。
     */
    const val FULL_TEXT_SHORT = Long.MAX_VALUE

    fun isStub(html: String): Boolean = plainText(html, Int.MAX_VALUE).length < STUB_TEXT_CHARS

    /**
     * Opening an excerpt asks for its full text, unless the server already said the article is that short. A failed attempt
     * is tried again on opening, without the [FULL_TEXT_RETRY] wait: the user is asking for this article now.
     * 打开只有摘要的文章时请求全文，除非服务器已经表明文章本来就这么短。失败过的照样在打开时再试，不等 [FULL_TEXT_RETRY]：用户此刻
     * 就要看这篇。
     */
    fun fetchOnOpen(article: StoredArticle): Boolean = article.fullTextTriedAt != FULL_TEXT_SHORT && isStub(article.contentHtml)

    /**
     * Excerpt-only articles worth fetching in full: unread or starred, not known to be short, not tried in the last
     * [FULL_TEXT_RETRY], newest first.
     * 值得抓取全文的只有摘要的文章：未读或已收藏、不是已知的短文、最近 [FULL_TEXT_RETRY] 内没试过的，最新的在前。
     */
    fun fullTextDue(articles: List<StoredArticle>, nowMillis: Long, limit: Int = FULL_TEXT_PER_SYNC): List<StoredArticle> =
        articles.asSequence()
            .filter { !it.read || it.starred }
            .filter { it.fullTextTriedAt != FULL_TEXT_SHORT && (it.fullTextTriedAt == 0L || nowMillis - it.fullTextTriedAt >= FULL_TEXT_RETRY.toMillis()) }
            .sortedByDescending { it.publishedAt }
            .filter { isStub(it.contentHtml) }
            .take(limit)
            .toList()

    /** The article as the list shows it: a one-paragraph preview, and whether its full text is here. / 列表里显示的文章：一段预览，以及全文是否已在本机。 */
    fun listItem(article: StoredArticle): ArticleItem {
        val text = plainText(article.contentHtml, Int.MAX_VALUE)
        return ArticleItem(article, text.replace('\n', ' ').take(PREVIEW_CHARS), text, text.length >= STUB_TEXT_CHARS)
    }

    /** Case-insensitive match on title, source or text; a blank query matches everything. / 不区分大小写匹配标题、来源或正文；空查询全部匹配。 */
    fun matches(item: ArticleItem, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return item.article.title.contains(q, ignoreCase = true) || item.article.feedTitle.contains(q, ignoreCase = true) ||
            item.text.contains(q, ignoreCase = true)
    }

    private const val PREVIEW_CHARS = 160
}
