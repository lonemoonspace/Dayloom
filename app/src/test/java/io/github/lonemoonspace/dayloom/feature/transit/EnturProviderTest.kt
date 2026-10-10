package io.github.lonemoonspace.dayloom.feature.transit

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.json.AppJson
import io.github.lonemoonspace.dayloom.core.network.FakeNetworkStatus
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCoordinator
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.refresh.SourceInput
import io.github.lonemoonspace.dayloom.core.refresh.Trigger
import io.github.lonemoonspace.dayloom.core.storage.InMemorySnapshotStore
import io.github.lonemoonspace.dayloom.core.time.FixedClock
import io.github.lonemoonspace.dayloom.feature.transit.data.BoardsSource
import io.github.lonemoonspace.dayloom.feature.transit.data.CommuteParams
import io.github.lonemoonspace.dayloom.feature.transit.data.CommuteSource
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteKind
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteRoute
import io.github.lonemoonspace.dayloom.feature.transit.data.EnturProvider
import io.github.lonemoonspace.dayloom.feature.transit.domain.BoardDeparture
import io.github.lonemoonspace.dayloom.feature.transit.domain.Boards
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteMode
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteTrips
import io.github.lonemoonspace.dayloom.feature.transit.domain.FavouriteBoard
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitProvider
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitStop
import io.github.lonemoonspace.dayloom.feature.transit.domain.TripOption
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EnturProviderTest {

    private val zone = ZoneId.of("Europe/Oslo")
    private val now = ZonedDateTime.of(2026, 10, 5, 7, 30, 0, 0, zone)
    private val a = TransitStop("NSR:StopPlace:1", "Stop A")
    private val b = TransitStop("NSR:StopPlace:2", "Stop B")

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun provider() = EnturProvider(OkHttpClient(), server.url("/journey-planner/v3/graphql"), server.url("/geocoder/v3/autocomplete"))

    private val tripBody = """
        {"data":{"trip":{"tripPatterns":[
          {"legs":[
            {"mode":"rail","realtime":true,"aimedStartTime":"2026-10-05T07:40:00+02:00","expectedStartTime":"2026-10-05T07:52:00+02:00",
             "aimedEndTime":"2026-10-05T08:10:00+02:00","expectedEndTime":"2026-10-05T08:22:00+02:00","line":{"publicCode":"R1"},
             "fromPlace":{"name":"Stop A"},"toPlace":{"name":"Stop C"},"fromEstimatedCall":{"cancellation":false,"destinationDisplay":{"frontText":"Town Z"}},
             "toEstimatedCall":{"cancellation":false}},
            {"mode":"foot","realtime":false,"aimedStartTime":"2026-10-05T08:22:00+02:00","aimedEndTime":"2026-10-05T08:25:00+02:00",
             "fromPlace":{"name":"Stop C"},"toPlace":{"name":"Stop C"}},
            {"mode":"bus","realtime":false,"aimedStartTime":"2026-10-05T08:30:00+02:00","aimedEndTime":"2026-10-05T08:45:00+02:00",
             "line":{"publicCode":"2"},"fromPlace":{"name":"Stop C"},"toPlace":{"name":"Stop B"},"fromEstimatedCall":{"cancellation":true}}
          ]},
          {"legs":[{"mode":"foot","realtime":false,"aimedStartTime":"2026-10-05T07:40:00+02:00","aimedEndTime":"2026-10-05T09:40:00+02:00"}]}
        ]}}}
    """.trimIndent()

    @Test
    fun `a trip keeps rides, drops walking, and carries real-time and cancellation per leg`() = runTest {
        server.enqueue(MockResponse().setBody(tripBody))

        val options = provider().planTrips(a, b, now, 3, CommuteKind.TRAIN)

        assertEquals(1, options.size)
        val (rail, bus) = options.single().legs
        assertEquals("R1", rail.line)
        assertEquals("Town Z", rail.frontText)
        assertEquals(12 * 60_000L, rail.expectedDeparture - rail.aimedDeparture)
        assertTrue(rail.realtime)
        assertFalse(rail.cancelled)
        // Without expected times the aimed ones stand in; the leg stays "no real-time". / 没有预计时刻时用计划时刻代替；该段仍是「实时未知」。
        assertEquals(bus.aimedDeparture, bus.expectedDeparture)
        assertFalse(bus.realtime)
        assertTrue(bus.cancelled)
        assertEquals(1, options.single().transfers)
    }

    @Test
    fun `ids travel as variables, never inside the query, with the client header`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":{"trip":{"tripPatterns":[]}}}"""))
        val hostile = TransitStop("x\" } evil { \"", "Hostile")

        provider().planTrips(hostile, b, now, 2, CommuteKind.BUS)

        val request = server.takeRequest()
        assertEquals(EnturProvider.CLIENT_NAME, request.getHeader(EnturProvider.CLIENT_HEADER))
        val body = AppJson.standard.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertFalse(body.getValue("query").jsonPrimitive.content.contains("evil"))
        assertEquals(hostile.id, body.getValue("variables").jsonObject.getValue("from").jsonPrimitive.content)
        assertTrue(body.getValue("query").jsonPrimitive.content.contains("includeRealtimeCancellations: true"))
        // The bus commute asks for buses and coaches only. / 公交通勤只要公交与长途大巴。
        val modes = body.getValue("variables").jsonObject.getValue("modes").jsonArray
        assertEquals(listOf("bus", "coach"), modes.map { it.jsonObject.getValue("transportMode").jsonPrimitive.content })
    }

    @Test
    fun `train commutes also ask for rail replacement buses and flag them`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"trip":{"tripPatterns":[{"legs":[
                {"mode":"bus","transportSubmode":"railReplacementBus","aimedStartTime":"2026-10-05T07:40:00+02:00",
                 "aimedEndTime":"2026-10-05T08:05:00+02:00","line":{"publicCode":"R1"},"fromPlace":{"name":"Stop A street"},"toPlace":{"name":"Stop B"}},
                {"mode":"bus","transportSubmode":"localBus","aimedStartTime":"2026-10-05T08:10:00+02:00",
                 "aimedEndTime":"2026-10-05T08:20:00+02:00","line":{"publicCode":"2"},"fromPlace":{"name":"Stop B"},"toPlace":{"name":"Stop C"}}
                ]}]}}}""",
            ),
        )

        val (replacement, ordinary) = provider().planTrips(a, b, now, 3, CommuteKind.TRAIN).single().legs

        assertTrue(replacement.replacementBus)
        assertEquals("R1", replacement.line)
        assertFalse(ordinary.replacementBus)
        val modes = AppJson.standard.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            .getValue("variables").jsonObject.getValue("modes").jsonArray.map { it.jsonObject }
        assertEquals(listOf("rail", "bus"), modes.map { it.getValue("transportMode").jsonPrimitive.content })
        assertFalse("transportSubModes" in modes[0])
        assertEquals(listOf("railReplacementBus"), modes[1].getValue("transportSubModes").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `graphql errors, http errors and garbage become AppErrors`() = runTest {
        server.enqueue(MockResponse().setBody("""{"errors":[{"message":"bad place"}]}"""))
        assertTrue(runCatching { provider().planTrips(a, b, now, 1, CommuteKind.TRAIN) }.exceptionOrNull() is AppError.BadData)
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(503, (runCatching { provider().planTrips(a, b, now, 1, CommuteKind.TRAIN) }.exceptionOrNull() as AppError.Http).code)
        server.enqueue(MockResponse().setBody("<html>"))
        assertTrue(runCatching { provider().planTrips(a, b, now, 1, CommuteKind.TRAIN) }.exceptionOrNull() is AppError.BadData)
    }

    @Test
    fun `departures map each stop, and a retired stop is simply missing`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"stop0":{"estimatedCalls":[{"realtime":true,"cancellation":false,
                "aimedDepartureTime":"2026-10-05T07:35:00+02:00","expectedDepartureTime":"2026-10-05T07:37:00+02:00",
                "destinationDisplay":{"frontText":"Town C"},"quay":{"publicCode":"B"},"serviceJourney":{"line":{"publicCode":"31","transportMode":"bus"}}},
                {"aimedDepartureTime":"2026-10-05T07:40:00+02:00","serviceJourney":{"transportSubmode":"railReplacementBus","line":{"publicCode":"R1","transportMode":"rail"}}}]},
                "stop1":null}}""",
            ),
        )

        val result = provider().departures(listOf("NSR:StopPlace:1", "NSR:StopPlace:9"), now)

        assertEquals(setOf("NSR:StopPlace:1"), result.keys)
        val (departure, replacement) = result.getValue("NSR:StopPlace:1")
        assertEquals(BoardDeparture("31", "bus", "Town C", "B", departure.aimed, departure.aimed + 120_000, realtime = true, cancelled = false), departure)
        // The line still says rail; the journey's submode marks the bus. / 线路仍写着 rail；靠这趟车的子类型认出巴士。
        assertTrue(replacement.replacementBus)
        val variables = AppJson.standard.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject.getValue("variables").jsonObject
        assertEquals("NSR:StopPlace:9", variables.getValue("stop1").jsonPrimitive.content)
        assertEquals(emptyMap<String, List<BoardDeparture>>(), provider().departures(emptyList(), now))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `stop search keeps stop places only and needs two characters`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"features":[
                {"properties":{"id":"NSR:GroupOfStopPlaces:1","names":{"default":"Town C"},"layer":"groupOfStopPlaces"}},
                {"properties":{"id":"NSR:StopPlace:7","names":{"default":"Town C stasjon"},"address":{"locality":"Town C"}}}]}""",
            ),
        )
        assertEquals(emptyList<TransitStop>(), provider().searchStops("x"))
        assertEquals(listOf(TransitStop("NSR:StopPlace:7", "Town C stasjon", "Town C")), provider().searchStops(" Town C "))
        val url = server.takeRequest().requestUrl!!
        assertEquals("Town C", url.queryParameter("q"))
        assertEquals("stopPlace", url.queryParameter("layers"))
    }

    // Sources / 数据来源

    private class FakeProvider : TransitProvider {
        val trips = mutableListOf<Pair<String, String>>()
        override suspend fun searchStops(query: String) = emptyList<TransitStop>()
        val kinds = mutableListOf<CommuteKind>()
        override suspend fun planTrips(from: TransitStop, to: TransitStop, at: ZonedDateTime, count: Int, kind: CommuteKind): List<TripOption> {
            trips += from.id to to.id
            kinds += kind
            return emptyList()
        }
        override suspend fun departures(stopIds: List<String>, at: ZonedDateTime) = mapOf(
            "NSR:StopPlace:1" to listOf(
                BoardDeparture("31", frontText = "Town C", aimed = at.toInstant().toEpochMilli() + 60_000, expected = at.toInstant().toEpochMilli() + 60_000),
                BoardDeparture("37", frontText = "Town C", aimed = at.toInstant().toEpochMilli() + 60_000, expected = at.toInstant().toEpochMilli() + 60_000),
            ),
        )
    }

    @Test
    fun `commute inputs need both stops and key on mode, stops and count`() {
        assertTrue(CommuteSource.inputFor(a, TransitStop(), 3, CommuteMode.OUTBOUND) is SourceInput.Missing)
        val ready = CommuteSource.inputFor(a, b, 9, CommuteMode.OUTBOUND) as SourceInput.Ready<CommuteParams>
        assertEquals(5, ready.params.options)
        assertEquals("OUTBOUND|NSR:StopPlace:1|NSR:StopPlace:2|5", ready.key)
    }

    @Test
    fun `only the current direction is fetched`() = runTest {
        val provider = FakeProvider()
        val clock = FixedClock(now)
        val store = InMemorySnapshotStore<CommuteTrips>()
        val mode = kotlinx.coroutines.flow.MutableStateFlow(CommuteMode.OUTBOUND)
        val source = CommuteSource(SourceId("transit.bus"), store, clock, CommuteKind.BUS, flowOf(CommuteRoute(a, b, 3)), mode, provider)
        val coordinator = RefreshCoordinator(FakeNetworkStatus(), clock, backgroundScope, watchInputs = false)
        coordinator.register(listOf(source))

        coordinator.refresh(setOf(source.id), Trigger.USER)
        assertEquals(listOf(a.id to b.id), provider.trips)
        assertEquals("the route's kind travels with every request", setOf(CommuteKind.BUS), provider.kinds.toSet())

        mode.value = CommuteMode.INBOUND
        provider.trips.clear()
        coordinator.refresh(setOf(source.id), Trigger.USER)
        assertEquals(listOf(b.id to a.id), provider.trips)
        assertEquals(CommuteMode.INBOUND, store.current!!.value.mode)
    }

    @Test
    fun `boards are filtered at fetch time and the filters are part of the key`() = runTest {
        val board = FavouriteBoard("board1", a, lines = listOf("31"))
        val other = (BoardsSource.inputFor(listOf(board.copy(lines = listOf("37")))) as SourceInput.Ready).key
        assertTrue(BoardsSource.inputFor(emptyList()) is SourceInput.Missing)
        assertFalse((BoardsSource.inputFor(listOf(board)) as SourceInput.Ready).key == other)

        val clock = FixedClock(now)
        val store = InMemorySnapshotStore<Boards>()
        val source = BoardsSource(SourceId("transit.boards"), store, clock, flowOf(listOf(board)), FakeProvider())
        val coordinator = RefreshCoordinator(FakeNetworkStatus(), clock, backgroundScope, watchInputs = false)
        coordinator.register(listOf(source))
        coordinator.refresh(setOf(source.id), Trigger.USER)

        assertEquals(listOf("31"), store.current!!.value.boards.single().departures.map { it.line })
    }
}
