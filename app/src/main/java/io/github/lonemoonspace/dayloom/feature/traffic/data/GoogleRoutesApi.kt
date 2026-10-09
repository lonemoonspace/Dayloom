package io.github.lonemoonspace.dayloom.feature.traffic.data

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.json.AppJson
import io.github.lonemoonspace.dayloom.core.network.decodeOrBadData
import io.github.lonemoonspace.dayloom.core.network.executeOrAppError
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class RouteInfo(val duration: String = "", val staticDuration: String = "", val distanceMeters: Long = 0)

@Serializable
private data class RoutesEnvelope(val routes: List<RouteInfo> = emptyList())

/**
 * Google Routes `computeRoutes` with the user's own key. Coordinates come from the saved places, so no address geocoding
 * can fail. Blocking call: the refresh coordinator runs it on the IO dispatcher.
 * 使用用户自己 Key 的 Google Routes `computeRoutes`。坐标取自已保存地点，不会因地址解析失败而出错。
 * 阻塞调用：刷新协调器在 IO 调度器上运行它。
 */
class GoogleRoutesApi(
    private val http: OkHttpClient,
    private val endpoint: HttpUrl = "https://routes.googleapis.com/directions/v2:computeRoutes".toHttpUrl(),
) {
    /** [departureTime] must lie in the future for TRAFFIC_AWARE; the caller adds a margin. / TRAFFIC_AWARE 要求 [departureTime] 在未来，调用方负责留余量。 */
    fun compute(apiKey: String, origin: Pair<Double, Double>, destination: Pair<Double, Double>, departureTime: ZonedDateTime): RouteInfo {
        val payload = buildJsonObject {
            put("origin", location(origin))
            put("destination", location(destination))
            put("travelMode", "DRIVE")
            put("routingPreference", "TRAFFIC_AWARE")
            put("departureTime", departureTime.withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            put("units", "METRIC")
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("X-Goog-Api-Key", apiKey)
            .header("X-Goog-FieldMask", "routes.duration,routes.staticDuration,routes.distanceMeters")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).executeOrAppError().use { response ->
            val body = response.body.string()
            // Google's error text explains a disabled API or a restricted key, so a short excerpt is kept for display.
            // Google 的错误文字会说明 API 未启用或 Key 受限，所以保留一小段用于展示。
            if (!response.isSuccessful) throw AppError.Http(SERVICE, response.code, body.take(300))
            val envelope = decodeOrBadData(SERVICE, "computeRoutes response") {
                AppJson.standard.decodeFromString(RoutesEnvelope.serializer(), body)
            }
            return envelope.routes.firstOrNull() ?: throw AppError.BadData(SERVICE, "no route returned")
        }
    }

    private fun location(latLng: Pair<Double, Double>): JsonObject = buildJsonObject {
        put("location", buildJsonObject {
            put("latLng", buildJsonObject {
                put("latitude", latLng.first)
                put("longitude", latLng.second)
            })
        })
    }

    companion object {
        const val SERVICE = "Google Routes"
        private val JSON = "application/json".toMediaType()
    }
}
