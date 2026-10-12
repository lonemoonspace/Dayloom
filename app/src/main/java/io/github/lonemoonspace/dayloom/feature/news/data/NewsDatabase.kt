package io.github.lonemoonspace.dayloom.feature.news.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleFilter
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleStore
import io.github.lonemoonspace.dayloom.feature.news.domain.Headline
import io.github.lonemoonspace.dayloom.feature.news.domain.MergeResult
import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.StoredArticle
import io.github.lonemoonspace.dayloom.feature.news.domain.SyncSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * One article row; the id is Miniflux's entry id. Table and column names are frozen from v1.0.0 (a change needs a
 * migration with a test, design §6.1).
 * 一篇文章一行；id 是 Miniflux 的条目 id。表名与列名从 v1.0.0 起冻结（修改需要带测试的迁移，设计文档 §6.1）。
 */
@Entity(
    tableName = "article",
    // The lists filter on read/starred and sort by date. / 列表按已读/收藏过滤、按日期排序。
    indices = [Index("read", "publishedAt"), Index("starred", "publishedAt"), Index("publishedAt")],
)
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
    @ColumnInfo(defaultValue = "0") val fullTextTriedAt: Long = 0,
)

data class ArticleHead(val id: Long, val feedTitle: String, val title: String, val publishedAt: Long)

data class ArticleCounts(val total: Int, val unread: Int, val starred: Int)

/** Just the pending changes, for the push. / 只取待发送的修改，供推送用。 */
data class ArticlePending(val id: Long, val pendingRead: Boolean?, val pendingStarred: Boolean?)

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

    // KSP needs a plain literal here, so the limit repeats [NewsSyncPolicy.RECENT_LIMIT]. / KSP 只接受纯字面量，所以这里重复 [NewsSyncPolicy.RECENT_LIMIT]。
    @Query("SELECT * FROM article ORDER BY publishedAt DESC LIMIT 300")
    fun recent(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM article WHERE id = :id")
    fun byId(id: Long): Flow<ArticleEntity?>

    /** Counted in SQL, so a tap does not read the whole table back. / 在 SQL 里计数，点一下不会把整张表读回来。 */
    @Query("SELECT COUNT(*) AS total, COALESCE(SUM(read = 0), 0) AS unread, COALESCE(SUM(starred = 1), 0) AS starred FROM article")
    fun counts(): Flow<ArticleCounts>

    @Query("SELECT id, feedTitle, title, publishedAt FROM article WHERE read = 0 ORDER BY publishedAt DESC LIMIT :limit")
    fun latestUnread(limit: Int): Flow<List<ArticleHead>>

    @Query("SELECT id, pendingRead, pendingStarred FROM article WHERE pendingRead IS NOT NULL OR pendingStarred IS NOT NULL")
    suspend fun pending(): List<ArticlePending>

    @Query("DELETE FROM article")
    suspend fun clear()

    @Query("UPDATE article SET fullTextTriedAt = :at WHERE id = :id")
    suspend fun markFullTextTried(id: Long, at: Long)

    @Query("UPDATE article SET read = :read, pendingRead = :read, readAt = :readAt WHERE id = :id")
    suspend fun markRead(id: Long, read: Boolean, readAt: Long)

    @Query("UPDATE article SET starred = :starred, pendingStarred = :starred WHERE id = :id")
    suspend fun markStarred(id: Long, starred: Boolean)

    @Query("UPDATE article SET summary = :summary, summaryLanguage = :language WHERE id = :id")
    suspend fun saveSummary(id: Long, summary: String, language: String)

    @Query("UPDATE article SET contentHtml = :html WHERE id = :id")
    suspend fun saveContent(id: Long, html: String)
}

/** `dayloom.db`, version 2; its schema is exported to `app/schemas/`. / `dayloom.db` 第 2 版；schema 导出到 `app/schemas/`。 */
@Database(entities = [ArticleEntity::class], version = 2, exportSchema = true)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun articles(): ArticleDao

    companion object {
        /** Every migration, in order; the app must pass all of them to the builder. / 全部迁移，按顺序；App 必须全部交给 builder。 */
        val MIGRATIONS: Array<Migration> = arrayOf(Migration1To2)
    }
}

/**
 * 1 → 2: the list indices and the persisted full-text attempt. Existing rows count as "not tried".
 * 1 → 2：列表索引与持久化的抓全文记录。已有的行视为「没试过」。
 */
object Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `article` ADD COLUMN `fullTextTriedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_read_publishedAt` ON `article` (`read`, `publishedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_starred_publishedAt` ON `article` (`starred`, `publishedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_publishedAt` ON `article` (`publishedAt`)")
    }
}

class RoomArticleStore(private val dao: ArticleDao) : ArticleStore {
    override suspend fun all(): List<StoredArticle> = dao.all().map { it.toArticle() }

    override suspend fun pending(): List<StoredArticle> = dao.pending().map { StoredArticle(it.id, pendingRead = it.pendingRead, pendingStarred = it.pendingStarred) }

    override suspend fun summary(): SyncSummary = observeSummary().first()

    override suspend fun clear() = dao.clear()

    override suspend fun markFullTextTried(id: Long, at: Long) = dao.markFullTextTried(id, at)

    override suspend fun apply(result: MergeResult) = dao.apply(result.upsert.map { it.toEntity() }, result.delete)

    override fun observe(filter: ArticleFilter): Flow<List<StoredArticle>> = when (filter) {
        ArticleFilter.UNREAD -> dao.unread()
        ArticleFilter.STARRED -> dao.starred()
        ArticleFilter.ALL -> dao.recent()
    }.map { rows -> rows.map { it.toArticle() } }

    override fun observe(id: Long): Flow<StoredArticle?> = dao.byId(id).map { it?.toArticle() }

    override fun observeSummary(): Flow<SyncSummary> = combine(dao.counts(), dao.latestUnread(NewsSyncPolicy.HEADLINES)) { counts, latest ->
        SyncSummary(
            unread = counts.unread,
            starred = counts.starred,
            latest = latest.map { Headline(it.id, it.title, it.feedTitle, it.publishedAt) },
            total = counts.total,
        )
    }

    override suspend fun markRead(id: Long, read: Boolean, readAt: Long) = dao.markRead(id, read, readAt)

    override suspend fun markStarred(id: Long, starred: Boolean) = dao.markStarred(id, starred)

    override suspend fun saveSummary(id: Long, summary: String, language: String) = dao.saveSummary(id, summary, language)

    override suspend fun saveContent(id: Long, html: String) = dao.saveContent(id, html)
}

// Named arguments: with positional ones, reordering a field would silently write it into another column.
// 用具名参数：按位置传参时，调换一个字段的顺序会把它静默写进另一列。
private fun ArticleEntity.toArticle() = StoredArticle(
    id = id, feedTitle = feedTitle, title = title, url = url, author = author, contentHtml = contentHtml, publishedAt = publishedAt,
    read = read, starred = starred, readingMinutes = readingMinutes, pendingRead = pendingRead, pendingStarred = pendingStarred,
    readAt = readAt, summary = summary, summaryLanguage = summaryLanguage, fullTextTriedAt = fullTextTriedAt,
)

private fun StoredArticle.toEntity() = ArticleEntity(
    id = id, feedTitle = feedTitle, title = title, url = url, author = author, contentHtml = contentHtml, publishedAt = publishedAt,
    read = read, starred = starred, readingMinutes = readingMinutes, pendingRead = pendingRead, pendingStarred = pendingStarred,
    readAt = readAt, summary = summary, summaryLanguage = summaryLanguage, fullTextTriedAt = fullTextTriedAt,
)
