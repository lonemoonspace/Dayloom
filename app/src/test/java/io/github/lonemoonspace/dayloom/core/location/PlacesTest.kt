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
}
