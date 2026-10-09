package io.github.lonemoonspace.dayloom.feature.weather

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.feature.weather.data.MetApi
import io.github.lonemoonspace.dayloom.feature.weather.data.WeatherSource
import io.github.lonemoonspace.dayloom.feature.weather.domain.Forecast
import io.github.lonemoonspace.dayloom.feature.weather.domain.ForecastPoint
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MetApiTest {

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

    private fun api() = MetApi(OkHttpClient(), baseUrl = server.url("/weatherapi/locationforecast/2.0/compact"))

    private val body = """
        {"type":"Feature","properties":{"meta":{"updated_at":"2026-09-22T05:30:00Z","units":{}},"timeseries":[
          {"time":"2026-09-22T06:00:00Z","data":{"instant":{"details":{"air_temperature":7.5,"wind_speed":3.2}},
            "next_1_hours":{"summary":{"symbol_code":"lightrain_day"},"details":{"precipitation_amount":0.4}},
            "next_6_hours":{"summary":{"symbol_code":"rain_day"},"details":{"precipitation_amount":2.1}}}},
          {"time":"2026-09-25T00:00:00Z","data":{"instant":{"details":{"air_temperature":4.0,"wind_speed":1.0}},
            "next_6_hours":{"summary":{"symbol_code":"cloudy"},"details":{"precipitation_amount":0.0}}}}
        ]}}
    """.trimIndent()

    @Test
    fun `sends four-decimal coordinates rounded half-up and no conditional header the first time`() {
        server.enqueue(MockResponse().setBody(body))

        api().fetch(59.91235, 0.0001, ifModifiedSince = null)

        val request = server.takeRequest()
        assertEquals("/weatherapi/locationforecast/2.0/compact?lat=59.9124&lon=0.0001", request.path)
        assertNull(request.getHeader("If-Modified-Since"))
    }

    @Test
    fun `parses hourly and six-hourly steps and keeps Last-Modified`() {
        server.enqueue(MockResponse().setBody(body).setHeader("Last-Modified", "Tue, 22 Sep 2026 05:31:00 GMT"))

        val forecast = (api().fetch(59.9, 10.7, ifModifiedSince = null) as MetApi.Result.Fresh).forecast

        assertEquals(Instant.parse("2026-09-22T05:30:00Z").toEpochMilli(), forecast.updatedAt)
        assertEquals("Tue, 22 Sep 2026 05:31:00 GMT", forecast.lastModified)
        val (first, later) = forecast.points
        assertEquals(7.5, first.temperature!!, 0.0)
        assertEquals("lightrain_day", first.symbol)
        assertEquals(0.4, first.precipitation1h!!, 0.0)
        assertEquals(2.1, first.precipitation6h!!, 0.0)
        // Beyond the hourly range the six-hour symbol stands in. / 超出逐小时范围时由 6 小时符号代替。
        assertEquals("cloudy", later.symbol)
        assertNull(later.precipitation1h)
    }

    @Test
    fun `304 means not modified and the conditional header is sent`() {
        server.enqueue(MockResponse().setResponseCode(304))

        val result = api().fetch(59.9, 10.7, ifModifiedSince = "Tue, 22 Sep 2026 05:31:00 GMT")

        assertEquals(MetApi.Result.NotModified, result)
        assertEquals("Tue, 22 Sep 2026 05:31:00 GMT", server.takeRequest().getHeader("If-Modified-Since"))
    }

    @Test
    fun `http errors, unreadable bodies and empty series become AppErrors`() {
        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(429, assertThrows(AppError.Http::class.java) { api().fetch(1.0, 1.0, null) }.code)

        server.enqueue(MockResponse().setBody("not json"))
        assertThrows(AppError.BadData::class.java) { api().fetch(1.0, 1.0, null) }

        server.enqueue(MockResponse().setBody("""{"properties":{"timeseries":[]}}"""))
        assertThrows(AppError.BadData::class.java) { api().fetch(1.0, 1.0, null) }
    }

    @Test
    fun `validation records the current point and rejects a missing reading instead of showing zero`() {
        val now = ZonedDateTime.of(2026, 9, 22, 8, 10, 0, 0, ZoneId.of("Europe/Oslo"))
        val at = now.withMinute(0).toInstant().toEpochMilli()
        val ok = Forecast(points = listOf(ForecastPoint(time = at, temperature = 5.0, windSpeed = 2.0, symbol1h = "fair_day")))

        assertEquals(at, WeatherSource.validated(ok, now).observedAt)
        val noTemp = ok.copy(points = listOf(ok.points.single().copy(temperature = null)))
        val error = assertThrows(AppError.BadData::class.java) { WeatherSource.validated(noTemp, now) }
        assertTrue(error.message!!.contains("air_temperature"))
    }
}
