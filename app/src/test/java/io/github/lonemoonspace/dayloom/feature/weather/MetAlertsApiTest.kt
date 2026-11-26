package io.github.lonemoonspace.dayloom.feature.weather

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.feature.weather.data.MetAlertsApi
import io.github.lonemoonspace.dayloom.feature.weather.domain.WarningEvent
import io.github.lonemoonspace.dayloom.feature.weather.domain.WarningLevel
import java.time.Instant
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class MetAlertsApiTest {

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

    private fun api() = MetAlertsApi(OkHttpClient(), baseUrl = server.url("/weatherapi/metalerts/2.0/current.json"))

    private fun feature(id: String, event: String, level: String, domain: String, status: String = "Actual", start: String = "2026-10-21T04:00:00+00:00") = """
        {"type":"Feature","geometry":{"type":"Polygon","coordinates":[[[10.0,59.0],[11.0,59.0],[11.0,60.0],[10.0,59.0]]]},
         "properties":{"id":"$id","event":"$event","status":"$status","awareness_level":"$level","awareness_type":"1; Wind",
           "geographicDomain":"$domain","county":["03"],"description":" Strong gusts. ","instruction":"Secure loose objects.",
           "riskMatrixColor":"Orange","title":"Wind"},
         "when":{"interval":["$start","2026-10-21T16:00:00+00:00"]}}
    """.trimIndent()

    @Test
    fun `keeps land warnings only and reads level, event and times`() {
        val body = """{"type":"FeatureCollection","lang":"en","lastChange":"2026-10-20T06:00:00+00:00","features":[
            ${feature("land", "wind", "3; orange; Severe", "land")},
            ${feature("sea", "gale", "2; yellow; Moderate", "marine")},
            ${feature("drill", "rain", "2; yellow; Moderate", "land", status = "Test")},
            ${feature("odd", "snow", "2; yellow; Moderate", "land", start = "soon")}
        ]}"""
        server.enqueue(MockResponse().setBody(body))

        val warnings = api().fetch(59.91235, 10.75).warnings

        val request = server.takeRequest()
        assertEquals("59.9124", request.requestUrl!!.queryParameter("lat"))
        assertEquals("en", request.requestUrl!!.queryParameter("lang"))
        assertEquals(listOf("land"), warnings.map { it.id })
        val warning = warnings.single()
        assertEquals(WarningLevel.ORANGE, warning.severity)
        assertEquals(WarningEvent.WIND, warning.kind)
        assertEquals(Instant.parse("2026-10-21T04:00:00Z").toEpochMilli(), warning.start)
        assertEquals(Instant.parse("2026-10-21T16:00:00Z").toEpochMilli(), warning.end)
        assertEquals("Strong gusts.", warning.description)
    }

    @Test
    fun `an empty answer is no warnings`() {
        server.enqueue(MockResponse().setBody("""{"type":"FeatureCollection","features":[],"lang":"en"}"""))
        assertEquals(emptyList<Any>(), api().fetch(59.0, 10.0).warnings)
    }

    @Test
    fun `server errors and broken bodies are errors, not an empty list`() {
        server.enqueue(MockResponse().setResponseCode(503))
        assertThrows(AppError.Http::class.java) { api().fetch(59.0, 10.0) }
        server.enqueue(MockResponse().setBody("""{"features":[{"properties":"""))
        assertThrows(AppError.BadData::class.java) { api().fetch(59.0, 10.0) }
    }

    @Test
    fun `unknown events still show as a weather warning`() {
        assertEquals(WarningEvent.OTHER, WarningEvent.fromMet("somethingNew"))
        assertEquals(WarningEvent.RAIN_FLOOD, WarningEvent.fromMet("rainFlood"))
    }
}
