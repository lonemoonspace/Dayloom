package io.github.lonemoonspace.dayloom.feature.news.domain

import java.time.Duration

/** What to tell Miniflux before merging. / 合并之前要告诉 Miniflux 的修改。 */
data class PushPlan(val markRead: List<Long>, val markUnread: List<Long>, val toggleStar: List<Long>) {
    val isEmpty: Boolean get() = markRead.isEmpty() && markUnread.isEmpty() && toggleStar.isEmpty()
}

data class MergeResult(val upsert: List<StoredArticle>, val delete: List<Long>)

/**
 * Pure decisions of the news sync: which local changes to send, and how the server's lists and the local rows combine.
 * 新闻同步的纯判定：要发送哪些本地修改，以及服务器的列表与本地记录如何合并。
 */
object NewsSyncPolicy {
    /** Unread and starred entries fetched per sync; Miniflux pages beyond this are left for later. / 每次同步取的未读与收藏条数；更多的留到以后。 */
    const val FETCH_LIMIT = 200

    /** Read, unstarred articles are kept this long so "All" still has something to show. / 已读且未收藏的文章保留这么久，「全部」里才有东西看。 */
    val KEEP_READ: Duration = Duration.ofDays(14)

    /**
     * Miniflux's bookmark call toggles, so a star is only sent when the server's state differs from the wanted one.
     * [remoteStarred] are the ids the server listed as starred in this sync.
     * Miniflux 的收藏接口是切换式的，所以只有服务器状态与想要的不同时才发送。[remoteStarred] 是本次同步服务器列为已收藏的 id。
     */
    fun pushPlan(local: List<StoredArticle>, remoteStarred: Set<Long>): PushPlan = PushPlan(
        markRead = local.filter { it.pendingRead == true }.map { it.id },
        markUnread = local.filter { it.pendingRead == false }.map { it.id },
        toggleStar = local.filter { it.pendingStarred != null && it.pendingStarred != (it.id in remoteStarred) }.map { it.id },
    )

    /**
     * A local change wins until it has been pushed; after a successful push it is the truth and stops being pending. Remote
     * entries refresh the stored text but keep the summary. An article that left a complete server list without a local
     * change was read (or unstarred) elsewhere; a list cut off at [FETCH_LIMIT] proves nothing about the rest. Old read,
     * unstarred articles go.
     * 本地修改在推送之前优先；推送成功后它就是事实，不再待发送。服务器的条目更新本地正文，但保留摘要。没有本地修改、却从完整的
     * 服务器列表里消失的文章，说明在别处读过（或取消了收藏）；被 [FETCH_LIMIT] 截断的列表对其余文章说明不了什么。旧的已读且未收藏
     * 的文章删除。
     */
    fun merge(
        local: List<StoredArticle>,
        unread: List<RemoteEntry>,
        starred: List<RemoteEntry>,
        pushed: Boolean,
        nowMillis: Long,
    ): MergeResult {
        val byId = local.associateBy { it.id }
        val remote = (unread + starred).associateBy { it.id }
        val starredIds = starred.mapTo(mutableSetOf()) { it.id }
        val unreadComplete = unread.size < FETCH_LIMIT
        val starredComplete = starred.size < FETCH_LIMIT
        val upsert = mutableListOf<StoredArticle>()
        val delete = mutableListOf<Long>()

        for (entry in remote.values) {
            val old = byId[entry.id]
            upsert += StoredArticle(
                id = entry.id,
                feedTitle = entry.feedTitle,
                title = entry.title,
                url = entry.url,
                author = entry.author,
                contentHtml = entry.contentHtml,
                publishedAt = entry.publishedAt,
                read = old?.pendingRead ?: entry.read,
                starred = old?.pendingStarred ?: (entry.id in starredIds),
                readingMinutes = entry.readingMinutes,
                pendingRead = old?.pendingRead.takeUnless { pushed },
                pendingStarred = old?.pendingStarred.takeUnless { pushed },
                summary = old?.summary.orEmpty(),
                summaryLanguage = old?.summaryLanguage.orEmpty(),
            )
        }

        for (old in local) {
            if (old.id in remote) continue
            val read = old.pendingRead ?: if (unreadComplete) true else old.read
            val starred = old.pendingStarred ?: if (starredComplete) false else old.starred
            val pendingRead = old.pendingRead.takeUnless { pushed }
            val pendingStarred = old.pendingStarred.takeUnless { pushed }
            val expired = read && !starred && nowMillis - old.publishedAt > KEEP_READ.toMillis()
            when {
                expired && pendingRead == null && pendingStarred == null -> delete += old.id
                read != old.read || starred != old.starred || pendingRead != old.pendingRead || pendingStarred != old.pendingStarred ->
                    upsert += old.copy(read = read, starred = starred, pendingRead = pendingRead, pendingStarred = pendingStarred)
            }
        }
        return MergeResult(upsert, delete)
    }

    /** The home card's view of the stored articles. / 首页卡片眼中的本地文章。 */
    fun summary(articles: List<StoredArticle>, headlines: Int = 3): SyncSummary = SyncSummary(
        unread = articles.count { !it.read },
        starred = articles.count { it.starred },
        latest = articles.filter { !it.read }.sortedByDescending { it.publishedAt }.take(headlines)
            .map { Headline(it.id, it.title, it.feedTitle, it.publishedAt) },
    )

    /**
     * A base URL the user typed: trimmed, without a trailing slash, https only (the token must not travel in clear text);
     * null when unusable.
     * 用户输入的基础地址：去掉首尾空白与末尾斜杠，只接受 https（令牌不能明文传输）；无法使用时为 null。
     */
    fun normalizeBaseUrl(text: String): String? {
        val trimmed = text.trim().trimEnd('/')
        if (!trimmed.startsWith("https://", ignoreCase = true)) return null
        val host = trimmed.substring("https://".length).substringBefore('/').substringBefore(':')
        if (host.isBlank() || host.contains(' ')) return null
        return trimmed
    }

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
}
