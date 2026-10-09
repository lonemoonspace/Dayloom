package io.github.lonemoonspace.dayloom.feature.news.domain

import kotlinx.serialization.Serializable

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
    /** AI summary and the language it was written in; kept across syncs. / AI 摘要及其语言；同步时保留。 */
    val summary: String = "",
    val summaryLanguage: String = "",
)

enum class ArticleFilter { UNREAD, STARRED, ALL }

/** A headline for the home card. / 首页卡片上的一条标题。 */
@Serializable
data class Headline(val id: Long = 0, val title: String = "", val feed: String = "", val publishedAt: Long = 0)

/** The snapshot of `news.sync`: what the home card shows; the articles themselves live in Room. / `news.sync` 的快照：首页卡片显示的内容；文章本身在 Room 里。 */
@Serializable
data class SyncSummary(val unread: Int = 0, val starred: Int = 0, val latest: List<Headline> = emptyList())
