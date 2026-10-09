package io.github.lonemoonspace.dayloom.feature.reminders.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.StatusChip
import io.github.lonemoonspace.dayloom.core.ui.rememberMinuteTick
import io.github.lonemoonspace.dayloom.core.ui.rememberPatternFormatter
import io.github.lonemoonspace.dayloom.core.ui.rememberTimeFormatter
import io.github.lonemoonspace.dayloom.core.ui.theme.statusColors
import io.github.lonemoonspace.dayloom.feature.reminders.RemindersSettings
import io.github.lonemoonspace.dayloom.feature.reminders.domain.ReminderItem
import io.github.lonemoonspace.dayloom.feature.reminders.domain.ReminderPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow

/**
 * The home card: every item with its remaining time, soonest first.
 * 首页卡片：列出每个条目及剩余时间，最早到期的在前。
 */
@Composable
internal fun RemindersCard(settings: Flow<RemindersSettings>) {
    val saved by settings.collectAsStateWithLifecycle(initialValue = null)
    val now = rememberMinuteTick().toLocalDateTime()
    InfoCard(title = stringResource(R.string.reminders_title)) {
        val statuses = ReminderPolicy.statuses(saved?.items.orEmpty(), now)
        if (saved != null && statuses.isEmpty()) {
            Hint(stringResource(R.string.reminders_empty))
        }
        statuses.forEach { status ->
            Column(Modifier.padding(vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        status.item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    val (color, icon) = chip(status.state)
                    StatusChip(remainingText(status), color, icon)
                }
                Hint(stringResource(R.string.reminders_valid_until, untilText(status.until)))
            }
        }
    }
}

/**
 * The settings section: add, edit and remove items. [update] is applied in the app scope by the module.
 * 设置分区：新增、编辑、删除条目。[update] 由模块在应用级作用域里执行。
 */
@Composable
internal fun RemindersSettingsSection(settings: Flow<RemindersSettings>, update: ((RemindersSettings) -> RemindersSettings) -> Unit) {
    val saved by settings.collectAsStateWithLifecycle(initialValue = RemindersSettings())
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val items = saved.items.sortedBy { ReminderPolicy.parse(it.until) ?: LocalDateTime.MAX }
    items.forEach { item ->
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { editing = item.id }
                .padding(vertical = 6.dp),
        ) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            ReminderPolicy.parse(item.until)?.let { Hint(stringResource(R.string.reminders_valid_until, untilText(it))) }
        }
    }
    TextButton(onClick = { editing = NEW }) { Text(stringResource(R.string.reminders_add)) }
    editing?.let { id ->
        val existing = saved.items.firstOrNull { it.id == id }
        // Keyed by item, so the dialog's saved fields never carry over from another item. / 按条目区分，对话框保存的字段不会串到另一个条目。
        key(id) {
            ItemDialog(
                initial = existing,
                onDismiss = { editing = null },
                onSave = { name, until, warnDays ->
                    editing = null
                    update { s ->
                        val item = (existing ?: ReminderItem(id = ReminderPolicy.newId(s.items)))
                            .copy(name = name, until = ReminderPolicy.format(until), warnDays = warnDays)
                        s.copy(items = s.items.filterNot { it.id == item.id } + item)
                    }
                },
                onDelete = existing?.let { item ->
                    {
                        editing = null
                        update { s -> s.copy(items = s.items.filterNot { it.id == item.id }) }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemDialog(
    initial: ReminderItem?,
    onDismiss: () -> Unit,
    onSave: (name: String, until: LocalDateTime, warnDays: Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val start = initial?.let { ReminderPolicy.parse(it.until) }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(start?.toLocalDate()?.toString().orEmpty()) }
    var time by rememberSaveable { mutableStateOf((start?.toLocalTime() ?: ReminderPolicy.DEFAULT_TIME).toString()) }
    var warnDays by rememberSaveable { mutableStateOf((initial?.warnDays ?: ReminderPolicy.DEFAULT_WARN_DAYS).toString()) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
    val parsedWarn = warnDays.trim().toIntOrNull()?.takeIf { it in ReminderPolicy.WARN_DAYS_RANGE }
    val valid = name.isNotBlank() && parsedDate != null && parsedWarn != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.reminders_add else R.string.reminders_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.reminders_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { picking = DATE }) {
                        Text(parsedDate?.let { untilDate(it) } ?: stringResource(R.string.reminders_pick_date))
                    }
                    TextButton(onClick = { picking = TIME }) {
                        Text(LocalTime.parse(time).format(rememberTimeFormatter()))
                    }
                }
                OutlinedTextField(
                    value = warnDays,
                    onValueChange = { warnDays = it.filter(Char::isDigit).take(2) },
                    singleLine = true,
                    isError = parsedWarn == null,
                    label = { Text(stringResource(R.string.reminders_warn_days)) },
                    supportingText = {
                        Text(
                            pluralStringResource(
                                R.plurals.reminders_warn_days_range,
                                ReminderPolicy.WARN_DAYS_RANGE.last,
                                ReminderPolicy.WARN_DAYS_RANGE.first,
                                ReminderPolicy.WARN_DAYS_RANGE.last,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), parsedDate!!.atTime(LocalTime.parse(time)), parsedWarn!!) },
                enabled = valid,
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.reminders_delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )

    when (picking) {
        DATE -> {
            // The picker works in UTC midnights; converting through UTC keeps the chosen calendar day. / 日期选择器以 UTC 零点计；经 UTC 换算才能保持所选的日历日。
            val state = rememberDatePickerState(
                initialSelectedDateMillis = parsedDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                        picking = null
                    }) { Text(stringResource(R.string.common_save)) }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.common_cancel)) } },
            ) { DatePicker(state) }
        }
        TIME -> {
            val current = LocalTime.parse(time)
            val state = rememberTimePickerState(
                initialHour = current.hour,
                initialMinute = current.minute,
                is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
            )
            AlertDialog(
                onDismissRequest = { picking = null },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton(onClick = {
                        time = LocalTime.of(state.hour, state.minute).toString()
                        picking = null
                    }) { Text(stringResource(R.string.common_save)) }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.common_cancel)) } },
            )
        }
    }
}

@Composable
private fun remainingText(status: ReminderPolicy.Status): String = when {
    status.state == ReminderPolicy.State.EXPIRED -> stringResource(R.string.reminders_expired)
    status.daysUntil == 0L -> stringResource(R.string.reminders_today)
    status.daysUntil == 1L -> stringResource(R.string.reminders_tomorrow)
    else -> pluralStringResource(R.plurals.reminders_days_left, status.daysUntil.toInt(), status.daysUntil.toInt())
}

@Composable
private fun chip(state: ReminderPolicy.State): Pair<Color, Int> = when (state) {
    ReminderPolicy.State.ACTIVE -> MaterialTheme.statusColors.green to R.drawable.ic_status_ok
    ReminderPolicy.State.EXPIRING -> MaterialTheme.statusColors.amber to R.drawable.ic_status_warn
    ReminderPolicy.State.EXPIRED -> MaterialTheme.statusColors.red to R.drawable.ic_status_warn
}

@Composable
private fun untilText(until: LocalDateTime): String = "${untilDate(until.toLocalDate())} ${until.format(rememberTimeFormatter())}"

@Composable
private fun untilDate(date: LocalDate): String = date.format(rememberPatternFormatter("yMMMd"))

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val NEW = ""
private const val DATE = "date"
private const val TIME = "time"
