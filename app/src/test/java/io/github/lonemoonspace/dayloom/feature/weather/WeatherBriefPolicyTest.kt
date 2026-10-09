package io.github.lonemoonspace.dayloom.feature.weather

import io.github.lonemoonspace.dayloom.feature.weather.domain.ClothingLevel
import io.github.lonemoonspace.dayloom.feature.weather.domain.Forecast
import io.github.lonemoonspace.dayloom.feature.weather.domain.ForecastPoint
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherBrief
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherBriefPolicy
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherCondition
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherBriefPolicyTest {

    private val zone = ZoneId.of("Europe/Oslo")

    /** Monday 2026-10-05, 07:10. / 2026-10-05 周一 07:10。 */
    private val now = ZonedDateTime.of(2026, 10, 5, 7, 10, 0, 0, zone)

    private fun forecast(rainAt: Int? = null) = Forecast(
        points = (0 until 24).map { h ->
            val at = now.withHour(0).withMinute(0).plusHours(h.toLong())
            val wet = h == rainAt
            ForecastPoint(
                time = at.toInstant().toEpochMilli(),
                temperature = 7.6,
                symbol1h = if (wet) "rain" else "partlycloudy_day",
                precipitation1h = if (wet) 1.5 else 0.0,
            )
        },
    )

    @Test
    fun `current reading, rounded, with what to wear`() {
        assertEquals(
            WeatherBrief(WeatherCondition.PARTLY_CLOUDY, 8, umbrella = false, clothing = ClothingLevel.COOL),
            WeatherBriefPolicy.brief(forecast(), now),
        )
    }

    @Test
    fun `rain later in the day already asks for an umbrella in the morning`() {
        assertEquals(true, WeatherBriefPolicy.brief(forecast(rainAt = 17), now)?.umbrella)
        assertEquals("rain that already fell does not", false, WeatherBriefPolicy.brief(forecast(rainAt = 5), now)?.umbrella)
        assertEquals("rain after the day ends does not", false, WeatherBriefPolicy.brief(forecast(rainAt = 23), now)?.umbrella)
    }

    @Test
    fun `no reading, no line`() {
        assertNull(WeatherBriefPolicy.brief(Forecast(), now))
    }
}
