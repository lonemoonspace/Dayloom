package io.github.lonemoonspace.dayloom.core.location

import kotlinx.serialization.Serializable

/**
 * A saved place. [id] is [HOME], [WORK] or a generated id for a custom place; modules refer to places by id only, so moving
 * "Home" updates every module that uses it. [label] is the user's name for a custom place and empty for the two presets,
 * whose labels come from resources.
 * 已保存的地点。[id] 是 [HOME]、[WORK] 或自定义地点的生成 id；模块只按 id 引用地点，所以改了「家」，所有用它的模块都跟着变。
 * [label] 是用户给自定义地点起的名字；两个预设地点为空，标签来自资源。
 */
@Serializable
data class Place(
    val id: String = "",
    val label: String = "",
    /** Display name from search or reverse geocoding, e.g. "Oslo". / 来自搜索或反向地理编码的显示名，如「Oslo」。 */
    val name: String = "",
    /** Region and country, shown under [name]. / 地区与国家，显示在 [name] 下方。 */
    val detail: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    /** ISO 3166-1 alpha-2, upper case; empty when unknown. / ISO 3166-1 二位字母代码，大写；未知时为空。 */
    val countryCode: String = "",
) {
    val isPreset: Boolean get() = id == HOME || id == WORK

    companion object {
        const val HOME = "home"
        const val WORK = "work"
    }
}

/**
 * A search or device-location result before it is saved under an id.
 * 搜索或设备定位得到的结果，尚未以某个 id 保存。
 */
data class PlaceCandidate(
    val name: String,
    val detail: String,
    val lat: Double,
    val lon: Double,
    val countryCode: String,
) {
    fun toPlace(id: String, label: String = ""): Place =
        Place(id = id, label = label, name = name, detail = detail, lat = lat, lon = lon, countryCode = countryCode)
}
