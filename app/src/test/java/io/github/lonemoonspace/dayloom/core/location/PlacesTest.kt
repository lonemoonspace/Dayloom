package io.github.lonemoonspace.dayloom.core.location

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.storage.InMemoryValueStore
import io.github.lonemoonspace.dayloom.core.storage.SharedData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {

    private val home = Place(id = Place.HOME, name = "Town A", lat = 1.0, lon = 2.0)
    private val work = Place(id = Place.WORK, name = "Town B")
    private val gym = Place(id = "place1", label = "gym", name = "Town C")
    private val cabin = Place(id = "place2", label = "Cabin", name = "Town D")

    @Test
    fun `presets come first, then custom places by label`() {
        assertEquals(listOf(home, work, cabin, gym), PlacesPolicy.ordered(listOf(gym, cabin, work, home)))
    }

    @Test
    fun `upsert replaces by id and custom ids skip used numbers`() {
        val moved = home.copy(name = "Town E")
        assertEquals(listOf(moved, gym), PlacesPolicy.upsert(listOf(home, gym), moved))
        assertEquals(listOf(home, gym, work), PlacesPolicy.upsert(listOf(home, gym), work))
        assertEquals("place3", PlacesPolicy.newCustomId(listOf(gym, cabin)))
        assertEquals("place1", PlacesPolicy.newCustomId(listOf(cabin)))
    }

    @Test
    fun `coordinates round half-up to four decimals`() {
        assertEquals(59.9124, PlacesPolicy.roundCoordinate(59.91235), 0.0)
        assertEquals(-10.7524, PlacesPolicy.roundCoordinate(-10.75235), 0.0)
        assertEquals("0.0001", PlacesPolicy.formatCoordinate(0.0001))
    }

    @Test
    fun `the place book stores, renames and removes places`() = runTest {
        val book = PlaceBook(InMemoryValueStore(SharedData()))
        book.put(home)
        val id = book.addCustom(PlaceCandidate("Town C", "", 3.0, 4.0, "NO"), label = "gym")

        assertEquals("place1", id)
        assertEquals(listOf(Place.HOME, "place1"), book.all.first().map { it.id })
        assertEquals("gym", book.observe(id).first()?.label)

        book.remove(Place.HOME)
        assertEquals(listOf("place1"), book.all.first().map { it.id })
    }

    @Test
    fun `open-meteo results become candidates with region and upper-case country`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{"results":[{"name":"Sample","latitude":59.91,"longitude":10.75,"country_code":"no",
                "country":"Norway","admin1":"Sample","admin2":"Region"}]}""",
            ),
        )
        server.start()
        try {
            val geocoder = OpenMeteoGeocoder(OkHttpClient(), server.url("/v1/search"))

            val result = geocoder.search(" Sample ", "zh").single()

            assertEquals(PlaceCandidate("Sample", "Region, Norway", 59.91, 10.75, "NO"), result)
            val path = server.takeRequest().path!!
            assertTrue(path, path.contains("name=Sample") && path.contains("language=zh"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `open-meteo needs two characters and reports http errors`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(500))
        server.start()
        try {
            val geocoder = OpenMeteoGeocoder(OkHttpClient(), server.url("/v1/search"))
            assertEquals(emptyList<PlaceCandidate>(), geocoder.search("x", "en"))
            assertEquals(0, server.requestCount)
            val error = runCatching { geocoder.search("Sample", "en") }.exceptionOrNull()
            assertEquals(500, (error as AppError.Http).code)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `the empty response has no results`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"generationtime_ms":0.1}"""))
        server.start()
        try {
            assertEquals(emptyList<PlaceCandidate>(), OpenMeteoGeocoder(OkHttpClient(), server.url("/v1/search")).search("Nowhere", "en"))
        } finally {
            server.shutdown()
        }
        assertThrows(IllegalArgumentException::class.java) { PlacesPolicy.upsert(emptyList(), Place()) }
    }

    @Test
    fun `typed coordinates are a place of their own, house numbers are not`() {
        val spot = PlacesPolicy.parseCoordinates(" 59.91235, 10.7522 ")!!
        assertEquals(59.9124, spot.lat, 0.0)
        assertEquals(10.7522, spot.lon, 0.0)
        assertEquals("59.9124, 10.7522", spot.name)
        assertEquals(-33.86, PlacesPolicy.parseCoordinates("-33.86；151.21")?.lat ?: 0.0, 0.0)
        assertEquals(1.5, PlacesPolicy.parseCoordinates("1.5 -2.5")?.lat ?: 0.0, 0.0)
        assertNull("no decimals: an address", PlacesPolicy.parseCoordinates("Storgata 12, 0155"))
        assertNull("out of range", PlacesPolicy.parseCoordinates("95.0, 10.0"))
        assertNull(PlacesPolicy.parseCoordinates("Oslo"))
    }

    @Test
    fun `google text search sends the key in a header and maps name, address and country`() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(
                MockResponse().setBody(
                    """{"places":[{"formattedAddress":"Street 1, 0001 Town A, Norway","location":{"latitude":59.1,"longitude":10.2},
                    "displayName":{"text":"Station A","languageCode":"en"},
                    "addressComponents":[{"longText":"Norway","shortText":"NO","types":["country","political"]}]},
                    {"displayName":{"text":"No location"}}]}""",
                ),
            )
            val search = GooglePlacesSearch(OkHttpClient(), endpoint = server.url("/v1/places:searchText"))
            val results = search.search("station a", "zh", "test-key")

            assertEquals(listOf(PlaceCandidate("Station A", "Street 1, 0001 Town A, Norway", 59.1, 10.2, "NO")), results)
            val request = server.takeRequest()
            assertEquals("test-key", request.getHeader("X-Goog-Api-Key"))
            assertTrue(request.getHeader("X-Goog-FieldMask")!!.contains("places.location"))
            assertTrue("key never in the URL", "test-key" !in request.path.orEmpty())
            assertTrue(request.body.readUtf8().contains("\"languageCode\":\"zh\""))

            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"message":"Places API (New) has not been used"}}"""))
            val error = try {
                search.search("station a", "en", "test-key")
                null
            } catch (e: AppError.Http) {
                e
            }
            assertTrue(error!!.detail.contains("Places API"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `the finder takes coordinates as typed, Google with a key, Open-Meteo without`() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"places":[{"displayName":{"text":"google"},"location":{"latitude":1.0,"longitude":1.0}}]}"""))
            val google = GooglePlacesSearch(OkHttpClient(), endpoint = server.url("/v1/places:searchText"))
            val meteo = object : PlaceSearch {
                override suspend fun search(query: String, language: String) = listOf(PlaceCandidate("meteo", "", 1.0, 1.0, ""))
            }
            var key = ""
            val finder = PlaceFinder(google, meteo) { key }
            assertEquals("meteo", finder.search("Town", "en").single().name)
            key = "k"
            assertEquals("google", finder.search("Town", "en").single().name)
            assertEquals("1.5000, 2.5000", finder.search("1.5, 2.5", "en").single().name)
            assertEquals("coordinates need no request", 1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
