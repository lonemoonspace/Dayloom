package io.github.lonemoonspace.dayloom.feature.news.domain

import kotlinx.serialization.Serializable

/**
 * One of the server's lists (unread, starred). [complete] is false when it was cut off at [NewsSyncPolicy.FETCH_LIMIT]:
 * then an article missing from it says nothing about that article's state. Not derived from the size, because the same
 * entry can turn up on two pages and be kept once.
 * 服务器的一个列表（未读、收藏）。在 [NewsSyncPolicy.FETCH_LIMIT] 处被截断时 [complete] 为 false：此时某篇文章不在列表里，
 * 并不能说明它的状态。不按条数推断，因为同一条目可能出现在两页、只保留一次。
 */
data class RemoteList(val entries: List<RemoteEntry> = emptyList(), val complete: Boolean = true)

/**
 * An article as Miniflux sends it. / Miniflux 发来的一篇文章。
 */
data class RemoteEntry(
    val id: Long,
    val feedTitle: String,
    val title: String,
    val url: String,
    val author: String,
    val contentHtml: String,
    /** Epoch millis. / epoch 毫秒。 */
    val publishedAt: Long,
    val read: Boolean,
    val starred: Boolean,
    val readingMinutes: Int,
)

/**
 * An article kept on the phone. [pendingRead] / [pendingStarred] hold a change made here that Miniflux has not confirmed
 * yet; while set, it wins over what the server says, so reading offline is not undone by the next sync.
 * 保存在手机上的一篇文章。[pendingRead] / [pendingStarred] 是在本机做出、Miniflux 尚未确认的修改；设置期间以它为准，
 * 离线时读过的文章不会被下一次同步改回去。
 */
data class StoredArticle(
    val id: Long,
    val feedTitle: String = "",
    val title: String = "",
    val url: String = "",
    val author: String = "",
    val contentHtml: String = "",
    val publishedAt: Long = 0,
    val read: Boolean = false,
    val starred: Boolean = false,
    val readingMinutes: Int = 0,
    val pendingRead: Boolean? = null,
    val pendingStarred: Boolean? = null,
    /** When it was first seen read here, epoch millis; 0 while unread. Read articles expire from this. / 在本机首次被看作已读的时刻（epoch 毫秒），未读时为 0；已读文章按它过期。 */
    val readAt: Long = 0,
    /** AI summary and the language it was written in; kept across syncs. / AI 摘要及其语言；同步时保留。 */
    val summary: String = "",
    val summaryLanguage: String = "",
    /**
     * Last failed full-text fetch, epoch millis; 0 = not tried, [NewsSyncPolicy.FULL_TEXT_SHORT] = the server's own text is
     * this short, so it is never asked for again. Kept in the database so a restart does not retry everything.
     * 上次抓全文失败的时刻（epoch 毫秒）；0 = 没试过，[NewsSyncPolicy.FULL_TEXT_SHORT] = 服务器上的全文本来就这么短，不再请求。
     * 存在数据库里，重启后不会全部重试一遍。
     */
    val fullTextTriedAt: Long = 0,
)

enum class ArticleFilter { UNREAD, STARRED, ALL }

/** A headline for the home card. / 首页卡片上的一条标题。 */
@Serializable
data class Headline(val id: Long = 0, val title: String = "", val feed: String = "", val publishedAt: Long = 0)

/** The snapshot of `news.sync`: what the home card shows; the articles themselves live in Room. / `news.sync` 的快照：首页卡片显示的内容；文章本身在 Room 里。 */
@Serializable
data class SyncSummary(
    val unread: Int = 0,
    val starred: Int = 0,
    val latest: List<Headline> = emptyList(),
    val total: Int = 0,
    /** The last sync could not send the changes made here (read-only token, a proxy refusing PUT…); they stay pending. / 上一次同步没能发出本机的修改（只读令牌、代理拒绝 PUT……）；修改仍待发送。 */
    val pushRejected: Boolean = false,
)

/** An article with what the list shows of it; [text] is its plain text, for search. / 文章及其在列表里显示的内容；[text] 是纯文本，供搜索用。 */
data class ArticleItem(val article: StoredArticle, val preview: String, val text: String, val fullText: Boolean)
