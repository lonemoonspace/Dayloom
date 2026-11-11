package io.github.lonemoonspace.dayloom.core.storage

import io.github.lonemoonspace.dayloom.core.location.Place
import kotlinx.serialization.Serializable

/**
 * The `shared_data` file: things several modules need, so they are entered once (design §8).
 * `shared_data` 文件：多个模块都要用的内容，只录入一次（设计文档 §8）。
 */
@Serializable
data class SharedData(
    val places: List<Place> = emptyList(),
    /** The highest `place<n>` number ever handed out, so ids of deleted places are not reused. / 发出过的最大 `place<n>` 编号，删掉的地点的 id 不再复用。 */
    val lastPlaceNumber: Int = 0,
)
