package io.github.lonemoonspace.dayloom.feature.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.LocalAppClock
import io.github.lonemoonspace.dayloom.core.ui.StatusChip
import io.github.lonemoonspace.dayloom.core.ui.rememberMinuteTick
import io.github.lonemoonspace.dayloom.core.ui.rememberPatternFormatter
import io.github.lonemoonspace.dayloom.core.ui.theme.statusColors
import io.github.lonemoonspace.dayloom.feature.todo.TodoSettings
import io.github.lonemoonspace.dayloom.feature.todo.domain.TodoItem
import io.github.lonemoonspace.dayloom.feature.todo.domain.TodoPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** Changes the stored item list; the module applies it in the app scope. / 修改保存的条目列表；由模块在应用级作用域里执行。 */
internal typealias TodoUpdate = ((List<TodoItem>) -> List<TodoItem>) -> Unit

/**
 * The home card: the most urgent open items with a circle to tick each off, "Add" and "All" underneath. A ticked item stays
 * struck through in its place for a few seconds with "Undo", so a slip of the thumb is easy to take back.
 * 首页卡片：最急的几条未完成待办，每条前面一个圆圈用来勾选；下面是「添加」与「全部」。勾选后条目划线留在原位几秒，旁边是
 * 「撤销」，手滑了也容易撤回。
 */
@Composable
internal fun TodoCard(settings: Flow<TodoSettings>, update: TodoUpdate) {
    val saved by settings.collectAsStateWithLifecycle(initialValue = null)
    val clock = LocalAppClock.current
    val today = rememberMinuteTick().toLocalDate()
    // Ids ticked off on this card in the last few seconds. / 最近几秒在这张卡片上勾选的条目。
    val justDone = remember { mutableStateListOf<String>() }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }

    val items = saved?.items.orEmpty()
    val open = TodoPolicy.openCount(items)
    InfoCard(
        title = stringResource(R.string.todo_title),
        icon = R.drawable.ic_todo,
        meta = if (open > 0) pluralStringResource(R.plurals.todo_open_count, open, open) else null,
    ) {
        if (saved == null) return@InfoCard
        val rows = TodoPolicy.ordered(items.filter { !it.done || it.id in justDone }, today).take(TodoPolicy.CARD_LIMIT)
        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.todo_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        rows.forEach { entry ->
            key(entry.item.id) {
                val item = entry.item
                if (item.id in justDone) {
                    LaunchedEffect(item.id, item.doneAt) {
                        delay(UNDO_MILLIS)
                        justDone.remove(item.id)
                    }
                }
                TodoRow(
                    entry = entry,
                    onToggle = {
                        val done = !item.done
                        if (done) justDone.add(item.id) else justDone.remove(item.id)
                        update { TodoPolicy.setDone(it, item.id, done, clock.instant()) }
                    },
                    onOpen = { editing = item.id },
                    trailing = if (item.done) {
                        {
                            TextButton(onClick = {
                                justDone.remove(item.id)
                                update { TodoPolicy.setDone(it, item.id, false, clock.instant()) }
                            }) { Text(stringResource(R.string.todo_undo)) }
                        }
                    } else {
                        null
                    },
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // No side padding, so "+" lines up with the circles and the arrow with the chips. / 去掉两侧内边距，「+」对齐圆圈，箭头对齐标签。
            TextButton(onClick = { editing = NEW }, contentPadding = FLUSH_START, modifier = Modifier.weight(1f, fill = false)) {
                Box(Modifier.width(CHECK_WIDTH)) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp)) }
                Text(stringResource(R.string.todo_add))
            }
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) {
                TextButton(onClick = { showAll = true }, contentPadding = FLUSH_END) {
                    Text(stringResource(R.string.todo_all), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    // One sheet at a time: editing from "All" closes the list and brings it back afterwards, instead of stacking two
    // sheets whose scrims and dismiss order fight.
    // 同一时间只有一个面板：从「全部」里编辑时先收起列表、编辑完再打开，而不是叠两层面板、让遮罩和关闭顺序互相打架。
    if (showAll && editing == null) {
        AllSheet(items, today, update, onEdit = { editing = it }, onDismiss = { showAll = false })
    }
    editing?.let { id ->
        key(id) { Editor(items.firstOrNull { it.id == id }, today, update) { editing = null } }
    }
}

/**
 * The settings section: the whole list, the same as "All" on the card. / 设置分区：完整列表，与卡片上的「全部」相同。
 */
@Composable
internal fun TodoSettingsSection(settings: Flow<TodoSettings>, update: TodoUpdate) {
    val saved by settings.collectAsStateWithLifecycle(initialValue = TodoSettings())
    val today = rememberMinuteTick().toLocalDate()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    TodoList(saved.items, today, update, onEdit = { editing = it })
    TextButton(onClick = { editing = NEW }) { Text(stringResource(R.string.todo_add)) }
    editing?.let { id ->
        key(id) { Editor(saved.items.firstOrNull { it.id == id }, today, update) { editing = null } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllSheet(items: List<TodoItem>, today: LocalDate, update: TodoUpdate, onEdit: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(stringResource(R.string.todo_all_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(4.dp))
            TodoList(items, today, update, onEdit)
            TextButton(onClick = { onEdit(NEW) }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.todo_add))
            }
        }
    }
}

/**
 * Every item, grouped: overdue, today, later, no date, then the done ones (folded) with "Clear done".
 * 全部条目，分组显示：逾期、今天、以后、无日期，最后是折叠起来的已完成条目和「清除已完成」。
 */
@Composable
private fun TodoList(items: List<TodoItem>, today: LocalDate, update: TodoUpdate, onEdit: (String) -> Unit) {
    val clock = LocalAppClock.current
    val now = rememberMinuteTick().toInstant()
    var doneOpen by rememberSaveable { mutableStateOf(false) }
    val open = TodoPolicy.open(items, today)
    val done = TodoPolicy.done(items, now)
    if (open.isEmpty() && done.isEmpty()) {
        Text(
            stringResource(R.string.todo_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
    open.groupBy { it.group }.forEach { (group, entries) ->
        SectionLabel(stringResource(groupLabel(group)))
        entries.forEach { entry ->
            TodoRow(
                entry = entry,
                onToggle = { update { TodoPolicy.setDone(it, entry.item.id, true, clock.instant()) } },
                onOpen = { onEdit(entry.item.id) },
            )
        }
    }
    if (done.isNotEmpty()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { doneOpen = !doneOpen },
        ) {
            SectionLabel(pluralStringResource(R.plurals.todo_done_count, done.size, done.size))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(16.dp),
            )
        }
        if (doneOpen) {
            done.forEach { item ->
                TodoRow(
                    entry = TodoPolicy.entry(item, today),
                    onToggle = { update { TodoPolicy.setDone(it, item.id, false, clock.instant()) } },
                    onOpen = { onEdit(item.id) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.todo_keep_done, KEEP_DAYS, KEEP_DAYS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { update { TodoPolicy.clearDone(it) } }) { Text(stringResource(R.string.todo_clear_done)) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/**
 * One item: the circle, the title (tap to edit) and its due chip, or [trailing] in the chip's place. The title takes the
 * rest of the row, so the circles and chips line up on every row.
 * 一条待办：圆圈、标题（点按编辑）和截止标签，或用 [trailing] 代替标签。标题占满剩余宽度，每行的圆圈与标签都对齐。
 */
@Composable
private fun TodoRow(
    entry: TodoPolicy.Entry,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val item = entry.item
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = ROW_HEIGHT)) {
        Check(item.done, onToggle)
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (item.done) FontWeight.Normal else FontWeight.SemiBold,
            color = if (item.done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(vertical = 6.dp),
        )
        when {
            trailing != null -> trailing()
            !item.done && entry.date != null -> {
                Spacer(Modifier.width(8.dp))
                StatusChip(dueText(entry), dueColor(entry.group))
            }
        }
    }
}

/** The tick circle. / 勾选圆圈。 */
@Composable
private fun Check(done: Boolean, onToggle: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val label = stringResource(R.string.todo_mark_done)
    // Flush with the card's left edge like every other card's first column; the target is the full row height and reaches
    // to the title. A toggle, so a screen reader announces whether the item is done.
    // 与其他卡片的第一列一样贴着卡片左边；点按区域与行同高、一直延伸到标题。用 toggleable，读屏会播报是否已完成。
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .size(CHECK_WIDTH, ROW_HEIGHT)
            .toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = label },
    ) {
        val circle = Modifier
            .size(20.dp)
            .then(
                if (done) {
                    Modifier.background(primary, CircleShape)
                } else {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                },
            )
        Box(circle, contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * Add or edit one item, in a sheet from the bottom: the title, then no date / today / tomorrow / a picked day. The
 * keyboard's Done key saves, so a quick note takes one line of typing.
 * 从底部弹出的新增/编辑面板：标题，然后是无日期 / 今天 / 明天 / 选日期。键盘上的完成键直接保存，随手记一条只要打一行字。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Editor(initial: TodoItem?, today: LocalDate, update: TodoUpdate, onClose: () -> Unit) {
    val clock = LocalAppClock.current
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var due by rememberSaveable { mutableStateOf(initial?.due.orEmpty()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val date = TodoPolicy.parseDate(due)
    val clean = TodoPolicy.cleanTitle(title)
    val focus = remember { FocusRequester() }
    val save = {
        if (clean != null) {
            if (initial == null) {
                update { TodoPolicy.add(it, clean, date, clock.instant()) }
            } else {
                update { TodoPolicy.edit(it, initial.id, clean, date) }
            }
            onClose()
        }
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
                .navigationBarsPadding(),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(TodoPolicy.TITLE_MAX) },
                singleLine = true,
                label = { Text(stringResource(R.string.todo_title_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
            // Wraps on a narrow screen or with large text, like the other chip rows. / 窄屏或大字号时换行，与其他按钮行一致。
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                val tomorrow = today.plusDays(1)
                FilterChip(selected = date == null, onClick = { due = "" }, label = { Text(stringResource(R.string.todo_no_date)) })
                FilterChip(selected = date == today, onClick = { due = today.toString() }, label = { Text(stringResource(R.string.todo_today)) })
                FilterChip(selected = date == tomorrow, onClick = { due = tomorrow.toString() }, label = { Text(stringResource(R.string.todo_tomorrow)) })
                val other = date != null && date != today && date != tomorrow
                FilterChip(
                    selected = other,
                    onClick = { picking = true },
                    label = { Text(if (other) dateText(date!!, today) else stringResource(R.string.todo_pick_date), maxLines = 1) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                if (initial != null) {
                    TextButton(onClick = {
                        update { TodoPolicy.delete(it, initial.id) }
                        onClose()
                    }) { Text(stringResource(R.string.todo_delete), color = MaterialTheme.statusColors.red) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) { Text(stringResource(R.string.common_cancel)) }
                TextButton(onClick = save, enabled = clean != null) { Text(stringResource(R.string.common_save)) }
            }
            // A new item starts typing at once; an edit leaves the keyboard closed until the field is tapped. Run inside the
            // sheet, after the field is attached, and a moment later so the sheet's window has focus and the keyboard shows.
            // 新增时直接弹出键盘；编辑时等点了输入框再弹。放在面板内部、输入框挂上之后执行，并稍等片刻，面板窗口拿到焦点后键盘才会弹出。
            if (initial == null) {
                LaunchedEffect(Unit) {
                    delay(FOCUS_DELAY_MILLIS)
                    focus.requestFocus()
                }
            }
        }
    }

    if (picking) {
        // The picker works in UTC midnights; converting through UTC keeps the chosen calendar day. / 日期选择器以 UTC 零点计；经 UTC 换算才能保持所选的日历日。
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    picking = false
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun dueText(entry: TodoPolicy.Entry): String {
    val days = (-entry.days).toInt()
    return when (entry.group) {
        TodoPolicy.Group.OVERDUE -> pluralStringResource(R.plurals.todo_overdue_days, days, days)
        TodoPolicy.Group.TODAY -> stringResource(R.string.todo_today)
        else -> if (entry.days == 1L) stringResource(R.string.todo_tomorrow) else dateText(entry.date!!, entry.date.minusDays(entry.days))
    }
}

/** Month and day; the year only when it is not this year's. / 月和日；不是今年时才加年份。 */
@Composable
private fun dateText(date: LocalDate, today: LocalDate): String =
    date.format(rememberPatternFormatter(if (date.year == today.year) "MMMd" else "yMMMd"))

@Composable
private fun dueColor(group: TodoPolicy.Group): Color = when (group) {
    TodoPolicy.Group.OVERDUE -> MaterialTheme.statusColors.red
    TodoPolicy.Group.TODAY -> MaterialTheme.statusColors.amber
    else -> MaterialTheme.statusColors.gray
}

private fun groupLabel(group: TodoPolicy.Group): Int = when (group) {
    TodoPolicy.Group.OVERDUE -> R.string.todo_group_overdue
    TodoPolicy.Group.TODAY -> R.string.todo_group_today
    TodoPolicy.Group.LATER -> R.string.todo_group_later
    TodoPolicy.Group.UNDATED -> R.string.todo_group_undated
}

private const val NEW = ""
private val CHECK_WIDTH = 40.dp
private val ROW_HEIGHT = 40.dp
private const val FOCUS_DELAY_MILLIS = 150L
private val FLUSH_START = PaddingValues(start = 0.dp, end = 12.dp)
private val FLUSH_END = PaddingValues(start = 12.dp, end = 0.dp)
private const val UNDO_MILLIS = 5_000L
private val KEEP_DAYS = TodoPolicy.KEEP_DONE.toDays().toInt()
