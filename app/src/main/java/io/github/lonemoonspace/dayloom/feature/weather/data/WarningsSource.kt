package io.github.lonemoonspace.dayloom.feature.weather.data

import io.github.lonemoonspace.dayloom.R
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
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherWarnings
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * `weather.warnings`: MET's warnings for the place the weather module shows. Places known to be outside Norway are not
 * asked about, since MET only warns for Norway.
 * `weather.warnings`：天气模块所显示地点的 MET 预警。已知在挪威以外的地点不去查询，MET 只发布挪威的预警。
 */
class WarningsSource(
    id: SourceId,
    store: SnapshotStore<WeatherWarnings>,
    clock: AppClock,
    place: Flow<Place?>,
    private val api: MetAlertsApi,
) : CachedSource<WeatherParams, WeatherWarnings>(id, store, clock) {

    override val schemaVersion = 1

    override val maxAge: Duration = Duration.ofHours(3)

    override val cadence = RefreshCadence(
        interval = Duration.ofMinutes(60),
        busyInterval = Duration.ofMinutes(30),
        busyByDay = true,
    )

    override val inputs: Flow<SourceInput<WeatherParams>> = place.map(::inputFor)

    override suspend fun fetch(params: WeatherParams, now: ZonedDateTime, previous: Snapshot<WeatherWarnings>?): WeatherWarnings =
        api.fetch(params.lat, params.lon)

    companion object {
        fun inputFor(place: Place?): SourceInput<WeatherParams> {
            if (place == null) return SourceInput.Missing(uiText(R.string.weather_setup_place))
            // An unknown country (older saved places) is asked about: abroad, MET simply answers with nothing.
            // 国家未知（较早保存的地点）时照样查询：在国外，MET 只会返回空。
            if (outsideNorway(place)) {
                return SourceInput.Missing(uiText(R.string.weather_warnings_norway_only))
            }
            val key = PlacesPolicy.formatCoordinate(place.lat) + "," + PlacesPolicy.formatCoordinate(place.lon)
            return SourceInput.Ready(WeatherParams(place.lat, place.lon), key = key)
        }

        /** Known to be abroad; an unknown country counts as Norway. / 已知在国外；国家未知时按挪威算。 */
        fun outsideNorway(place: Place): Boolean = place.countryCode.isNotEmpty() && !place.countryCode.equals("NO", ignoreCase = true)
    }
}
