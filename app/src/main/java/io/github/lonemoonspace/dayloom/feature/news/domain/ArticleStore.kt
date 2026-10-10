package io.github.lonemoonspace.dayloom.feature.news.domain

import kotlinx.coroutines.flow.Flow

/**
 * Where articles are kept on the phone; Room in the app, a map in tests.
 * 文章在手机上的存放处；App 里是 Room，测试里是一个 map。
 */
interface ArticleStore {
    suspend fun all(): List<StoredArticle>

    suspend fun apply(result: MergeResult)

    fun observe(filter: ArticleFilter): Flow<List<StoredArticle>>

    fun observe(id: Long): Flow<StoredArticle?>

    /** Counts and newest headlines, live, for the home card and the filter chips. / 实时的数量与最新标题，供首页卡片与筛选标签使用。 */
    fun observeSummary(): Flow<SyncSummary>

    /** Marks read or unread here, to be sent to Miniflux; [readAt] is 0 for unread. / 在本机标为已读或未读，稍后发给 Miniflux；未读时 [readAt] 为 0。 */
    suspend fun markRead(id: Long, read: Boolean, readAt: Long)

    suspend fun markStarred(id: Long, starred: Boolean)

    suspend fun saveSummary(id: Long, summary: String, language: String)
}
