package io.github.lonemoonspace.dayloom.feature.news

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import io.github.lonemoonspace.dayloom.feature.news.data.NewsDatabase
import io.github.lonemoonspace.dayloom.feature.news.data.RoomArticleStore
import io.github.lonemoonspace.dayloom.feature.news.domain.MergeResult
import io.github.lonemoonspace.dayloom.feature.news.domain.StoredArticle
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `dayloom.db` built from an exported old schema, then opened by the current code: Room checks the migrated tables
 * against the entities, so a wrong migration fails here instead of on a tester's phone.
 * 按导出的旧 schema 建出 `dayloom.db`，再用当前代码打开：Room 会把迁移后的表与实体比对，迁移写错会在这里失败，而不是在测试者手机上。
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application: DayloomApp would build the whole app graph and schedule work. / 用普通 Application：DayloomApp 会构建整个应用图并排期后台任务。
@Config(sdk = [35], application = Application::class)
class NewsDatabaseMigrationTest {

    private val context = RuntimeEnvironment.getApplication()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    /** Creates the database exactly as [version]'s exported schema describes it. / 严格按 [version] 导出的 schema 建库。 */
    private fun createVersion(version: Int, fill: SQLiteDatabase.() -> Unit) {
        val file = File("schemas/${NewsDatabase::class.java.name}/$version.json")
        val database = Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            database.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                db.execSQL(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
            database.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.fill()
            db.version = version
        }
    }

    @Test
    fun `version 1 rows survive the migration and every field round-trips`() = runBlocking {
        createVersion(1) {
            execSQL(
                "INSERT INTO article VALUES (1, 'Feed', 'Old', 'https://example.org/1', 'A', '<p>x</p>', 100, 1, 0, 3, 1, NULL, 200, 'Sum', 'en')",
            )
        }
        val db = Room.databaseBuilder(context, NewsDatabase::class.java, name)
            .addMigrations(*NewsDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val store = RoomArticleStore(db.articles())
            val migrated = store.all().single()
            assertEquals(
                StoredArticle(
                    id = 1, feedTitle = "Feed", title = "Old", url = "https://example.org/1", author = "A", contentHtml = "<p>x</p>",
                    publishedAt = 100, read = true, starred = false, readingMinutes = 3, pendingRead = true, pendingStarred = null,
                    readAt = 200, summary = "Sum", summaryLanguage = "en", fullTextTriedAt = 0,
                ),
                migrated,
            )

            // Every field distinct, so two swapped columns cannot pass. / 每个字段都不同，两列互换就过不了。
            val full = StoredArticle(
                id = 2, feedTitle = "F", title = "T", url = "U", author = "W", contentHtml = "H", publishedAt = 300, read = false,
                starred = true, readingMinutes = 4, pendingRead = false, pendingStarred = true, readAt = 5, summary = "S",
                summaryLanguage = "zh", fullTextTriedAt = 6,
            )
            store.apply(MergeResult(listOf(full), emptyList()))
            assertEquals(full, store.all().single { it.id == 2L })
            assertEquals(listOf(2L), store.pending().filter { it.pendingStarred != null }.map { it.id })

            val summary = store.summary()
            assertEquals(2, summary.total)
            assertEquals(1, summary.unread)
            assertEquals(1, summary.starred)
            assertEquals(listOf(2L), summary.latest.map { it.id })

            store.clear()
            assertEquals(emptyList<StoredArticle>(), store.all())
        } finally {
            db.close()
        }
    }
}
