package io.github.lonemoonspace.dayloom.core.location

import io.github.lonemoonspace.dayloom.core.storage.SharedData
import io.github.lonemoonspace.dayloom.core.storage.ValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Read access to the saved places, which is all modules get: places are edited only on the settings screen.
 * 对已保存地点的只读访问，模块只拿得到这个：地点只在设置页编辑。
 */
interface Places {
    /** Home, Work, then custom places. / 家、公司，然后是自定义地点。 */
    val all: Flow<List<Place>>

    fun observe(id: String): Flow<Place?> = all.map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged()
}

/**
 * The saved places, stored in `shared_data`. / 已保存的地点，存于 `shared_data`。
 */
class PlaceBook(private val store: ValueStore<SharedData>) : Places {
    override val all: Flow<List<Place>> = store.flow.map { PlacesPolicy.ordered(it.places) }.distinctUntilChanged()

    suspend fun put(place: Place) = store.update { it.copy(places = PlacesPolicy.upsert(it.places, place)) }

    suspend fun remove(id: String) = store.update { it.copy(places = PlacesPolicy.remove(it.places, id)) }

    /** Saves [candidate] as a new custom place and returns its id. / 把 [candidate] 存为新的自定义地点，并返回其 id。 */
    suspend fun addCustom(candidate: PlaceCandidate, label: String): String {
        var id = ""
        store.update {
            id = PlacesPolicy.newCustomId(it.places)
            it.copy(places = PlacesPolicy.upsert(it.places, candidate.toPlace(id, label)))
        }
        return id
    }
}
