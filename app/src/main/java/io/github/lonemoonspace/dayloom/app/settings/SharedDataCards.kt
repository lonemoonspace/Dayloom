package io.github.lonemoonspace.dayloom.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.asString
import io.github.lonemoonspace.dayloom.core.location.Place
import io.github.lonemoonspace.dayloom.core.location.PlaceCandidate
import io.github.lonemoonspace.dayloom.core.routine.DailyWindow
import io.github.lonemoonspace.dayloom.core.routine.Routine
import io.github.lonemoonspace.dayloom.core.routine.WindowKind
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.SecretInput
import io.github.lonemoonspace.dayloom.core.ui.currentLocale
import io.github.lonemoonspace.dayloom.core.ui.placeTitle
import io.github.lonemoonspace.dayloom.core.ui.rememberTimeFormatter
import io.github.lonemoonspace.dayloom.core.ui.userMessage
import java.time.LocalTime

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
        val key by vm.googleKey.collectAsStateWithLifecycle()
        Text(
            stringResource(R.string.places_google_key_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        SecretInput(stringResource(R.string.places_google_key), key, vm::saveGoogleKey)
    }
    if (editor != null) PlaceEditorDialog(editor, places.firstOrNull { it.id == editor.id }, vm)
}

/**
 * Only the Home slot, for the onboarding: the same editor as [PlacesCard] without the rest of the list.
 * 只有「家」这一项，供首次启动引导使用：与 [PlacesCard] 同一个编辑对话框，只是不列出其他地点。
 */
@Composable
fun HomePlaceCard(places: List<Place>, editor: PlaceEditor?, vm: SharedDataViewModel) {
    val home = places.firstOrNull { it.id == Place.HOME }
    InfoCard(title = stringResource(R.string.place_home)) {
        PlaceRow(
            title = home?.name ?: stringResource(R.string.places_not_set),
            subtitle = stringResource(R.string.places_search_hint),
            onClick = { vm.edit(home, presetId = Place.HOME) },
        )
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
 * Search by address or name (Google with a key, Entur and OpenStreetMap without), or type coordinates; picking a result
 * saves it.
 * 按地址或名称搜索（有 Key 用 Google，没有用 Entur 与 OpenStreetMap），或直接输入坐标；选中一个结果即保存。
 */
@Composable
private fun PlaceEditorDialog(editor: PlaceEditor, existing: Place?, vm: SharedDataViewModel) {
    val locale = currentLocale()
    var query by rememberSaveable(editor.id) { mutableStateOf("") }
    val custom = editor.id.isEmpty() || existing?.isPreset == false
    val key by vm.googleKey.collectAsStateWithLifecycle()
    val google = key.display.isNotBlank()
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
                    if (editor.busy) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(Modifier.padding(4.dp).width(20.dp), strokeWidth = 2.dp)
                    }
                }
                editor.error?.let { Text(it.userMessage().asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (editor.noResults) Hint(stringResource(R.string.places_no_results))
                Column(Modifier.heightIn(max = 320.dp)) {
                    editor.results.forEach { candidate -> CandidateRow(candidate) { vm.choose(candidate) } }
                }
                Hint(stringResource(if (google) R.string.places_attribution_google else R.string.places_attribution))
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
 * The daily windows shared by transport, traffic and the morning brief: when the user leaves for work and heads home. They
 * apply every day.
 * 公共交通、路况与早间简报共用的日常时间窗：几点出门上班、几点回家。每天都生效。
 */
@Composable
fun RoutineCard(routine: Routine, vm: SharedDataViewModel) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    InfoCard(title = stringResource(R.string.settings_routine)) {
        WindowKind.entries.forEach { kind ->
            WindowRow(kind, routine.window(kind)) { end -> picking = "${kind.name}:${if (end) "end" else "start"}" }
        }
        if (invalid) {
            Text(stringResource(R.string.routine_window_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            stringResource(R.string.routine_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
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

/**
 * Fixed-width time buttons with tabular digits, so both rows line up whatever the times are.
 * 时间按钮定宽并用等宽数字，两行无论时间是多少都上下对齐。
 */
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
        TimeButton(time(window.startMinute)) { onPick(false) }
        Text("–", modifier = Modifier.padding(horizontal = 4.dp))
        TimeButton(time(window.endMinute)) { onPick(true) }
    }
}

@Composable
private fun TimeButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.width(TIME_BUTTON_WIDTH), contentPadding = PaddingValues(horizontal = 4.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), textAlign = TextAlign.Center)
    }
}

/** Wide enough for "12:30 PM" in a 12-hour locale. / 足够放下 12 小时制的「12:30 PM」。 */
private val TIME_BUTTON_WIDTH = 96.dp

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
