package io.github.lonemoonspace.dayloom.feature.todo

import io.github.lonemoonspace.dayloom.feature.todo.domain.TodoItem
import io.github.lonemoonspace.dayloom.feature.todo.domain.TodoPolicy
import io.github.lonemoonspace.dayloom.feature.todo.domain.TodoPolicy.Group
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoPolicyTest {

    private val today = LocalDate.parse("2026-11-13")
    private val now = Instant.parse("2026-11-13T09:00:00Z")

    private fun item(id: String, due: String = "", created: Long = 0, doneAt: Long = 0) =
        TodoItem(id = id, title = id, due = due, createdAt = created, doneAt = doneAt)

    @Test
    fun `groups by due day relative to today`() {
        assertEquals(Group.OVERDUE, TodoPolicy.entry(item("a", "2026-11-11"), today).group)
        assertEquals(-2L, TodoPolicy.entry(item("a", "2026-11-11"), today).days)
        assertEquals(Group.TODAY, TodoPolicy.entry(item("a", "2026-11-13"), today).group)
        assertEquals(Group.LATER, TodoPolicy.entry(item("a", "2026-11-14"), today).group)
        assertEquals(Group.UNDATED, TodoPolicy.entry(item("a"), today).group)
        // A broken date reads as no date rather than failing the whole card. / 坏掉的日期当作无日期，而不是让整张卡片出错。
        assertEquals(Group.UNDATED, TodoPolicy.entry(item("a", "13.11.2026"), today).group)
    }

    @Test
    fun `open items are ordered most urgent first and undated ones by when they were added`() {
        val items = listOf(
            item("undated-late", created = 20),
            item("later-far", "2026-12-01"),
            item("today", "2026-11-13"),
            item("undated-early", created = 10),
            item("overdue-recent", "2026-11-12"),
            item("overdue-old", "2026-11-01"),
            item("later-near", "2026-11-20"),
            item("done", "2026-11-01", doneAt = now.toEpochMilli()),
        )
        assertEquals(
            listOf("overdue-old", "overdue-recent", "today", "later-near", "later-far", "undated-early", "undated-late"),
            TodoPolicy.open(items, today).map { it.item.id },
        )
    }

    @Test
    fun `card moves to the top only for open items overdue or due today`() {
        assertTrue(TodoPolicy.needsAttention(listOf(item("a", "2026-11-13")), today))
        assertTrue(TodoPolicy.needsAttention(listOf(item("a", "2026-11-10")), today))
        assertFalse(TodoPolicy.needsAttention(listOf(item("a", "2026-11-14"), item("b")), today))
        assertFalse(TodoPolicy.needsAttention(listOf(item("a", "2026-11-10", doneAt = 1)), today))
    }

    @Test
    fun `done items are kept for a week then purged`() {
        val fresh = item("fresh", doneAt = now.minus(Duration.ofDays(6)).toEpochMilli())
        val old = item("old", doneAt = now.minus(Duration.ofDays(8)).toEpochMilli())
        val open = item("open")
        val items = listOf(fresh, old, open)
        assertEquals(listOf("fresh"), TodoPolicy.done(items, now).map { it.id })
        assertEquals(listOf("fresh", "open"), TodoPolicy.purge(items, now).map { it.id })
        val kept = listOf(fresh, open)
        assertSame(kept, TodoPolicy.purge(kept, now))
    }

    @Test
    fun `add trims the title, skips blank ones and numbers ids past the largest`() {
        val start = listOf(item("todo1"), item("todo4"))
        val added = TodoPolicy.add(start, "  Buy   coffee\n beans ", LocalDate.parse("2026-11-20"), now)
        val new = added.last()
        assertEquals("todo5", new.id)
        assertEquals("Buy coffee beans", new.title)
        assertEquals("2026-11-20", new.due)
        assertEquals(now.toEpochMilli(), new.createdAt)
        assertSame(start, TodoPolicy.add(start, "   ", null, now))
        assertEquals("todo1", TodoPolicy.newId(emptyList()))
    }

    @Test
    fun `tick, untick, edit, delete and clear done`() {
        val items = listOf(item("todo1"), item("todo2", "2026-11-20"))
        val ticked = TodoPolicy.setDone(items, "todo1", true, now)
        assertEquals(now.toEpochMilli(), ticked[0].doneAt)
        assertEquals(0L, TodoPolicy.setDone(ticked, "todo1", false, now)[0].doneAt)
        val edited = TodoPolicy.edit(items, "todo2", "Dentist", null)
        assertEquals(TodoItem("todo2", "Dentist", "", 0, 0), edited[1])
        assertSame(items, TodoPolicy.edit(items, "todo2", " ", null))
        assertEquals(listOf("todo2"), TodoPolicy.delete(items, "todo1").map { it.id })
        assertEquals(listOf("todo2"), TodoPolicy.clearDone(ticked).map { it.id })
    }

    @Test
    fun `stored settings decode with missing fields`() {
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(TodoSettings.serializer(), """{"items":[{"id":"todo1","title":"A"}]}""")
        assertEquals(listOf(TodoItem(id = "todo1", title = "A")), decoded.items)
    }
}
