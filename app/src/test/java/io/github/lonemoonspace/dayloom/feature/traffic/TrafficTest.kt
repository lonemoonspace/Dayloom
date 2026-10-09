package io.github.lonemoonspace.dayloom.feature.traffic

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.json.AppJson
import io.github.lonemoonspace.dayloom.core.location.Place
import io.github.lonemoonspace.dayloom.core.network.FakeNetworkStatus
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCoordinator
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.refresh.SourceInput
import io.github.lonemoonspace.dayloom.core.refresh.SourceResult
import io.github.lonemoonspace.dayloom.core.refresh.Trigger
import io.github.lonemoonspace.dayloom.core.routine.DailyWindow
import io.github.lonemoonspace.dayloom.core.routine.Routine
import io.github.lonemoonspace.dayloom.core.secret.SecretState
import io.github.lonemoonspace.dayloom.core.storage.InMemorySnapshotStore
import io.github.lonemoonspace.dayloom.core.time.FixedClock
import io.github.lonemoonspace.dayloom.feature.traffic.data.GoogleRoutesApi
import io.github.lonemoonspace.dayloom.feature.traffic.data.TrafficParams
import io.github.lonemoonspace.dayloom.feature.traffic.data.TrafficSource
import io.github.lonemoonspace.dayloom.feature.traffic.domain.Direction
import io.github.lonemoonspace.dayloom.feature.traffic.domain.TrafficLevel
import io.github.lonemoonspace.dayloom.feature.traffic.domain.TrafficPolicy
import io.github.lonemoonspace.dayloom.feature.traffic.domain.TrafficStatus
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrafficTest {

    private val zone = ZoneId.of("Europe/Oslo")

    /** 2026-10-05 is a Monday. / 2026-10-05 是周一。 */
    private fun t(day: Int, h: Int, mi: Int = 0) = ZonedDateTime.of(2026, 10, day, h, mi, 0, 0, zone)

    private val home = Place(id = Place.HOME, name = "Town A", lat = 59.1, lon = 10.1)
    private val work = Place(id = Place.WORK, name = "Town B", lat = 59.2, lon = 10.2)
    private val key = SecretState(display = "test-key", isSet = true, unreadable = false)

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

    private fun api() = GoogleRoutesApi(OkHttpClient(), server.url("/directions/v2:computeRoutes"))

    private fun route(duration: String = "1500s", static: String = "1200s") =
        MockResponse().setBody("""{"routes":[{"duration":"$duration","staticDuration":"$static","distanceMeters":23400}]}""")

    // Direction / 方向

    private val routine = Routine(toWork = DailyWindow(7 * 60, 9 * 60), backHome = DailyWindow(15 * 60, 17 * 60))

    @Test
    fun `direction follows the window under way, else the next one`() {
        assertEquals(Direction.TO_WORK, TrafficPolicy.direction(routine, t(5, 6)))
        assertEquals(Direction.TO_WORK, TrafficPolicy.direction(routine, t(5, 8)))
        // After the morning window the next one is the way home. / 早上的时间窗过后，下一个是回家。
        assertEquals(Direction.BACK_HOME, TrafficPolicy.direction(routine, t(5, 12)))
        assertEquals(Direction.BACK_HOME, TrafficPolicy.direction(routine, t(5, 16)))
        assertEquals(Direction.TO_WORK, TrafficPolicy.direction(routine, t(5, 18)))
        // Weekends too. / 周末也一样。
        assertEquals(Direction.BACK_HOME, TrafficPolicy.direction(routine, t(10, 16)))
    }

    // Parsing and levels / 解析与等级

    @Test
    fun `durations are protobuf seconds, fractions allowed, anything else unreadable`() {
        assertEquals(1500L, TrafficPolicy.parseSeconds("1500s"))
        assertEquals(10L, TrafficPolicy.parseSeconds("10.5s"))
        assertNull(TrafficPolicy.parseSeconds(""))
        assertNull(TrafficPolicy.parseSeconds("1500"))
        assertNull(TrafficPolicy.parseSeconds("-3s"))
    }

    @Test
    fun `levels by delay, unknown without a free-flow time`() {
        assertEquals(0L to TrafficLevel.CLEAR, TrafficPolicy.level(1000, 1100))
        assertEquals(120L to TrafficLevel.CLEAR, TrafficPolicy.level(1320, 1200))
        assertEquals(300L to TrafficLevel.SLIGHT, TrafficPolicy.level(1500, 1200))
        assertEquals(900L to TrafficLevel.MODERATE, TrafficPolicy.level(2100, 1200))
        assertEquals(1200L to TrafficLevel.SEVERE, TrafficPolicy.level(2400, 1200))
        assertEquals(0L to TrafficLevel.UNKNOWN, TrafficPolicy.level(2400, null))
    }

    @Test
    fun `an unknown level name falls back to UNKNOWN instead of breaking the snapshot`() {
        val decoded = AppJson.standard.decodeFromString(TrafficStatus.serializer(), """{"durationSec":60,"level":"GRIDLOCK"}""")
        assertEquals(TrafficLevel.UNKNOWN, decoded.level)
        assertTrue(AppJson.standard.encodeToString(TrafficStatus.serializer(), TrafficStatus(level = TrafficLevel.SLIGHT)).contains("\"SLIGHT\""))
    }

    // Inputs / 输入

    @Test
    fun `inputs need both places and a key, and swap ends for the way home`() {
        assertTrue(TrafficSource.inputFor(null, work, Direction.TO_WORK, key) is SourceInput.Missing)
        assertTrue(TrafficSource.inputFor(home, work, Direction.TO_WORK, SecretState.EMPTY) is SourceInput.Missing)
        val unreadable = SecretState(display = "", isSet = true, unreadable = true)
        assertTrue(TrafficSource.inputFor(home, work, Direction.TO_WORK, unreadable) is SourceInput.Missing)

        val out = TrafficSource.inputFor(home, work, Direction.TO_WORK, key) as SourceInput.Ready<TrafficParams>
        val back = TrafficSource.inputFor(home, work, Direction.BACK_HOME, key) as SourceInput.Ready<TrafficParams>
        assertEquals(TrafficParams(Direction.TO_WORK, 59.1 to 10.1, 59.2 to 10.2), out.params)
        assertEquals(TrafficParams(Direction.BACK_HOME, 59.2 to 10.2, 59.1 to 10.1), back.params)
        assertNotEquals(out.key, back.key)
    }

    @Test
    fun `a new key changes the refresh key but not the snapshot key, and the key itself is never in either`() {
        val a = TrafficSource.inputFor(home, work, Direction.TO_WORK, key) as SourceInput.Ready<TrafficParams>
        val b = TrafficSource.inputFor(home, work, Direction.TO_WORK, key.copy(display = "other-key")) as SourceInput.Ready<TrafficParams>
        assertEquals(a.key, b.key)
        assertNotEquals(a.refreshKey, b.refreshKey)
        assertTrue(!a.key.contains("test-key") && !a.refreshKey.toString().contains("test-key"))
    }

    // Fetching through the coordinator / 经协调器抓取

    private suspend fun refresh(apiKey: String, scope: kotlinx.coroutines.CoroutineScope): Pair<SourceResult?, InMemorySnapshotStore<TrafficStatus>> {
        val clock = FixedClock(t(5, 7, 30))
        val store = InMemorySnapshotStore<TrafficStatus>()
        val source = TrafficSource(
            id = SourceId("traffic.route"),
            store = store,
            clock = clock,
            from = flowOf(home),
            to = flowOf(work),
            direction = MutableStateFlow(Direction.TO_WORK),
            secret = flowOf(key),
            apiKey = { apiKey },
            api = api(),
        )
        val coordinator = RefreshCoordinator(FakeNetworkStatus(), clock, scope, watchInputs = false)
        coordinator.register(listOf(source))
        return coordinator.refresh(setOf(source.id), Trigger.USER).results[source.id] to store
    }

    @Test
    fun `the request carries coordinates, the key in a header and a future departure`() = runTest {
        server.enqueue(route())
        val (result, store) = refresh("test-key", backgroundScope)

        assertTrue(result is SourceResult.Success)
        val request = server.takeRequest()
        assertEquals("test-key", request.getHeader("X-Goog-Api-Key"))
        assertNull(request.requestUrl!!.queryParameter("key"))
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"latitude\":59.1") && body.contains("\"latitude\":59.2"))
        assertTrue(body, body.contains("\"departureTime\":\"2026-10-05T07:31:00+02:00\""))
        assertEquals(TrafficStatus(1500, 1200, 300, 23400, TrafficLevel.SLIGHT, Direction.TO_WORK), store.current!!.value)
    }

    @Test
    fun `an unusable key never reaches Google`() = runTest {
        val (result, _) = refresh("", backgroundScope)
        assertTrue(result is SourceResult.Skipped)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a missing duration fails instead of reporting clear traffic, a missing free-flow time degrades`() = runTest {
        server.enqueue(route(duration = ""))
        val (failed, store) = refresh("test-key", backgroundScope)
        assertTrue((failed as SourceResult.Failed).error is AppError.BadData)
        assertNull(store.current)

        server.enqueue(route(static = ""))
        val (_, degraded) = refresh("test-key", backgroundScope)
        assertEquals(TrafficLevel.UNKNOWN, degraded.current!!.value.level)
    }

    @Test
    fun `google errors keep a short excerpt for the user`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("Routes API has not been used in project " + "x".repeat(400)))
        val (result, _) = refresh("test-key", backgroundScope)
        val error = (result as SourceResult.Failed).error as AppError.Http
        assertEquals(403, error.code)
        assertEquals(300, error.detail.length)

        server.enqueue(MockResponse().setBody("""{"routes":[]}"""))
        val (empty, _) = refresh("test-key", backgroundScope)
        assertTrue((empty as SourceResult.Failed).error is AppError.BadData)
    }
}
