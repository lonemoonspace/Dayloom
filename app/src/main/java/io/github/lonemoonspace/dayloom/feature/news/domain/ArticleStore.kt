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

    /** Marks read or unread here, to be sent to Miniflux. / 在本机标为已读或未读，稍后发给 Miniflux。 */
    suspend fun markRead(id: Long, read: Boolean)

    suspend fun markStarred(id: Long, starred: Boolean)

    /** Miniflux confirmed these read states; they stop being pending. / Miniflux 已确认这些已读状态，不再待发送。 */
    suspend fun confirmRead(ids: List<Long>)

    suspend fun saveSummary(id: Long, summary: String, language: String)
}
