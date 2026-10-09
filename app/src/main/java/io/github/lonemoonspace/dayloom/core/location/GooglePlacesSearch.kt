package io.github.lonemoonspace.dayloom.core.location

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.json.AppJson
import io.github.lonemoonspace.dayloom.core.network.decodeOrBadData
import io.github.lonemoonspace.dayloom.core.network.executeOrAppError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Google Places API (New) Text Search with the user's own key: street addresses and named places worldwide. The key goes in
 * a header, never in the URL, so it does not end up in logs. Blocking call: run it off the main thread.
 * 使用用户自己 Key 的 Google Places API（新版）文本搜索：全球的街道地址与具名地点。Key 放在请求头而不是 URL 里，不会进日志。
 * 阻塞调用：不要在主线程运行。
 */
class GooglePlacesSearch(
    private val http: OkHttpClient,
    private val endpoint: HttpUrl = "https://places.googleapis.com/v1/places:searchText".toHttpUrl(),
) {

    fun search(query: String, language: String, key: String): List<PlaceCandidate> {
        val text = query.trim()
        if (text.length < 2 || key.isBlank()) return emptyList()
        val payload = buildJsonObject {
            put("textQuery", text)
            put("languageCode", language)
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("X-Goog-Api-Key", key)
            .header("X-Goog-FieldMask", FIELD_MASK)
            .post(payload.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).executeOrAppError().use { response ->
            val body = response.body.string()
            // Google's error text explains a disabled API or a restricted key. / Google 的错误文字会说明 API 未启用或 Key 受限。
            if (!response.isSuccessful) throw AppError.Http(SERVICE, response.code, body.take(300))
            return decodeOrBadData(SERVICE, "searchText response") {
                AppJson.standard.decodeFromString(SearchResponse.serializer(), body)
            }.places.mapNotNull { it.toCandidate() }
        }
    }

    @Serializable
    internal data class SearchResponse(val places: List<GooglePlace> = emptyList())

    @Serializable
    internal data class GooglePlace(
        val displayName: LocalizedText? = null,
        val formattedAddress: String = "",
        val location: LatLng? = null,
        val addressComponents: List<Component> = emptyList(),
    ) {
        fun toCandidate(): PlaceCandidate? {
            val at = location ?: return null
            val name = displayName?.text.orEmpty().ifBlank { formattedAddress }
            return PlaceCandidate(
                name = name,
                // The address often starts with the name itself ("Oslo S, Jernbanetorget 1"); no need to say it twice.
                // 地址经常以名称开头（「Oslo S, Jernbanetorget 1」），不必重复。
                detail = formattedAddress.takeIf { it != name }.orEmpty(),
                lat = at.latitude,
                lon = at.longitude,
                countryCode = addressComponents.firstOrNull { "country" in it.types }?.shortText.orEmpty().uppercase(),
            )
        }
    }

    @Serializable
    internal data class LocalizedText(val text: String = "")

    @Serializable
    internal data class LatLng(val latitude: Double = 0.0, val longitude: Double = 0.0)

    @Serializable
    internal data class Component(val shortText: String = "", val types: List<String> = emptyList())

    companion object {
        const val SERVICE = "Google Places"
        private const val FIELD_MASK = "places.displayName,places.formattedAddress,places.location,places.addressComponents"
        private val JSON = "application/json".toMediaType()
    }
}

/**
 * The place search the settings use: coordinates typed by hand are taken as they are; otherwise Google when the user has
 * a key, else Open-Meteo, which knows towns but not street addresses.
 * 设置页用的地点搜索：手动输入的坐标直接采用；否则有 Key 时用 Google，没有时用 Open-Meteo（只认城镇，不认街道地址）。
 */
class PlaceFinder(
    private val google: GooglePlacesSearch,
    private val fallback: PlaceSearch,
    private val googleKey: suspend () -> String,
) : PlaceSearch {
    override suspend fun search(query: String, language: String): List<PlaceCandidate> {
        PlacesPolicy.parseCoordinates(query)?.let { return listOf(it) }
        val key = googleKey()
        return if (key.isNotBlank()) google.search(query, language, key) else fallback.search(query, language)
    }
}
