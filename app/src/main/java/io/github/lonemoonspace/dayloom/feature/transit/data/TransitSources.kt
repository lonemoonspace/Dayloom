package io.github.lonemoonspace.dayloom.feature.transit.data

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.uiText
import io.github.lonemoonspace.dayloom.core.refresh.CachedSource
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCadence
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.refresh.SourceInput
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.storage.SnapshotStore
import io.github.lonemoonspace.dayloom.core.time.AppClock
import io.github.lonemoonspace.dayloom.feature.transit.domain.BoardResult
import io.github.lonemoonspace.dayloom.feature.transit.domain.Boards
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteKind
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteMode
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteRoute
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteTrips
import io.github.lonemoonspace.dayloom.feature.transit.domain.FavouriteBoard
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitPolicy
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitProvider
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitStop
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class CommuteParams(val mode: CommuteMode, val origin: TransitStop, val destination: TransitStop, val options: Int)

/**
 * `transit.train` / `transit.bus`: trip options between one route's two stops on that kind of vehicle, in the direction the
 * daily windows call for.
 * `transit.train` / `transit.bus`：一条路线两个站点之间、只坐该类车辆的出行方案，方向按日常时间窗决定。
 */
class CommuteSource(
    id: SourceId,
    store: SnapshotStore<CommuteTrips>,
    clock: AppClock,
    private val kind: CommuteKind,
    route: Flow<CommuteRoute>,
    mode: Flow<CommuteMode>,
    private val provider: TransitProvider,
) : CachedSource<CommuteParams, CommuteTrips>(id, store, clock) {

    override val schemaVersion = 1

    override val maxAge: Duration = Duration.ofMinutes(30)

    // Real-time data changes by the minute around the commute; Entur is free, so the windows refresh often.
    // 通勤时段的实时数据每分钟都在变；Entur 免费，所以时间窗内刷新得勤。
    override val cadence = RefreshCadence(
        interval = Duration.ofMinutes(30),
        busyInterval = Duration.ofMinutes(5),
        busyByDay = true,
    )

    override val inputs: Flow<SourceInput<CommuteParams>> = combine(route, mode) { r, m ->
        inputFor(r.origin, r.destination, r.options, m)
    }

    override suspend fun fetch(params: CommuteParams, now: ZonedDateTime, previous: Snapshot<CommuteTrips>?): CommuteTrips {
        // A few more than shown, so the card still has options after the first ones leave. / 比显示的多取几个，前几班开走后卡片仍有方案。
        val count = params.options + EXTRA_OPTIONS
        return when (params.mode) {
            CommuteMode.OUTBOUND -> CommuteTrips(params.mode, outbound = provider.planTrips(params.origin, params.destination, now, count, kind))
            CommuteMode.INBOUND -> CommuteTrips(params.mode, inbound = provider.planTrips(params.destination, params.origin, now, count, kind))
        }
    }

    companion object {
        private const val EXTRA_OPTIONS = 2

        fun inputFor(origin: TransitStop, destination: TransitStop, options: Int, mode: CommuteMode): SourceInput<CommuteParams> {
            if (!origin.isSet || !destination.isSet) return SourceInput.Missing(uiText(R.string.transit_setup_commute))
            val count = options.coerceIn(TransitPolicy.OPTIONS_RANGE)
            return SourceInput.Ready(CommuteParams(mode, origin, destination, count), key = "${mode.name}|${origin.id}|${destination.id}|$count")
        }
    }
}

/**
 * `transit.boards`: upcoming departures of every favourite stop, filtered by its lines and destinations, in one request.
 * `transit.boards`：所有收藏站点的后续班次，按各自的线路与终点过滤，一次请求取回。
 */
class BoardsSource(
    id: SourceId,
    store: SnapshotStore<Boards>,
    clock: AppClock,
    boards: Flow<List<FavouriteBoard>>,
    private val provider: TransitProvider,
) : CachedSource<List<FavouriteBoard>, Boards>(id, store, clock) {

    override val schemaVersion = 1

    override val maxAge: Duration = Duration.ofMinutes(20)

    override val cadence = RefreshCadence(
        interval = Duration.ofMinutes(15),
        busyInterval = Duration.ofMinutes(5),
        busyByDay = true,
    )

    override val inputs: Flow<SourceInput<List<FavouriteBoard>>> = boards.map(::inputFor)

    override suspend fun fetch(params: List<FavouriteBoard>, now: ZonedDateTime, previous: Snapshot<Boards>?): Boards {
        val departures = provider.departures(params.map { it.stop.id }.distinct(), now)
        return Boards(
            params.map { board ->
                BoardResult(board.id, board.stop.name, TransitPolicy.filterBoard(board, departures[board.stop.id].orEmpty(), now))
            },
        )
    }

    companion object {
        fun inputFor(boards: List<FavouriteBoard>): SourceInput<List<FavouriteBoard>> {
            val usable = boards.filter { it.stop.isSet }
            if (usable.isEmpty()) return SourceInput.Missing(uiText(R.string.transit_setup_boards))
            // Filters are part of the key: the snapshot holds filtered departures. / 过滤条件是指纹的一部分：快照里存的是过滤后的班次。
            val key = usable.joinToString(";") { "${it.id}=${it.stop.id}|${it.lines.joinToString(",")}|${it.destinations.joinToString(",")}" }
            return SourceInput.Ready(usable, key = key)
        }
    }
}
