package io.github.lonemoonspace.dayloom.feature.weather.data

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.i18n.uiText
import io.github.lonemoonspace.dayloom.core.location.Place
import io.github.lonemoonspace.dayloom.core.location.PlacesPolicy
import io.github.lonemoonspace.dayloom.core.refresh.CachedSource
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCadence
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.refresh.SourceInput
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.storage.SnapshotStore
import io.github.lonemoonspace.dayloom.core.time.AppClock
import io.github.lonemoonspace.dayloom.feature.weather.domain.Forecast
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherPointPicker
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class WeatherParams(val lat: Double, val lon: Double)

/**
 * `weather.forecast`: the MET forecast for Home.
 * `weather.forecast`：家所在地的 MET 预报。
 */
class WeatherSource(
    id: SourceId,
    store: SnapshotStore<Forecast>,
    clock: AppClock,
    place: Flow<Place?>,
    private val api: MetApi,
) : CachedSource<WeatherParams, Forecast>(id, store, clock) {

    override val schemaVersion = 2

    override val maxAge: Duration = Duration.ofHours(6)

    override val cadence = RefreshCadence(
        interval = Duration.ofMinutes(60),
        busyInterval = Duration.ofMinutes(30),
        busyInWindows = true,
    )

    override val inputs: Flow<SourceInput<WeatherParams>> = place.map(::inputFor)

    override suspend fun fetch(params: WeatherParams, now: ZonedDateTime, previous: Snapshot<Forecast>?): Forecast {
        val forecast = when (val result = api.fetch(params.lat, params.lon, previous?.value?.lastModified)) {
            is MetApi.Result.Fresh -> result.forecast
            // Only sent with a previous snapshot, so it exists; refetch unconditionally if it somehow does not.
            // 只有存在上一份快照时才发条件请求，所以它一定存在；万一没有，就无条件重抓。
            MetApi.Result.NotModified -> previous?.value
                ?: (api.fetch(params.lat, params.lon, ifModifiedSince = null) as MetApi.Result.Fresh).forecast
        }
        return validated(forecast, now)
    }

    /**
     * Freshness follows the time of the point shown as current, not the fetch time: a fetch still succeeds when MET stopped
     * updating, while the point may be hours old.
     * 新鲜度看当前读数所取数据点的时刻，而不是抓取时刻：MET 断更时抓取照样成功，数据点却可能已是几小时前的。
     */
    override fun dataTime(snapshot: Snapshot<Forecast>): Instant =
        snapshot.value.observedAt.takeIf { it > 0 }?.let(Instant::ofEpochMilli) ?: super.dataTime(snapshot)

    companion object {
        /**
         * Missing data is an error that keeps the previous snapshot, never a 0: "could not read" shown as "0 °C, calm, clear"
         * is indistinguishable from a real reading. Only the point actually shown is checked, so one odd step does not sink the
         * whole response.
         * 缺数据一律报错、沿用上一份快照，不兜成 0：「取不到」显示成「0℃ 无风晴天」与真实读数无法区分。只校验真正要显示的那个点，
         * 单个异常时次不拖垮整个响应。
         */
        fun validated(forecast: Forecast, now: ZonedDateTime): Forecast {
            val current = WeatherPointPicker.pick(forecast.points, now) { Instant.ofEpochMilli(it.time).atZone(now.zone) }
                ?: throw AppError.BadData(MetApi.SERVICE, "no time steps")
            if (current.temperature == null) throw AppError.BadData(MetApi.SERVICE, "current step has no air_temperature")
            if (current.windSpeed == null) throw AppError.BadData(MetApi.SERVICE, "current step has no wind_speed")
            if (current.symbol.isEmpty()) throw AppError.BadData(MetApi.SERVICE, "current step has no symbol_code")
            return forecast.copy(observedAt = current.time)
        }

        fun inputFor(place: Place?): SourceInput<WeatherParams> {
            if (place == null) return SourceInput.Missing(uiText(R.string.weather_setup_place))
            val key = PlacesPolicy.formatCoordinate(place.lat) + "," + PlacesPolicy.formatCoordinate(place.lon)
            return SourceInput.Ready(WeatherParams(place.lat, place.lon), key = key)
        }
    }
}
