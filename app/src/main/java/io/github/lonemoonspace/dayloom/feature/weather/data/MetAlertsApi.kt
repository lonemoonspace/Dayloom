package io.github.lonemoonspace.dayloom.feature.weather.data

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.json.AppJson
import io.github.lonemoonspace.dayloom.core.location.PlacesPolicy
import io.github.lonemoonspace.dayloom.core.network.decodeOrBadData
import io.github.lonemoonspace.dayloom.core.network.executeOrAppError
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherWarning
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherWarnings
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * MET Norway MetAlerts 2.0: the warnings in force at a point, in English. Norway and its waters only; anywhere else the
 * answer is simply empty. Same terms as the forecast (identifying User-Agent from the shared client, four decimals).
 * Blocking call: the refresh coordinator runs it on the IO dispatcher.
 * MET Norway MetAlerts 2.0：某个点当前有效的预警，英文。只覆盖挪威及其海域；其他地方返回空。条款与预报相同（共享客户端带标识
 * User-Agent、坐标四位小数）。阻塞调用：刷新协调器在 IO 调度器上运行它。
 */
class MetAlertsApi(
    private val http: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api.met.no/weatherapi/metalerts/2.0/current.json".toHttpUrl(),
) {
    fun fetch(lat: Double, lon: Double): WeatherWarnings {
        val url = baseUrl.newBuilder()
            .addQueryParameter("lat", PlacesPolicy.formatCoordinate(lat))
            .addQueryParameter("lon", PlacesPolicy.formatCoordinate(lon))
            .addQueryParameter("lang", "en")
            .build()
        http.newCall(Request.Builder().url(url).build()).executeOrAppError().use { response ->
            if (!response.isSuccessful) throw AppError.Http(SERVICE, response.code)
            val body = response.body.string()
            val dto = decodeOrBadData(SERVICE, "metalerts response") {
                AppJson.standard.decodeFromString(AlertsResponse.serializer(), body)
            }
            return parse(dto)
        }
    }

    companion object {
        const val SERVICE = "MET Norway"

        /**
         * Land warnings only: a point on the coast also lies in sea areas, whose gale warnings are for boats. Cancellations
         * and warnings without a readable time are dropped.
         * 只要陆地预警：海边的点也落在海区里，那里的大风预警是给船的。撤销通知与时间读不出的预警丢弃。
         */
        internal fun parse(dto: AlertsResponse): WeatherWarnings = WeatherWarnings(
            dto.features.mapNotNull { feature ->
                val p = feature.properties
                if (p.geographicDomain.equals("marine", ignoreCase = true)) return@mapNotNull null
                if (p.type.equals("Cancel", ignoreCase = true)) return@mapNotNull null
                val start = feature.time.interval.getOrNull(0)?.let(::epochMillis) ?: return@mapNotNull null
                val end = feature.time.interval.getOrNull(1)?.let(::epochMillis) ?: return@mapNotNull null
                WeatherWarning(
                    id = p.id,
                    event = p.event,
                    level = p.awarenessLevel.substringBefore(';').trim().toIntOrNull() ?: 0,
                    start = start,
                    end = end,
                    description = p.description.trim(),
                    instruction = p.instruction.trim(),
                )
            },
        )

        private fun epochMillis(value: String): Long? = try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

@Serializable
internal data class AlertsResponse(val features: List<Feature> = emptyList()) {
    @Serializable
    data class Feature(val properties: Properties = Properties(), @SerialName("when") val time: When = When())

    @Serializable
    data class When(val interval: List<String> = emptyList())

    @Serializable
    data class Properties(
        val id: String = "",
        val event: String = "",
        val type: String = "",
        @SerialName("awareness_level") val awarenessLevel: String = "",
        val geographicDomain: String = "",
        val description: String = "",
        val instruction: String = "",
    )
}
