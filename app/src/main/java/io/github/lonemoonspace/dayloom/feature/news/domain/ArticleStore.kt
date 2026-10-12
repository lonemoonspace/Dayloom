package io.github.lonemoonspace.dayloom.feature.news.domain

import kotlinx.coroutines.flow.Flow

/**
 * Where articles are kept on the phone; Room in the app, a map in tests.
 * 文章在手机上的存放处；App 里是 Room，测试里是一个 map。
 */
interface ArticleStore {
    suspend fun all(): List<StoredArticle>

    /** Only the articles with a change not yet sent, without their text. / 只取有未发送修改的文章，不带正文。 */
    suspend fun pending(): List<StoredArticle>

    /** The same numbers as [observeSummary], once. / 与 [observeSummary] 相同的数字，取一次。 */
    suspend fun summary(): SyncSummary

    /** Drops every article, e.g. when they came from another server. / 删除全部文章，比如它们来自另一台服务器时。 */
    suspend fun clear()

    /** Records a full-text attempt, see [StoredArticle.fullTextTriedAt]. / 记录一次抓全文的结果，见 [StoredArticle.fullTextTriedAt]。 */
    suspend fun markFullTextTried(id: Long, at: Long)

    suspend fun apply(result: MergeResult)

    fun observe(filter: ArticleFilter): Flow<List<StoredArticle>>

    fun observe(id: Long): Flow<StoredArticle?>

    /** Counts and newest headlines, live, for the home card and the filter chips. / 实时的数量与最新标题，供首页卡片与筛选标签使用。 */
    fun observeSummary(): Flow<SyncSummary>

    /** Marks read or unread here, to be sent to Miniflux; [readAt] is 0 for unread. / 在本机标为已读或未读，稍后发给 Miniflux；未读时 [readAt] 为 0。 */
    suspend fun markRead(id: Long, read: Boolean, readAt: Long)

    suspend fun markStarred(id: Long, starred: Boolean)

    suspend fun saveSummary(id: Long, summary: String, language: String)

    /** Replaces an article's text with its full text. / 用全文替换文章正文。 */
    suspend fun saveContent(id: Long, html: String)
}

/**
 * The Miniflux server the stored articles came from. Entry ids are per server, so rows and pending changes must never meet
 * another server: a pending read would mark an unrelated article there.
 * 本地文章来自哪台 Miniflux 服务器。条目 id 按服务器各自编号，所以本地行与待发送修改绝不能碰到另一台服务器：待发送的已读会把那边
 * 一篇无关的文章标为已读。
 */
interface ArticleOwner {
    /** Base URL, or blank before the first sync that recorded one. / 基础地址；第一次记录它的同步之前为空。 */
    suspend fun get(): String

    suspend fun set(baseUrl: String)
}
