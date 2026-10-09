package io.github.lonemoonspace.dayloom.feature.traffic.data

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.i18n.uiText
import io.github.lonemoonspace.dayloom.core.location.Place
import io.github.lonemoonspace.dayloom.core.location.PlacesPolicy
import io.github.lonemoonspace.dayloom.core.refresh.CachedSource
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCadence
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.refresh.SourceInput
import io.github.lonemoonspace.dayloom.core.secret.SecretState
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.storage.SnapshotStore
import io.github.lonemoonspace.dayloom.core.time.AppClock
import io.github.lonemoonspace.dayloom.feature.traffic.domain.Direction
import io.github.lonemoonspace.dayloom.feature.traffic.domain.TrafficPolicy
import io.github.lonemoonspace.dayloom.feature.traffic.domain.TrafficStatus
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Objects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class TrafficParams(val direction: Direction, val origin: Pair<Double, Double>, val destination: Pair<Double, Double>)

/**
 * `traffic.route`: driving time between the two configured places, in the direction the daily windows call for.
 * `traffic.route`：两个设定地点之间的驾车时间，方向按日常时间窗决定。
 */
class TrafficSource(
    id: SourceId,
    store: SnapshotStore<TrafficStatus>,
    clock: AppClock,
    from: Flow<Place?>,
    to: Flow<Place?>,
    direction: Flow<Direction>,
    secret: Flow<SecretState>,
    private val apiKey: suspend () -> String,
    private val api: GoogleRoutesApi,
) : CachedSource<TrafficParams, TrafficStatus>(id, store, clock) {

    override val schemaVersion = 1

    override val maxAge: Duration = Duration.ofHours(2)

    // Every request may cost the user money beyond Google's free tier, so it refreshes hourly and only more often around the
    // commute.
    // 每次请求超出 Google 免费额度后都可能让用户付费，所以平时每小时刷新一次，只在通勤时段更频繁。
    override val cadence = RefreshCadence(
        interval = Duration.ofMinutes(60),
        busyInterval = Duration.ofMinutes(15),
        busyInWindows = true,
    )

    override val inputs: Flow<SourceInput<TrafficParams>> = combine(from, to, direction, secret, ::inputFor)

    override suspend fun fetch(params: TrafficParams, now: ZonedDateTime, previous: Snapshot<TrafficStatus>?): TrafficStatus {
        // An unreadable key counts as unset ("" from usable), so ciphertext is never sent. / 解不开的 Key 视同未设置（usable 为 ""），密文永远不会被发出。
        val key = apiKey().ifBlank { throw AppError.NotConfigured(uiText(R.string.traffic_setup_key)) }
        val route = api.compute(key, params.origin, params.destination, departureTime = now.plusSeconds(60))
        // duration is the only time source; reading it as 0 would show "clear" — the opposite of the truth.
        // duration 是唯一的时长来源；当成 0 会显示「畅通」，方向正好反了。
        val duration = TrafficPolicy.parseSeconds(route.duration)
            ?: throw AppError.BadData(GoogleRoutesApi.SERVICE, "unreadable duration: ${route.duration}")
        // A missing free-flow time only costs the congestion level. / 缺少畅通时长只影响拥堵等级。
        val static = TrafficPolicy.parseSeconds(route.staticDuration)
        val (delay, level) = TrafficPolicy.level(duration, static)
        return TrafficStatus(
            durationSec = duration,
            staticDurationSec = static ?: 0,
            delaySec = delay,
            distanceMeters = route.distanceMeters,
            level = level,
            direction = params.direction,
        )
    }

    companion object {
        fun inputFor(from: Place?, to: Place?, direction: Direction, secret: SecretState): SourceInput<TrafficParams> {
            if (from == null || to == null) return SourceInput.Missing(uiText(R.string.traffic_setup_places))
            if (secret.display.isBlank()) return SourceInput.Missing(uiText(R.string.traffic_setup_key))
            val (origin, destination) = if (direction == Direction.TO_WORK) Pair(from, to) else Pair(to, from)
            val key = "${direction.name}|${coordinates(origin)}|${coordinates(destination)}"
            return SourceInput.Ready(
                params = TrafficParams(direction, origin.lat to origin.lon, destination.lat to destination.lon),
                key = key,
                // A new key should refetch; only a hash goes in, never the key itself. / 换了 Key 要重抓；放进去的只是哈希，绝不是 Key 本身。
                refreshKey = key to Objects.hash(secret.display, secret.unreadable),
            )
        }

        private fun coordinates(place: Place) = PlacesPolicy.formatCoordinate(place.lat) + "," + PlacesPolicy.formatCoordinate(place.lon)
    }
}
