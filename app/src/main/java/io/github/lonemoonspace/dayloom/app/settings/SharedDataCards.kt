package io.github.lonemoonspace.dayloom.app.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.asString
import io.github.lonemoonspace.dayloom.core.location.Place
import io.github.lonemoonspace.dayloom.core.location.PlaceCandidate
import io.github.lonemoonspace.dayloom.core.routine.DailyWindow
import io.github.lonemoonspace.dayloom.core.routine.Routine
import io.github.lonemoonspace.dayloom.core.routine.WindowKind
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.SwitchRow
import io.github.lonemoonspace.dayloom.core.ui.currentLocale
import io.github.lonemoonspace.dayloom.core.ui.placeTitle
import io.github.lonemoonspace.dayloom.core.ui.rememberTimeFormatter
import io.github.lonemoonspace.dayloom.core.ui.userMessage
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle

/**
 * Saved places: Home and Work always listed, custom places below. Modules refer to these, so they are edited only here.
 * 已保存的地点：家与公司总是列出，自定义地点在下面。各模块引用这些地点，所以只在这里编辑。
 */
@Composable
fun PlacesCard(
    places: List<Place>,
    editor: PlaceEditor?,
    vm: SharedDataViewModel,
) {
    InfoCard(title = stringResource(R.string.settings_places)) {
        listOf(Place.HOME, Place.WORK).forEach { id ->
            val place = places.firstOrNull { it.id == id }
            PlaceRow(
                title = stringResource(if (id == Place.HOME) R.string.place_home else R.string.place_work),
                subtitle = place?.name ?: stringResource(R.string.places_not_set),
                onClick = { vm.edit(place, presetId = id) },
            )
        }
        places.filterNot { it.isPreset }.forEach { place ->
            PlaceRow(title = placeTitle(place), subtitle = place.name, onClick = { vm.edit(place) })
        }
        TextButton(onClick = { vm.edit(null) }) { Text(stringResource(R.string.places_add)) }
    }
    if (editor != null) PlaceEditorDialog(editor, places.firstOrNull { it.id == editor.id }, vm)
}

@Composable
private fun PlaceRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Search by name or take the current position once; picking a result saves it. Location permission is asked for only
 * when the user taps the button.
 * 按名称搜索，或读取一次当前位置；选中一个结果即保存。只有用户点按钮时才请求定位权限。
 */
@Composable
private fun PlaceEditorDialog(editor: PlaceEditor, existing: Place?, vm: SharedDataViewModel) {
    val locale = currentLocale()
    var query by rememberSaveable(editor.id) { mutableStateOf("") }
    val custom = editor.id.isEmpty() || existing?.isPreset == false
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.locate(locale)
    }
    val title = when (editor.id) {
        Place.HOME -> stringResource(R.string.place_home)
        Place.WORK -> stringResource(R.string.place_work)
        "" -> stringResource(R.string.places_add)
        else -> existing?.let { placeTitle(it) }.orEmpty()
    }
    AlertDialog(
        onDismissRequest = vm::closeEditor,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (custom) {
                    OutlinedTextField(
                        value = editor.label,
                        onValueChange = vm::setLabel,
                        singleLine = true,
                        label = { Text(stringResource(R.string.places_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.places_search_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search(query, locale) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.search(query, locale) }, enabled = query.trim().length >= 2) {
                        Text(stringResource(R.string.places_search))
                    }
                    TextButton(onClick = {
                        if (vm.hasLocationPermission()) vm.locate(locale) else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }) {
                        Text(stringResource(R.string.places_use_location))
                    }
                    if (editor.busy) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(Modifier.padding(4.dp).width(20.dp), strokeWidth = 2.dp)
                    }
                }
                editor.error?.let { Text(it.userMessage().asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (editor.noResults) Hint(stringResource(R.string.places_no_results))
                if (editor.locateFailed) Hint(stringResource(R.string.places_locate_failed))
                Column(Modifier.heightIn(max = 320.dp)) {
                    editor.results.forEach { candidate -> CandidateRow(candidate) { vm.choose(candidate) } }
                }
                Hint(stringResource(R.string.places_attribution))
            }
        },
        confirmButton = {
            // A custom place can be renamed without searching again. / 自定义地点不用重新搜索也能改名。
            if (existing != null && custom) {
                TextButton(onClick = { vm.saveLabel(existing) }, enabled = editor.label.isNotBlank()) {
                    Text(stringResource(R.string.common_save))
                }
            }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = { vm.remove(existing.id) }) { Text(stringResource(R.string.places_remove)) }
                }
                TextButton(onClick = vm::closeEditor) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}

@Composable
private fun CandidateRow(candidate: PlaceCandidate, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(candidate.name, style = MaterialTheme.typography.bodyLarge)
        if (candidate.detail.isNotBlank()) {
            Text(candidate.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The daily windows shared by weather, transport and traffic: when the user leaves for work and heads home, on which days.
 * 天气、公交与路况共用的日常时间窗：哪几天、几点出门上班、几点回家。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineCard(routine: Routine, vm: SharedDataViewModel) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    InfoCard(title = stringResource(R.string.settings_routine)) {
        SwitchRow(
            label = stringResource(R.string.routine_commute),
            summary = stringResource(R.string.routine_commute_summary),
            checked = routine.enabled,
            onCheckedChange = vm::setCommute,
        )
        if (routine.enabled) {
            WindowKind.entries.forEach { kind ->
                WindowRow(kind, routine.window(kind)) { end -> picking = "${kind.name}:${if (end) "end" else "start"}" }
            }
            if (invalid) {
                Text(stringResource(R.string.routine_window_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.routine_working_days),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            val locale = currentLocale()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = routine.isWorkingDay(day),
                        onClick = { vm.setWorkingDay(day.value, !routine.isWorkingDay(day)) },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                    )
                }
            }
        }
    }
    picking?.let { key ->
        val kind = WindowKind.valueOf(key.substringBefore(':'))
        val end = key.endsWith(":end")
        val window = routine.window(kind)
        TimeDialog(
            initialMinute = if (end) window.endMinute else window.startMinute,
            onDismiss = { picking = null },
            onConfirm = { minute ->
                picking = null
                val next = if (end) window.copy(endMinute = minute) else window.copy(startMinute = minute)
                invalid = !vm.setWindow(kind, next)
            },
        )
    }
}

@Composable
private fun WindowRow(kind: WindowKind, window: DailyWindow, onPick: (end: Boolean) -> Unit) {
    val format = rememberTimeFormatter()
    fun time(minute: Int) = LocalTime.MIDNIGHT.plusMinutes(minute.toLong()).format(format)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(if (kind == WindowKind.TO_WORK) R.string.routine_to_work else R.string.routine_back_home),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onPick(false) }) { Text(time(window.startMinute)) }
        Text("–")
        TextButton(onClick = { onPick(true) }) { Text(time(window.endMinute)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
