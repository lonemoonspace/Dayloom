package io.github.lonemoonspace.dayloom.core.location

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.json.AppJson
import io.github.lonemoonspace.dayloom.core.network.decodeOrBadData
import io.github.lonemoonspace.dayloom.core.network.executeOrAppError
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Finds places by name. / 按名称查找地点。
 */
interface PlaceSearch {
    /** [language] is a BCP 47 tag such as `en` or `zh`; results come back in it where the service has names. / [language] 是 `en`、`zh` 这类 BCP 47 标签；服务有对应名称时按它返回。 */
    suspend fun search(query: String, language: String): List<PlaceCandidate>
}

/**
 * Entur's geocoder: Norwegian street addresses (Kartverket), places, points of interest and stops, free and without a key;
 * data under NLOD. Entur asks every client to name itself in `ET-Client-Name`. Blocking call: run it off the main thread.
 * Entur 地理编码：挪威的街道地址（Kartverket）、地名、地标与站点，免费、无需 Key；数据为 NLOD 许可。Entur 要求每个客户端
 * 用 `ET-Client-Name` 标明身份。阻塞调用：不要在主线程运行。
 */
class EnturGeocoder(
    private val http: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api.entur.io/geocoder/v3/autocomplete".toHttpUrl(),
) : PlaceSearch {

    override suspend fun search(query: String, language: String): List<PlaceCandidate> {
        val text = query.trim()
        if (text.length < 2) return emptyList()
        val url = baseUrl.newBuilder()
            .addQueryParameter("q", text)
            .addQueryParameter("limit", "10")
            .build()
        val request = Request.Builder().url(url).header(CLIENT_HEADER, CLIENT_NAME).build()
        http.newCall(request).executeOrAppError().use { response ->
            if (!response.isSuccessful) throw AppError.Http(SERVICE, response.code)
            val body = response.body.string()
            return decodeOrBadData(SERVICE, "geocoder response") {
                AppJson.standard.decodeFromString(Response.serializer(), body)
            }.features.mapNotNull { it.toCandidate() }
        }
    }

    @Serializable
    private data class Response(val features: List<Feature> = emptyList())

    @Serializable
    private data class Feature(val geometry: Geometry = Geometry(), val properties: Properties = Properties()) {
        fun toCandidate(): PlaceCandidate? {
            // GeoJSON order: longitude first. / GeoJSON 顺序：经度在前。
            val (lon, lat) = geometry.coordinates.takeIf { it.size >= 2 } ?: return null
            val name = properties.names.default.ifBlank { return null }
            return PlaceCandidate(
                name = name,
                detail = listOf(properties.address.locality, properties.address.county)
                    .filter { it.isNotBlank() && it != name }.distinct().joinToString(", "),
                lat = lat,
                lon = lon,
                countryCode = properties.address.countryCode.uppercase(),
            )
        }
    }

    @Serializable
    private data class Geometry(val coordinates: List<Double> = emptyList())

    @Serializable
    private data class Properties(val names: Names = Names(), val address: Address = Address())

    @Serializable
    private data class Names(val default: String = "")

    @Serializable
    private data class Address(val locality: String = "", val county: String = "", val countryCode: String = "")

    companion object {
        const val SERVICE = "Entur"
        const val CLIENT_HEADER = "ET-Client-Name"
        const val CLIENT_NAME = "lonemoonspace-dayloom"
    }
}

/**
 * Nominatim (OpenStreetMap): worldwide addresses and places, free and without a key; data © OpenStreetMap contributors
 * (ODbL). Its usage policy allows about one request a second with an identifying User-Agent (the shared client sends one)
 * and forbids search-as-you-type, so it is only called when the user presses Search.
 * Nominatim（OpenStreetMap）：全球的地址与地点，免费、无需 Key；数据 © OpenStreetMap 贡献者（ODbL）。其使用政策允许带可识别
 * User-Agent（共享客户端会带上）每秒约一次请求，并禁止边输入边搜索，所以只在用户按「搜索」时调用。
 */
class NominatimSearch(
    private val http: OkHttpClient,
    private val baseUrl: HttpUrl = "https://nominatim.openstreetmap.org/search".toHttpUrl(),
) : PlaceSearch {

    override suspend fun search(query: String, language: String): List<PlaceCandidate> {
        val text = query.trim()
        if (text.length < 2) return emptyList()
        val url = baseUrl.newBuilder()
            .addQueryParameter("q", text)
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("addressdetails", "1")
            .addQueryParameter("limit", "8")
            .addQueryParameter("accept-language", language)
            .build()
        http.newCall(Request.Builder().url(url).build()).executeOrAppError().use { response ->
            if (!response.isSuccessful) throw AppError.Http(SERVICE, response.code)
            val body = response.body.string()
            return decodeOrBadData(SERVICE, "search response") {
                AppJson.standard.decodeFromString(ListSerializer(Result.serializer()), body)
            }.mapNotNull { it.toCandidate() }
        }
    }

    @Serializable
    private data class Result(
        val name: String = "",
        @SerialName("display_name") val displayName: String = "",
        val lat: String = "",
        val lon: String = "",
        val address: Address = Address(),
    ) {
        fun toCandidate(): PlaceCandidate? {
            val latitude = lat.toDoubleOrNull() ?: return null
            val longitude = lon.toDoubleOrNull() ?: return null
            // Plain addresses have no name; then road and house number stand in, in the Norwegian order the app is
            // mostly used with ("Storgata 10").
            // 普通地址没有名称，此时用路名加门牌号代替，按 App 主要使用地挪威的顺序（「Storgata 10」）。
            val title = name.ifBlank { listOf(address.road, address.houseNumber).filter { it.isNotBlank() }.joinToString(" ") }
                .ifBlank { displayName.substringBefore(", ") }
                .ifBlank { return null }
            return PlaceCandidate(
                name = title,
                detail = displayName.split(", ").filter { it.isNotBlank() && it !in title }.distinct().joinToString(", "),
                lat = latitude,
                lon = longitude,
                countryCode = address.countryCode.uppercase(),
            )
        }
    }

    @Serializable
    private data class Address(
        val road: String = "",
        @SerialName("house_number") val houseNumber: String = "",
        @SerialName("country_code") val countryCode: String = "",
    )

    companion object {
        const val SERVICE = "OpenStreetMap"
    }
}

/**
 * [primary] first; [fallback] when it finds nothing relevant ([PlacesPolicy.isRelevant]) or fails. Entur only knows
 * Norway, Nominatim the rest of the world.
 * 先用 [primary]；它找不到相关结果（[PlacesPolicy.isRelevant]）或出错时用 [fallback]。Entur 只认挪威，世界其他地方交给 Nominatim。
 */
class FallbackSearch(private val primary: PlaceSearch, private val fallback: PlaceSearch) : PlaceSearch {
    override suspend fun search(query: String, language: String): List<PlaceCandidate> {
        val first = try {
            primary.search(query, language)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return try {
                fallback.search(query, language)
            } catch (f: CancellationException) {
                throw f
            } catch (_: Exception) {
                // Both failed: the first service's error is the one that matters most. / 两个都失败：第一个服务的错误最有用。
                throw e
            }
        }
        return first.filter { PlacesPolicy.isRelevant(query, it) }.ifEmpty { fallback.search(query, language) }
    }
}
