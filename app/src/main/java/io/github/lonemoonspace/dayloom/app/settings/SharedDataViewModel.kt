package io.github.lonemoonspace.dayloom.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.error.toAppError
import io.github.lonemoonspace.dayloom.core.location.DeviceLocator
import io.github.lonemoonspace.dayloom.core.location.Place
import io.github.lonemoonspace.dayloom.core.location.PlaceBook
import io.github.lonemoonspace.dayloom.core.location.PlaceCandidate
import io.github.lonemoonspace.dayloom.core.location.PlaceSearch
import io.github.lonemoonspace.dayloom.core.routine.DailyWindow
import io.github.lonemoonspace.dayloom.core.routine.Routine
import io.github.lonemoonspace.dayloom.core.routine.RoutinePolicy
import io.github.lonemoonspace.dayloom.core.routine.WindowKind
import io.github.lonemoonspace.dayloom.core.storage.SharedData
import io.github.lonemoonspace.dayloom.core.storage.ValueStore
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Which place the editor is for: a preset slot or an existing custom place ([id]), or a new custom place ([id] empty).
 * 编辑器针对哪个地点：预设位置或已有的自定义地点（[id]），或新的自定义地点（[id] 为空）。
 */
data class PlaceEditor(
    val id: String,
    val label: String = "",
    val results: List<PlaceCandidate> = emptyList(),
    val busy: Boolean = false,
    /** True after a search that found nothing. / 搜索后一个结果都没有时为 true。 */
    val noResults: Boolean = false,
    /** True when the one-shot location found no position. / 单次定位拿不到位置时为 true。 */
    val locateFailed: Boolean = false,
    val error: AppError? = null,
)

/**
 * The shared places and daily windows on the settings screen. Writes go to [appScope] so leaving the screen right after a
 * change does not cancel it.
 * 设置页上共用的地点与日常时间窗。写入放在 [appScope]，改完立刻离开页面也不会被取消。
 */
class SharedDataViewModel(
    private val places: PlaceBook,
    private val sharedData: ValueStore<SharedData>,
    private val search: PlaceSearch,
    private val locator: DeviceLocator,
    private val appScope: CoroutineScope,
    /** Where blocking network calls run; production passes Dispatchers.IO. / 阻塞网络调用在哪里运行；生产环境传 Dispatchers.IO。 */
    private val ioContext: CoroutineContext,
) : ViewModel() {

    val placeList: StateFlow<List<Place>> = places.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routine: StateFlow<Routine> = sharedData.flow.map { it.routine }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Routine())

    private val _editor = MutableStateFlow<PlaceEditor?>(null)
    val editor: StateFlow<PlaceEditor?> = _editor.asStateFlow()

    private var work: Job? = null

    fun edit(place: Place?, presetId: String? = null) {
        work?.cancel()
        _editor.value = PlaceEditor(id = place?.id ?: presetId.orEmpty(), label = place?.label.orEmpty())
    }

    fun closeEditor() {
        work?.cancel()
        _editor.value = null
    }

    fun setLabel(label: String) = _editor.update { it?.copy(label = label) }

    fun search(query: String, locale: Locale) = runInEditor { editor ->
        val results = withContext(ioContext) { search.search(query, locale.language) }
        editor.copy(results = results, noResults = results.isEmpty())
    }

    fun locate(locale: Locale) = runInEditor { editor ->
        val found = locator.locateOnce(locale)
        if (found == null) editor.copy(locateFailed = true) else editor.copy(results = listOf(found))
    }

    fun hasLocationPermission(): Boolean = locator.hasPermission()

    fun choose(candidate: PlaceCandidate) {
        val editor = _editor.value ?: return
        val label = editor.label.trim()
        appScope.launch {
            if (editor.id.isEmpty()) places.addCustom(candidate, label) else places.put(candidate.toPlace(editor.id, label))
        }
        closeEditor()
    }

    /** Renames a custom place without searching again. / 不重新搜索，只给自定义地点改名。 */
    fun saveLabel(place: Place) {
        val label = _editor.value?.label?.trim() ?: return
        appScope.launch { places.put(place.copy(label = label)) }
        closeEditor()
    }

    fun remove(id: String) {
        appScope.launch { places.remove(id) }
        closeEditor()
    }

    fun setCommute(enabled: Boolean) = updateRoutine { it.copy(enabled = enabled) }

    /** Returns false (and saves nothing) for an empty window. / 时间窗为空时返回 false 且不保存。 */
    fun setWindow(kind: WindowKind, window: DailyWindow): Boolean {
        if (!RoutinePolicy.isValid(window)) return false
        updateRoutine { if (kind == WindowKind.TO_WORK) it.copy(toWork = window) else it.copy(backHome = window) }
        return true
    }

    fun setWorkingDay(isoDay: Int, working: Boolean) =
        updateRoutine { it.copy(workingDays = if (working) it.workingDays + isoDay else it.workingDays - isoDay) }

    private fun updateRoutine(transform: (Routine) -> Routine) {
        appScope.launch { sharedData.update { it.copy(routine = transform(it.routine)) } }
    }

    private fun runInEditor(block: suspend (PlaceEditor) -> PlaceEditor) {
        val start = _editor.value ?: return
        work?.cancel()
        _editor.value = start.copy(busy = true, error = null, noResults = false, locateFailed = false)
        work = viewModelScope.launch {
            val next = try {
                block(start)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                start.copy(error = e.toAppError())
            }
            // The user may have closed or switched the editor meanwhile; keep the label they typed. / 期间用户可能已关闭或切换编辑器；保留其输入的标签。
            _editor.update { current -> if (current?.id == start.id) next.copy(label = current.label, busy = false) else current }
        }
    }
}
