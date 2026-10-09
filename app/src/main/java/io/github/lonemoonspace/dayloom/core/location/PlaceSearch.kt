package io.github.lonemoonspace.dayloom.core.location

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.json.AppJson
import io.github.lonemoonspace.dayloom.core.network.decodeOrBadData
import io.github.lonemoonspace.dayloom.core.network.executeOrAppError
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
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
 * Open-Meteo Geocoding: worldwide, free, no key; data under CC BY 4.0, credited where the search is offered.
 * Blocking call: run it off the main thread.
 * Open-Meteo 地理编码：全球、免费、无需 Key；数据为 CC BY 4.0，在提供搜索的界面注明来源。阻塞调用：不要在主线程运行。
 */
class OpenMeteoGeocoder(
    private val http: OkHttpClient,
    private val baseUrl: HttpUrl = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl(),
) : PlaceSearch {

    override suspend fun search(query: String, language: String): List<PlaceCandidate> {
        val name = query.trim()
        // The service needs at least two characters. / 该服务至少需要两个字符。
        if (name.length < 2) return emptyList()
        val url = baseUrl.newBuilder()
            .addQueryParameter("name", name)
            .addQueryParameter("count", "10")
            .addQueryParameter("language", language)
            .addQueryParameter("format", "json")
            .build()
        http.newCall(Request.Builder().url(url).build()).executeOrAppError().use { response ->
            if (!response.isSuccessful) throw AppError.Http(SERVICE, response.code)
            val body = response.body.string()
            return decodeOrBadData(SERVICE, "geocoding response") {
                AppJson.standard.decodeFromString(GeocodingResponse.serializer(), body)
            }.results.map { it.toCandidate() }
        }
    }

    @Serializable
    private data class GeocodingResponse(val results: List<Result> = emptyList())

    @Serializable
    private data class Result(
        val name: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        @SerialName("country_code") val countryCode: String = "",
        val country: String = "",
        val admin1: String = "",
        val admin2: String = "",
    ) {
        fun toCandidate() = PlaceCandidate(
            name = name,
            // Most specific region first; repeated names (a city that is also its own county) are dropped.
            // 先写最具体的地区；重复的名称（城市本身也是同名的县）去掉。
            detail = listOf(admin2, admin1, country).filter { it.isNotBlank() && it != name }.distinct().joinToString(", "),
            lat = latitude,
            lon = longitude,
            countryCode = countryCode.uppercase(),
        )
    }

    companion object {
        const val SERVICE = "Open-Meteo"
    }
}
