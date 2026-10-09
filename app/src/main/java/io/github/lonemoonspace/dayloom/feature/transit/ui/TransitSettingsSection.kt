package io.github.lonemoonspace.dayloom.feature.transit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.error.toAppError
import io.github.lonemoonspace.dayloom.core.i18n.asString
import io.github.lonemoonspace.dayloom.core.ui.userMessage
import io.github.lonemoonspace.dayloom.feature.transit.TransitSettings
import io.github.lonemoonspace.dayloom.feature.transit.domain.FavouriteBoard
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitPolicy
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitProvider
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitStop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Commute stops, number of options and favourite stops. [update] runs in the app scope, set up by the module.
 * 通勤站点、方案数量与收藏站点。[update] 在应用级作用域里执行，由模块提供。
 */
@Composable
internal fun TransitSettingsSection(
    settings: Flow<TransitSettings>,
    provider: TransitProvider,
    outsideNorway: Boolean,
    update: ((TransitSettings) -> TransitSettings) -> Unit,
) {
    val saved by settings.collectAsStateWithLifecycle(initialValue = TransitSettings())
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var editingBoard by rememberSaveable { mutableStateOf<String?>(null) }

    if (outsideNorway) Hint(stringResource(R.string.transit_region_note), color = MaterialTheme.colorScheme.error)

    Label(stringResource(R.string.transit_commute_title))
    StopRow(stringResource(R.string.transit_from), saved.origin) { picking = ORIGIN }
    StopRow(stringResource(R.string.transit_to), saved.destination) { picking = DESTINATION }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.transit_options), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(
            onClick = { update { it.copy(options = (it.options - 1).coerceIn(TransitPolicy.OPTIONS_RANGE)) } },
            enabled = saved.options > TransitPolicy.OPTIONS_RANGE.first,
        ) { Text("−") }
        Text(saved.options.toString(), style = MaterialTheme.typography.bodyLarge)
        TextButton(
            onClick = { update { it.copy(options = (it.options + 1).coerceIn(TransitPolicy.OPTIONS_RANGE)) } },
            enabled = saved.options < TransitPolicy.OPTIONS_RANGE.last,
        ) { Text("+") }
    }

    Label(stringResource(R.string.transit_boards_title))
    saved.boards.forEach { board ->
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { editingBoard = board.id }
                .padding(vertical = 6.dp),
        ) {
            Text(board.stop.name, style = MaterialTheme.typography.bodyLarge)
            Hint(filterSummary(board))
        }
    }
    TextButton(onClick = { editingBoard = NEW }) { Text(stringResource(R.string.transit_add_board)) }
    Hint(stringResource(R.string.transit_attribution))

    when (picking) {
        ORIGIN -> StopSearchDialog(provider, onDismiss = { picking = null }) { stop ->
            picking = null
            update { it.copy(origin = stop) }
        }
        DESTINATION -> StopSearchDialog(provider, onDismiss = { picking = null }) { stop ->
            picking = null
            update { it.copy(destination = stop) }
        }
    }
    editingBoard?.let { id ->
        val existing = saved.boards.firstOrNull { it.id == id }
        // Keyed by board, so the dialog's fields never carry over from another one. / 按收藏站点区分，对话框字段不会串到另一个。
        key(id) {
            BoardDialog(
                initial = existing,
                provider = provider,
                onDismiss = { editingBoard = null },
                onSave = { stop, lines, destinations ->
                    editingBoard = null
                    update { s ->
                        val board = (existing ?: FavouriteBoard(id = TransitPolicy.newBoardId(s.boards)))
                            .copy(stop = stop, lines = lines, destinations = destinations)
                        s.copy(boards = s.boards.map { if (it.id == board.id) board else it }.let { list -> if (existing == null) list + board else list })
                    }
                },
                onDelete = existing?.let { board ->
                    {
                        editingBoard = null
                        update { s -> s.copy(boards = s.boards.filterNot { it.id == board.id }) }
                    }
                },
            )
        }
    }
}

@Composable
private fun StopRow(label: String, stop: TransitStop, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Hint(if (stop.isSet) stop.name else stringResource(R.string.transit_not_set))
    }
}

@Composable
private fun filterSummary(board: FavouriteBoard): String {
    val parts = buildList {
        if (board.lines.isNotEmpty()) add(stringResource(R.string.transit_filter_lines, board.lines.joinToString(", ")))
        if (board.destinations.isNotEmpty()) add(stringResource(R.string.transit_filter_destinations, board.destinations.joinToString(", ")))
    }
    return parts.joinToString(" · ").ifEmpty { stringResource(R.string.transit_filter_none) }
}

@Composable
private fun BoardDialog(
    initial: FavouriteBoard?,
    provider: TransitProvider,
    onDismiss: () -> Unit,
    onSave: (TransitStop, List<String>, List<String>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var stop by remember { mutableStateOf(initial?.stop ?: TransitStop()) }
    var lines by rememberSaveable { mutableStateOf(initial?.lines.orEmpty().joinToString(", ")) }
    var destinations by rememberSaveable { mutableStateOf(initial?.destinations.orEmpty().joinToString(", ")) }
    var searching by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.transit_add_board else R.string.transit_edit_board)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StopRow(stringResource(R.string.transit_stop), stop) { searching = true }
                OutlinedTextField(
                    value = lines,
                    onValueChange = { lines = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.transit_lines_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = destinations,
                    onValueChange = { destinations = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.transit_destinations_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Hint(stringResource(R.string.transit_filter_help), maxLines = 3)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(stop, TransitPolicy.parseList(lines), TransitPolicy.parseList(destinations)) }, enabled = stop.isSet) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.transit_delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
    if (searching) {
        StopSearchDialog(provider, onDismiss = { searching = false }) {
            stop = it
            searching = false
        }
    }
}

/**
 * Searches Entur's stop register; picking a result returns it. The search runs in this dialog's scope, so closing the dialog
 * cancels it.
 * 搜索 Entur 的站点库；选中一个结果即返回。搜索在对话框的作用域里运行，关闭对话框就会取消。
 */
@Composable
private fun StopSearchDialog(provider: TransitProvider, onDismiss: () -> Unit, onPick: (TransitStop) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TransitStop>?>(null) }
    var error by remember { mutableStateOf<AppError?>(null) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    fun search() {
        job?.cancel()
        error = null
        busy = true
        job = scope.launch {
            try {
                results = provider.searchStops(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toAppError()
            } finally {
                busy = false
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.transit_search_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.transit_search_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = ::search, enabled = query.trim().length >= 2) { Text(stringResource(R.string.transit_search)) }
                    if (busy) CircularProgressIndicator(Modifier.padding(4.dp).heightIn(max = 20.dp), strokeWidth = 2.dp)
                }
                error?.let { Text(it.userMessage().asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (results?.isEmpty() == true) Hint(stringResource(R.string.transit_search_none))
                results.orEmpty().forEach { stop ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(stop) }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(stop.name, style = MaterialTheme.typography.bodyLarge)
                        if (stop.locality.isNotBlank()) Hint(stop.locality)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Hint(text: String, maxLines: Int = 2, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = maxLines)
}

private const val ORIGIN = "origin"
private const val DESTINATION = "destination"
private const val NEW = ""
