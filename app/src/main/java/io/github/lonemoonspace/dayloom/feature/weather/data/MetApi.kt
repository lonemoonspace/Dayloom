package io.github.lonemoonspace.dayloom.feature.weather.data

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.json.AppJson
import io.github.lonemoonspace.dayloom.core.location.PlacesPolicy
import io.github.lonemoonspace.dayloom.core.network.decodeOrBadData
import io.github.lonemoonspace.dayloom.core.network.executeOrAppError
import io.github.lonemoonspace.dayloom.feature.weather.domain.Forecast
import io.github.lonemoonspace.dayloom.feature.weather.domain.ForecastPoint
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * MET Norway Locationforecast 2.0 (`complete`, for feels-like, gusts, UV and rain probability), worldwide. The terms ask for an identifying User-Agent (set by the shared client),
 * at most four decimals in coordinates and conditional requests, so an unchanged forecast costs MET nothing.
 * Blocking call: the refresh coordinator runs it on the IO dispatcher.
 * MET Norway Locationforecast 2.0（`complete`，为了体感温度、阵风、紫外线与降水概率），全球可用。条款要求带标识的 User-Agent（由共享客户端设置）、坐标最多四位小数、
 * 使用条件请求，预报没变时不给 MET 增加负担。阻塞调用：刷新协调器在 IO 调度器上运行它。
 */
class MetApi(
    private val http: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api.met.no/weatherapi/locationforecast/2.0/complete".toHttpUrl(),
    private val fallbackUrl: HttpUrl = "https://api.met.no/weatherapi/locationforecast/2.0/compact".toHttpUrl(),
) {
    sealed interface Result {
        data class Fresh(val forecast: Forecast) : Result

        /** HTTP 304: keep the forecast we already have. / HTTP 304：沿用已有的预报。 */
        data object NotModified : Result
    }

    /**
     * MET has served `complete` bodies cut off mid-JSON (a matching Content-Length, so only the decoder notices) while
     * `compact` stayed whole. An unreadable `complete` therefore falls back to `compact`: the card loses feels-like, gusts,
     * UV and rain probability for that hour instead of showing an old forecast. HTTP errors do not fall back, since `compact`
     * sits behind the same server and limits.
     * MET 出现过 `complete` 返回的内容在 JSON 中途被截断（Content-Length 与之相符，只有解析时才发现），而 `compact` 完好。
     * 所以 `complete` 读不了时改用 `compact`：这一小时卡片少了体感、阵风、紫外线与降水概率，但不会停在旧预报上。
     * HTTP 错误不切换，`compact` 与它在同一台服务器、同一套限流之后。
     *
     * A forecast taken from `compact` carries no `Last-Modified`: both endpoints share it, so sending it back would make
     * `complete` answer 304 and the reduced forecast would stay until MET's next model run.
     * 取自 `compact` 的预报不记 `Last-Modified`：两个接口的这个时间相同，回传它会让 `complete` 回 304，缺项的预报就会一直留到 MET
     * 下一轮更新。
     */
    fun fetch(lat: Double, lon: Double, ifModifiedSince: String?): Result = try {
        fetchFrom(baseUrl, lat, lon, ifModifiedSince)
    } catch (e: AppError.BadData) {
        try {
            when (val result = fetchFrom(fallbackUrl, lat, lon, ifModifiedSince)) {
                is Result.Fresh -> Result.Fresh(result.forecast.copy(lastModified = ""))
                Result.NotModified -> result
            }
        } catch (fallback: AppError) {
            // Report the original failure: it names the endpoint the app normally reads. / 报告最初的失败：它对应平常读取的接口。
            e.addSuppressed(fallback)
            throw e
        }
    }

    private fun fetchFrom(baseUrl: HttpUrl, lat: Double, lon: Double, ifModifiedSince: String?): Result {
        val url = baseUrl.newBuilder()
            .addQueryParameter("lat", PlacesPolicy.formatCoordinate(lat))
            .addQueryParameter("lon", PlacesPolicy.formatCoordinate(lon))
            .build()
        val request = Request.Builder().url(url).apply {
            if (!ifModifiedSince.isNullOrBlank()) header("If-Modified-Since", ifModifiedSince)
        }.build()
        http.newCall(request).executeOrAppError().use { response ->
            if (response.code == 304) return Result.NotModified
            // 203 means "this API version is deprecated" but the body is still valid. / 203 表示该版本已弃用，但内容仍然有效。
            if (!response.isSuccessful) throw AppError.Http(SERVICE, response.code)
            val body = response.body.string()
            val dto = decodeOrBadData(SERVICE, "locationforecast response") {
                AppJson.standard.decodeFromString(MetResponse.serializer(), body)
            }
            val forecast = parse(dto, response.header("Last-Modified").orEmpty())
            if (forecast.points.isEmpty()) throw AppError.BadData(SERVICE, "empty timeseries")
            return Result.Fresh(forecast)
        }
    }

    companion object {
        const val SERVICE = "MET Norway"

        internal fun parse(dto: MetResponse, lastModified: String): Forecast = Forecast(
            updatedAt = epochMillis(dto.properties.meta.updatedAt) ?: 0,
            lastModified = lastModified,
            points = dto.properties.timeseries.mapNotNull { step ->
                val time = epochMillis(step.time) ?: return@mapNotNull null
                ForecastPoint(
                    time = time,
                    temperature = step.data.instant.details.airTemperature,
                    windSpeed = step.data.instant.details.windSpeed,
                    symbol1h = step.data.next1h?.summary?.symbolCode.orEmpty(),
                    precipitation1h = step.data.next1h?.details?.precipitationAmount,
                    symbol6h = step.data.next6h?.summary?.symbolCode.orEmpty(),
                    precipitation6h = step.data.next6h?.details?.precipitationAmount,
                    apparentTemperature = step.data.instant.details.apparentTemperature,
                    windGust = step.data.instant.details.windGust,
                    uvIndex = step.data.instant.details.uvIndex,
                    precipProbability1h = step.data.next1h?.details?.precipitationProbability,
                    thunderProbability1h = step.data.next1h?.details?.thunderProbability,
                    precipProbability6h = step.data.next6h?.details?.precipitationProbability,
                    temperatureMin6h = step.data.next6h?.details?.temperatureMin,
                    temperatureMax6h = step.data.next6h?.details?.temperatureMax,
                )
            },
        )

        private fun epochMillis(value: String): Long? = try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

@Serializable
internal data class MetResponse(val properties: Properties = Properties()) {
    @Serializable
    data class Properties(val meta: Meta = Meta(), val timeseries: List<Step> = emptyList())

    @Serializable
    data class Meta(@SerialName("updated_at") val updatedAt: String = "")

    @Serializable
    data class Step(val time: String = "", val data: StepData = StepData())

    @Serializable
    data class StepData(
        val instant: InstantData = InstantData(),
        @SerialName("next_1_hours") val next1h: Period? = null,
        @SerialName("next_6_hours") val next6h: Period? = null,
    )

    @Serializable
    data class InstantData(val details: InstantDetails = InstantDetails())

    @Serializable
    data class InstantDetails(
        @SerialName("air_temperature") val airTemperature: Double? = null,
        @SerialName("wind_speed") val windSpeed: Double? = null,
        @SerialName("apparent_air_temperature") val apparentTemperature: Double? = null,
        @SerialName("wind_speed_of_gust") val windGust: Double? = null,
        @SerialName("ultraviolet_index_clear_sky") val uvIndex: Double? = null,
    )

    @Serializable
    data class Period(val summary: Summary = Summary(), val details: PeriodDetails = PeriodDetails())

    @Serializable
    data class Summary(@SerialName("symbol_code") val symbolCode: String = "")

    @Serializable
    data class PeriodDetails(
        @SerialName("precipitation_amount") val precipitationAmount: Double? = null,
        @SerialName("probability_of_precipitation") val precipitationProbability: Double? = null,
        @SerialName("probability_of_thunder") val thunderProbability: Double? = null,
        @SerialName("air_temperature_min") val temperatureMin: Double? = null,
        @SerialName("air_temperature_max") val temperatureMax: Double? = null,
    )
}
