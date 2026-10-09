package io.github.lonemoonspace.dayloom.core.location

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Pure rules for the saved-place list.
 * 已保存地点列表的纯规则。
 */
object PlacesPolicy {

    /** Home, then Work, then custom places by label. / 先家、再公司，然后按标签排列自定义地点。 */
    fun ordered(places: List<Place>): List<Place> =
        places.sortedWith(compareBy<Place>({ presetRank(it.id) }, { it.label.lowercase() }, { it.id }))

    /** Replaces the place with the same id, or adds it. / 替换同 id 的地点，没有就新增。 */
    fun upsert(places: List<Place>, place: Place): List<Place> {
        require(place.id.isNotBlank()) { "place id must not be blank" }
        val index = places.indexOfFirst { it.id == place.id }
        return if (index < 0) places + place else places.toMutableList().apply { set(index, place) }
    }

    fun remove(places: List<Place>, id: String): List<Place> = places.filterNot { it.id == id }

    /**
     * A fresh id for a custom place: `place<n>` with the smallest unused n. Ids are never derived from the label, so renaming
     * keeps every module's reference.
     * 自定义地点的新 id：`place<n>`，n 取最小未用的数。id 不由标签生成，改名后各模块的引用照样有效。
     */
    fun newCustomId(places: List<Place>): String {
        val used = places.mapTo(mutableSetOf()) { it.id }
        return generateSequence(1) { it + 1 }.map { "place$it" }.first { it !in used }
    }

    /**
     * Coordinates with at most four decimals (about 11 m): MET Norway rejects more, and finer precision says nothing more
     * about the weather while making the parameter key needlessly specific. Rounded half-up on the decimal digits, so 59.91235
     * becomes 59.9124 rather than whatever binary floating point makes of it.
     * 最多保留四位小数（约 11 米）：MET Norway 拒绝更多位数；更高精度对天气毫无意义，还让参数指纹无谓地变细。
     * 按十进制数字四舍五入，59.91235 得到 59.9124，不受二进制浮点误差影响。
     */
    fun roundCoordinate(value: Double): Double = BigDecimal(value.toString()).setScale(4, RoundingMode.HALF_UP).toDouble()

    /** Four decimals with a dot, never exponent notation (`1.0E-4`). / 四位小数、小数点，绝不用科学计数法（`1.0E-4`）。 */
    fun formatCoordinate(value: Double): String = String.format(Locale.ROOT, "%.4f", roundCoordinate(value))

    /**
     * "59.9139, 10.7522" (also with a space, a semicolon or Chinese punctuation) typed by hand, as a place at exactly that spot;
     * null for anything else, including coordinates out of range. Both numbers need decimals, so a house number and a
     * postcode are never read as coordinates.
     * 手动输入的「59.9139, 10.7522」（也接受空格、分号或中文标点分隔），作为恰好在那一点的地点；其他输入（包括超出范围的坐标）为 null。
     * 两个数都必须带小数，门牌号加邮编才不会被当成坐标。
     */
    fun parseCoordinates(text: String): PlaceCandidate? {
        val match = COORDINATES.matchEntire(text.trim()) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return PlaceCandidate(
            name = formatCoordinate(lat) + ", " + formatCoordinate(lon),
            detail = "",
            lat = roundCoordinate(lat),
            lon = roundCoordinate(lon),
            countryCode = "",
        )
    }

    private val COORDINATES = Regex("""(-?\d{1,2}\.\d+)\s*[,;，；\s]\s*(-?\d{1,3}\.\d+)""")

    private fun presetRank(id: String): Int = when (id) {
        Place.HOME -> 0
        Place.WORK -> 1
        else -> 2
    }
}
