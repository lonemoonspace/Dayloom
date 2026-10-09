package io.github.lonemoonspace.dayloom.feature.news.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleFilter
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleStore
import io.github.lonemoonspace.dayloom.feature.news.domain.MergeResult
import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.StoredArticle
import io.github.lonemoonspace.dayloom.feature.news.domain.SyncSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One article row; the id is Miniflux's entry id. Table and column names are frozen from v1.0.0 (a change needs a
 * migration with a test, design §6.1).
 * 一篇文章一行；id 是 Miniflux 的条目 id。表名与列名从 v1.0.0 起冻结（修改需要带测试的迁移，设计文档 §6.1）。
 */
@Entity(tableName = "article")
data class ArticleEntity(
    @PrimaryKey val id: Long,
    val feedTitle: String,
    val title: String,
    val url: String,
    val author: String,
    val contentHtml: String,
    val publishedAt: Long,
    val read: Boolean,
    val starred: Boolean,
    val readingMinutes: Int,
    val pendingRead: Boolean?,
    val pendingStarred: Boolean?,
    val readAt: Long,
    val summary: String,
    val summaryLanguage: String,
)

data class ArticleHead(val id: Long, val feedTitle: String, val title: String, val publishedAt: Long, val read: Boolean, val starred: Boolean)

@Dao
interface ArticleDao {
    @Query("SELECT * FROM article")
    suspend fun all(): List<ArticleEntity>

    @Upsert
    suspend fun upsert(rows: List<ArticleEntity>)

    @Query("DELETE FROM article WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Transaction
    suspend fun apply(upsert: List<ArticleEntity>, delete: List<Long>) {
        if (upsert.isNotEmpty()) upsert(upsert)
        // SQLite caps the number of bound variables, so long lists go in chunks. / SQLite 限制绑定变量数，长列表分批删除。
        delete.chunked(500).forEach { delete(it) }
    }

    @Query("SELECT * FROM article WHERE read = 0 ORDER BY publishedAt DESC")
    fun unread(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM article WHERE starred = 1 ORDER BY publishedAt DESC")
    fun starred(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM article ORDER BY publishedAt DESC LIMIT 300")
    fun recent(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM article WHERE id = :id")
    fun byId(id: Long): Flow<ArticleEntity?>

    /** Just what the home card needs, without the article text. / 只取首页卡片需要的字段，不带正文。 */
    @Query("SELECT id, feedTitle, title, publishedAt, read, starred FROM article")
    fun heads(): Flow<List<ArticleHead>>

    @Query("UPDATE article SET read = :read, pendingRead = :read, readAt = :readAt WHERE id = :id")
    suspend fun markRead(id: Long, read: Boolean, readAt: Long)

    @Query("UPDATE article SET starred = :starred, pendingStarred = :starred WHERE id = :id")
    suspend fun markStarred(id: Long, starred: Boolean)

    @Query("UPDATE article SET summary = :summary, summaryLanguage = :language WHERE id = :id")
    suspend fun saveSummary(id: Long, summary: String, language: String)
}

/** `dayloom.db`, version 1; its schema is exported to `app/schemas/`. / `dayloom.db` 第 1 版；schema 导出到 `app/schemas/`。 */
@Database(entities = [ArticleEntity::class], version = 1, exportSchema = true)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun articles(): ArticleDao
}

class RoomArticleStore(private val dao: ArticleDao) : ArticleStore {
    override suspend fun all(): List<StoredArticle> = dao.all().map { it.toArticle() }

    override suspend fun apply(result: MergeResult) = dao.apply(result.upsert.map { it.toEntity() }, result.delete)

    override fun observe(filter: ArticleFilter): Flow<List<StoredArticle>> = when (filter) {
        ArticleFilter.UNREAD -> dao.unread()
        ArticleFilter.STARRED -> dao.starred()
        ArticleFilter.ALL -> dao.recent()
    }.map { rows -> rows.map { it.toArticle() } }

    override fun observe(id: Long): Flow<StoredArticle?> = dao.byId(id).map { it?.toArticle() }

    override fun observeSummary(): Flow<SyncSummary> = dao.heads().map { heads ->
        NewsSyncPolicy.summary(heads.map { StoredArticle(it.id, it.feedTitle, it.title, publishedAt = it.publishedAt, read = it.read, starred = it.starred) })
    }

    override suspend fun markRead(id: Long, read: Boolean, readAt: Long) = dao.markRead(id, read, readAt)

    override suspend fun markStarred(id: Long, starred: Boolean) = dao.markStarred(id, starred)

    override suspend fun saveSummary(id: Long, summary: String, language: String) = dao.saveSummary(id, summary, language)
}

private fun ArticleEntity.toArticle() = StoredArticle(
    id, feedTitle, title, url, author, contentHtml, publishedAt, read, starred, readingMinutes, pendingRead, pendingStarred, readAt, summary, summaryLanguage,
)

private fun StoredArticle.toEntity() = ArticleEntity(
    id, feedTitle, title, url, author, contentHtml, publishedAt, read, starred, readingMinutes, pendingRead, pendingStarred, readAt, summary, summaryLanguage,
)
