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
import io.github.lonemoonspace.dayloom.feature.transit.domain.OpposingPolicy
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitPolicy
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitProvider
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitStop
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class CommuteParams(
    val mode: CommuteMode,
    val origin: TransitStop,
    val destination: TransitStop,
    val options: Int,
    val opposing: Boolean = false,
)

/**
 * `train.commute` / `bus.commute`: trip options between one route's two stops on that kind of vehicle, towards the
 * destination before noon and back from noon on ([TransitPolicy.commuteMode]).
 * `train.commute` / `bus.commute`：一条路线两个站点之间、只坐该类车辆的出行方案，中午前去程、中午起回程（[TransitPolicy.commuteMode]）。
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

    // Real-time data changes by the minute; Entur is free, so the daytime refreshes often.
    // 实时数据每分钟都在变；Entur 免费，所以白天刷新得勤。
    override val cadence = RefreshCadence(
        interval = Duration.ofMinutes(30),
        busyInterval = Duration.ofMinutes(5),
        busyByDay = true,
    )

    /**
     * Every minute while a train has passed its expected departure since the last fetch, so a fresh delay shows quickly
     * instead of after the usual five minutes.
     * 上次抓取之后有车过了预计发车时刻时每分钟刷新一次，新报的晚点很快就能显示，而不用等平时的五分钟。
     */
    override fun cadence(current: Snapshot<CommuteTrips>?): RefreshCadence {
        val snapshot = current ?: return cadence
        return TransitPolicy.commuteCadence(snapshot.value.current, snapshot.fetchedAt, clock.now(), cadence)
    }

    override val inputs: Flow<SourceInput<CommuteParams>> = combine(route, mode) { r, m ->
        inputFor(r.origin, r.destination, r.options, m, opposing = kind == CommuteKind.TRAIN && r.opposingHint)
    }

    override suspend fun fetch(params: CommuteParams, now: ZonedDateTime, previous: Snapshot<CommuteTrips>?): CommuteTrips {
        // A few more than shown, so the card still has options after the first ones leave. / 比显示的多取几个，前几班开走后卡片仍有方案。
        val count = params.options + EXTRA_OPTIONS
        val first = if (params.mode == CommuteMode.OUTBOUND) params.origin else params.destination
        // Independent of the trips, so it runs alongside instead of adding a round trip. / 与行程无关，并行请求，不多等一轮。
        return coroutineScope {
            val opposing = if (params.opposing) async { opposingDepartures(first, now) } else null
            val trips = when (params.mode) {
                CommuteMode.OUTBOUND -> CommuteTrips(params.mode, outbound = provider.planTrips(params.origin, params.destination, now, count, kind))
                CommuteMode.INBOUND -> CommuteTrips(params.mode, inbound = provider.planTrips(params.destination, params.origin, now, count, kind))
            }
            trips.copy(opposing = opposing?.await().orEmpty())
        }
    }

    /**
     * The hint is a guess, so failing to fetch it must not fail the trips. Starting a little early keeps an opposing train
     * that is already past its timetable time.
     * 这个提示只是推测，取不到也不能让行程跟着失败。起始时间提前一点，已经过了时刻表时间的对向车也能拿到。
     */
    private suspend fun opposingDepartures(stop: TransitStop, now: ZonedDateTime) = try {
        provider.departures(listOf(stop.id), now.minus(OpposingPolicy.CROSSING_WINDOW))[stop.id].orEmpty().filter { it.mode == "rail" && !it.replacementBus }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        private const val EXTRA_OPTIONS = 2

        fun inputFor(
            origin: TransitStop,
            destination: TransitStop,
            options: Int,
            mode: CommuteMode,
            opposing: Boolean = false,
        ): SourceInput<CommuteParams> {
            if (!origin.isSet || !destination.isSet) return SourceInput.Missing(uiText(R.string.transit_setup_commute))
            val count = options.coerceIn(TransitPolicy.OPTIONS_RANGE)
            val key = "${mode.name}|${origin.id}|${destination.id}|$count" + if (opposing) "|opposing" else ""
            return SourceInput.Ready(CommuteParams(mode, origin, destination, count, opposing), key = key)
        }
    }
}

/**
 * `departures.boards`: upcoming departures of every favourite stop, filtered by its lines and destinations, in one request.
 * `departures.boards`：所有收藏站点的后续班次，按各自的线路与终点过滤，一次请求取回。
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
