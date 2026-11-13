package io.github.lonemoonspace.dayloom.feature.todo.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

/**
 * One to-do. [due] is an optional calendar day in the app's time zone (`yyyy-MM-dd`, empty for none); [doneAt] is the
 * moment it was ticked off (epoch millis, 0 while open). [createdAt] keeps undated items in the order they were added.
 * 一条待办。[due] 是可选的应用时区日历日（`yyyy-MM-dd`，空表示无日期）；[doneAt] 是勾选完成的时刻（毫秒时间戳，未完成为 0）。
 * [createdAt] 让无日期的条目保持添加的先后顺序。
 */
@Serializable
data class TodoItem(
    /** `todo<n>`; never derived from the title. / `todo<n>`；不由标题生成。 */
    val id: String = "",
    val title: String = "",
    val due: String = "",
    val createdAt: Long = 0,
    val doneAt: Long = 0,
) {
    val done: Boolean get() = doneAt > 0
}

/**
 * Ordering, grouping and editing of to-dos. Pure, no Android, `now` as a parameter; every edit returns a new list.
 * 待办的排序、分组与增删改。纯逻辑、无 Android，`now` 作参数；每次修改都返回新的列表。
 */
object TodoPolicy {

    enum class Group { OVERDUE, TODAY, LATER, UNDATED }

    data class Entry(
        val item: TodoItem,
        val group: Group,
        val date: LocalDate?,
        /** Calendar days from today to [date]: negative when overdue, 0 today; 0 without a date. / 今天到 [date] 的自然日数：逾期为负，今天为 0；无日期为 0。 */
        val days: Long,
    )

    /** How many open items the home card lists before "All". / 首页卡片在「全部」之前最多列出几条未完成的条目。 */
    const val CARD_LIMIT = 5

    /** Done items stay this long, so a mistaken tick can still be found and undone. / 已完成的条目保留这么久，误勾的还能找回来。 */
    val KEEP_DONE: Duration = Duration.ofDays(7)

    const val TITLE_MAX = 200

    fun parseDate(text: String): LocalDate? = if (text.isBlank()) null else runCatching { LocalDate.parse(text) }.getOrNull()

    fun entry(item: TodoItem, today: LocalDate): Entry {
        val date = parseDate(item.due) ?: return Entry(item, Group.UNDATED, null, 0)
        val days = ChronoUnit.DAYS.between(today, date)
        val group = when {
            days < 0 -> Group.OVERDUE
            days == 0L -> Group.TODAY
            else -> Group.LATER
        }
        return Entry(item, group, date, days)
    }

    /**
     * Open items, most urgent first: overdue (oldest first), today, later by date, then undated in the order they were added.
     * 未完成的条目，最急的在前：逾期（最早的在前）、今天、以后按日期，最后是无日期的，按添加顺序。
     */
    fun open(items: List<TodoItem>, today: LocalDate): List<Entry> = ordered(items.filterNot { it.done }, today)

    /**
     * [items] in the order of [open], done or not; the card uses it so a just-ticked item stays in its place until it fades.
     * 按 [open] 的顺序排列 [items]，不管是否完成；卡片用它让刚勾选的条目在淡出前留在原位。
     */
    fun ordered(items: List<TodoItem>, today: LocalDate): List<Entry> =
        items.map { entry(it, today) }
            .sortedWith(compareBy<Entry>({ it.group.ordinal }, { it.date }, { it.item.createdAt }, { it.item.id }))

    /** Done items still kept, most recently done first. / 仍保留着的已完成条目，最近完成的在前。 */
    fun done(items: List<TodoItem>, now: Instant): List<TodoItem> =
        items.filter { it.done && !expired(it, now) }.sortedByDescending { it.doneAt }

    /** Whether the card should move to the top: something is overdue or due today. / 卡片是否该置顶：有逾期或今天到期的条目。 */
    fun needsAttention(items: List<TodoItem>, today: LocalDate): Boolean =
        items.any { !it.done && entry(it, today).group.let { g -> g == Group.OVERDUE || g == Group.TODAY } }

    fun openCount(items: List<TodoItem>): Int = items.count { !it.done }

    private fun expired(item: TodoItem, now: Instant): Boolean =
        item.done && Instant.ofEpochMilli(item.doneAt).plus(KEEP_DONE) < now

    /** Drops done items older than [KEEP_DONE]; returns the same list when nothing changes. / 去掉超过 [KEEP_DONE] 的已完成条目；没有变化时返回原列表。 */
    fun purge(items: List<TodoItem>, now: Instant): List<TodoItem> =
        if (items.none { expired(it, now) }) items else items.filterNot { expired(it, now) }

    /** The title as stored: trimmed, one line, at most [TITLE_MAX] characters; null when blank. / 实际保存的标题：去首尾空白、单行、最多 [TITLE_MAX] 字；空白时为 null。 */
    fun cleanTitle(text: String): String? =
        text.replace(Regex("\\s+"), " ").trim().take(TITLE_MAX).takeIf { it.isNotEmpty() }

    /** `todo<n>` with n one above the largest in use. / `todo<n>`，n 比现有最大的大一。 */
    fun newId(items: List<TodoItem>): String =
        "todo${(items.mapNotNull { it.id.removePrefix("todo").toIntOrNull() }.maxOrNull() ?: 0) + 1}"

    fun add(items: List<TodoItem>, title: String, due: LocalDate?, now: Instant): List<TodoItem> {
        val clean = cleanTitle(title) ?: return items
        return items + TodoItem(id = newId(items), title = clean, due = due?.toString().orEmpty(), createdAt = now.toEpochMilli())
    }

    fun edit(items: List<TodoItem>, id: String, title: String, due: LocalDate?): List<TodoItem> {
        val clean = cleanTitle(title) ?: return items
        return items.map { if (it.id == id) it.copy(title = clean, due = due?.toString().orEmpty()) else it }
    }

    fun setDone(items: List<TodoItem>, id: String, done: Boolean, now: Instant): List<TodoItem> =
        items.map { if (it.id == id) it.copy(doneAt = if (done) now.toEpochMilli() else 0) else it }

    fun delete(items: List<TodoItem>, id: String): List<TodoItem> = items.filterNot { it.id == id }

    fun clearDone(items: List<TodoItem>): List<TodoItem> = items.filterNot { it.done }
}
