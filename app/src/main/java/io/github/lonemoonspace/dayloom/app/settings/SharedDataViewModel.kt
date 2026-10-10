package io.github.lonemoonspace.dayloom.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.error.toAppError
import io.github.lonemoonspace.dayloom.core.location.Place
import io.github.lonemoonspace.dayloom.core.location.PlaceBook
import io.github.lonemoonspace.dayloom.core.location.PlaceCandidate
import io.github.lonemoonspace.dayloom.core.location.PlaceSearch
import io.github.lonemoonspace.dayloom.core.secret.SecretState
import io.github.lonemoonspace.dayloom.core.secret.SecretStore
import io.github.lonemoonspace.dayloom.core.secret.SharedSecrets
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val error: AppError? = null,
)

/**
 * The shared places and the Google key on the settings screen. Writes go to [appScope] so leaving the screen right after a
 * change does not cancel it.
 * 设置页上共用的地点与 Google Key。写入放在 [appScope]，改完立刻离开页面也不会被取消。
 */
class SharedDataViewModel(
    private val places: PlaceBook,
    private val search: PlaceSearch,
    private val secrets: SecretStore,
    private val appScope: CoroutineScope,
    /** Where blocking network calls run; production passes Dispatchers.IO. / 阻塞网络调用在哪里运行；生产环境传 Dispatchers.IO。 */
    private val ioContext: CoroutineContext,
) : ViewModel() {

    val placeList: StateFlow<List<Place>> = places.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The Google key place search uses; shared with modules that call Google. / 地点搜索用的 Google Key，与调用 Google 的模块共用。 */
    val googleKey: StateFlow<SecretState> = secrets.observe(SharedSecrets.GOOGLE_MAPS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecretState.EMPTY)

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

    fun saveGoogleKey(plain: String) {
        appScope.launch { secrets.put(SharedSecrets.GOOGLE_MAPS, plain) }
    }

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

    private fun runInEditor(block: suspend (PlaceEditor) -> PlaceEditor) {
        val start = _editor.value ?: return
        work?.cancel()
        _editor.value = start.copy(busy = true, error = null, noResults = false)
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
