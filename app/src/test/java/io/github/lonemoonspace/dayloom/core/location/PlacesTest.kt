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
import org.junit.Assert.assertFalse
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
    fun `entur results become candidates with locality, county and upper-case country`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{"type":"FeatureCollection","features":[
                {"geometry":{"type":"Point","coordinates":[10.75,59.91]},"properties":{"names":{"default":"Street 1","display":"Street 1, Town A"},
                 "layer":"address","address":{"locality":"Town A","county":"County B","countryCode":"no"}}},
                {"geometry":{"type":"Point","coordinates":[]},"properties":{"names":{"default":"No position"}}}]}""",
            ),
        )
        server.start()
        try {
            val result = EnturGeocoder(OkHttpClient(), server.url("/geocoder/v3/autocomplete")).search(" Street 1 ", "zh").single()

            assertEquals(PlaceCandidate("Street 1", "Town A, County B", 59.91, 10.75, "NO"), result)
            val request = server.takeRequest()
            assertTrue(request.path!!.contains("q=Street%201"))
            assertEquals(EnturGeocoder.CLIENT_NAME, request.getHeader(EnturGeocoder.CLIENT_HEADER))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `entur needs two characters and reports http errors`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(500))
        server.start()
        try {
            val geocoder = EnturGeocoder(OkHttpClient(), server.url("/geocoder/v3/autocomplete"))
            assertEquals(emptyList<PlaceCandidate>(), geocoder.search("x", "en"))
            assertEquals(0, server.requestCount)
            val error = runCatching { geocoder.search("Sample", "en") }.exceptionOrNull()
            assertEquals(500, (error as AppError.Http).code)
        } finally {
            server.shutdown()
        }
        assertThrows(IllegalArgumentException::class.java) { PlacesPolicy.upsert(emptyList(), Place()) }
    }

    @Test
    fun `nominatim names plain addresses by road and number and keeps named places`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """[{"name":"","display_name":"10, Street A, District, Town A, 2000, Country","lat":"59.95","lon":"11.04",
                  "address":{"road":"Street A","house_number":"10","country_code":"no"}},
                 {"name":"Hall B","display_name":"Hall B, Town C, Country","lat":"51.5","lon":"-0.12","address":{"country_code":"gb"}},
                 {"name":"Broken","lat":"x","lon":"1"}]""",
            ),
        )
        server.start()
        try {
            val results = NominatimSearch(OkHttpClient(), server.url("/search")).search("street a 10", "en")

            assertEquals(
                listOf(
                    PlaceCandidate("Street A 10", "District, Town A, 2000, Country", 59.95, 11.04, "NO"),
                    PlaceCandidate("Hall B", "Town C, Country", 51.5, -0.12, "GB"),
                ),
                results,
            )
            val path = server.takeRequest().path!!
            assertTrue(path, path.contains("format=jsonv2") && path.contains("accept-language=en"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `the fallback search is used when the first finds nothing or fails`() = runTest {
        fun fixed(name: String) = object : PlaceSearch {
            override suspend fun search(query: String, language: String) = listOf(PlaceCandidate(name, "", 1.0, 1.0, ""))
        }
        val empty = object : PlaceSearch { override suspend fun search(query: String, language: String) = emptyList<PlaceCandidate>() }
        val broken = object : PlaceSearch { override suspend fun search(query: String, language: String): List<PlaceCandidate> = throw AppError.Http("A", 503) }

        assertEquals("first", FallbackSearch(fixed("first"), fixed("second")).search("q", "en").single().name)
        assertEquals("second", FallbackSearch(empty, fixed("second")).search("q", "en").single().name)
        assertEquals("second", FallbackSearch(broken, fixed("second")).search("q", "en").single().name)
        // A Norwegian street for a Berlin address is no answer. / 柏林地址得到一条挪威街道不算结果。
        val fuzzy = object : PlaceSearch {
            override suspend fun search(query: String, language: String) = listOf(PlaceCandidate("Street Z 5", "Town Y", 1.0, 1.0, "NO"))
        }
        assertEquals("second", FallbackSearch(fuzzy, fixed("second")).search("Hauptstraße 5 Berlin", "en").single().name)
        // Both failing reports the first service's error. / 两个都失败时报告第一个服务的错误。
        val error = runCatching { FallbackSearch(broken, object : PlaceSearch {
            override suspend fun search(query: String, language: String): List<PlaceCandidate> = throw AppError.Http("B", 500)
        }).search("q", "en") }.exceptionOrNull()
        assertEquals("A", (error as AppError.Http).service)
    }

    @Test
    fun `a result is relevant when every word of the query is in it, accents aside`() {
        val town = PlaceCandidate("Storgata 10", "Lillestrøm, Akershus", 1.0, 1.0, "NO")
        assertTrue(PlacesPolicy.isRelevant("storgata 10", town))
        assertTrue("ø typed as o", PlacesPolicy.isRelevant("Lillestrom", town))
        assertTrue("house number only: trust the service", PlacesPolicy.isRelevant("10", town))
        assertFalse(PlacesPolicy.isRelevant("Hauptstraße 5 Berlin", town))
        assertFalse("another town's street of the same name", PlacesPolicy.isRelevant("Storgata 10 Bergen", town))
        assertTrue("ß as ss", PlacesPolicy.isRelevant("Hauptstrasse", PlaceCandidate("Hauptstraße 5", "Berlin", 1.0, 1.0, "DE")))
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
    fun `the finder takes coordinates as typed, Google with a key, the keyless search without`() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"places":[{"displayName":{"text":"google"},"location":{"latitude":1.0,"longitude":1.0}}]}"""))
            val google = GooglePlacesSearch(OkHttpClient(), endpoint = server.url("/v1/places:searchText"))
            val keyless = object : PlaceSearch {
                override suspend fun search(query: String, language: String) = listOf(PlaceCandidate("keyless", "", 1.0, 1.0, ""))
            }
            var key = ""
            val finder = PlaceFinder(google, keyless) { key }
            assertEquals("keyless", finder.search("Town", "en").single().name)
            key = "k"
            assertEquals("google", finder.search("Town", "en").single().name)
            assertEquals("1.5000, 2.5000", finder.search("1.5, 2.5", "en").single().name)
            assertEquals("coordinates need no request", 1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
