package io.github.lonemoonspace.dayloom.feature.weather

import io.github.lonemoonspace.dayloom.feature.weather.domain.ClothingLevel
import io.github.lonemoonspace.dayloom.feature.weather.domain.DayOutlookPolicy
import io.github.lonemoonspace.dayloom.feature.weather.domain.ForecastPoint
import io.github.lonemoonspace.dayloom.feature.weather.domain.OutlookDay
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherTip
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayOutlookPolicyTest {

    private val zone = ZoneId.of("Europe/Oslo")
    private val monday = LocalDate.of(2026, 10, 5)

    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone)

    /** Two days of hourly points from Monday 00:00; [shape] can change any hour. / 周一 00:00 起两天的逐小时数据；[shape] 可改任意一小时。 */
    private fun points(shape: (ZonedDateTime, ForecastPoint) -> ForecastPoint = { _, p -> p }) = (0 until 48).map { h ->
        val t = monday.atStartOfDay(zone).plusHours(h.toLong())
        shape(t, ForecastPoint(time = t.toInstant().toEpochMilli(), temperature = 10.0, windSpeed = 3.0, symbol1h = "cloudy", precipitation1h = 0.0))
    }

    @Test
    fun `daytime shows today, from the evening on tomorrow`() {
        val morning = DayOutlookPolicy.outlook(points(), at(5, 7, 30))!!
        assertEquals(OutlookDay.TODAY, morning.day)
        assertEquals(monday, morning.date)
        val evening = DayOutlookPolicy.outlook(points(), at(5, 18))!!
        assertEquals(OutlookDay.TOMORROW, evening.day)
        assertEquals(monday.plusDays(1), evening.date)
    }

    @Test
    fun `today covers only the hours left, from the current hour`() {
        val temps = points { t, p -> p.copy(temperature = t.hour.toDouble()) }
        val outlook = DayOutlookPolicy.outlook(temps, at(5, 14, 20))!!
        assertEquals(14.0, outlook.minTemp, 0.0)
        assertEquals("the day ends at 22:00", 21.0, outlook.maxTemp, 0.0)
    }

    @Test
    fun `rain hours merge into spells and the symbol follows the wettest hour`() {
        val wet = points { t, p ->
            if (t.dayOfMonth == 5 && t.hour in setOf(9, 10, 15)) p.copy(precipitation1h = if (t.hour == 10) 1.5 else 0.5, symbol1h = "rain") else p
        }
        val outlook = DayOutlookPolicy.outlook(wet, at(5, 7))!!
        assertEquals(listOf(at(5, 9) to at(5, 11), at(5, 15) to at(5, 16)), outlook.rainSpells.map { it.start to it.end })
        assertEquals(2.5, outlook.precipMm, 1e-9)
        assertEquals("rain", outlook.symbolCode)
        assertTrue(WeatherTip.UMBRELLA in outlook.tips)
    }

    @Test
    fun `drizzle below the umbrella threshold is dry`() {
        val drizzle = points { _, p -> p.copy(precipitation1h = 0.1) }
        val outlook = DayOutlookPolicy.outlook(drizzle, at(5, 7))!!
        assertTrue(outlook.rainSpells.isEmpty())
        assertFalse(WeatherTip.UMBRELLA in outlook.tips)
    }

    @Test
    fun `clothing follows the coldest feels-like temperature`() {
        val chilly = points { t, p -> p.copy(apparentTemperature = if (t.hour == 7) -2.0 else 12.0) }
        assertEquals(ClothingLevel.FREEZING, DayOutlookPolicy.outlook(chilly, at(5, 6))!!.clothing)
        assertEquals("without feels-like the air temperature counts", ClothingLevel.COOL, DayOutlookPolicy.outlook(points(), at(5, 6))!!.clothing)
        assertEquals(ClothingLevel.HOT, DayOutlookPolicy.clothing(26.0))
        assertEquals(ClothingLevel.WARM, DayOutlookPolicy.clothing(25.9))
        assertEquals(ClothingLevel.COLD, DayOutlookPolicy.clothing(0.0))
    }

    @Test
    fun `tips for snow, ice, wind, sun and a wide range`() {
        val winter = points { t, p -> p.copy(temperature = 0.0, symbol1h = if (t.hour == 9) "snow" else "cloudy", precipitation1h = if (t.hour == 9) 0.5 else 0.0) }
        val tips = DayOutlookPolicy.outlook(winter, at(5, 7))!!.tips
        assertTrue(WeatherTip.SNOW in tips)
        assertTrue(WeatherTip.SLIPPERY in tips)
        assertFalse("snow is not an umbrella day", WeatherTip.UMBRELLA in tips)

        val windy = points { _, p -> p.copy(windGust = 16.0) }
        assertTrue(WeatherTip.STRONG_WIND in DayOutlookPolicy.outlook(windy, at(5, 7))!!.tips)

        val sunny = points { t, p -> p.copy(symbol1h = "clearsky_day", uvIndex = 4.0, temperature = if (t.hour < 10) 8.0 else 18.0) }
        val sunnyTips = DayOutlookPolicy.outlook(sunny, at(5, 7))!!.tips
        assertTrue(WeatherTip.SUNSCREEN in sunnyTips)
        assertTrue(WeatherTip.LAYERS in sunnyTips)

        val cloudyUv = points { _, p -> p.copy(uvIndex = 4.0) }
        assertFalse("clear-sky UV under clouds", WeatherTip.SUNSCREEN in DayOutlookPolicy.outlook(cloudyUv, at(5, 7))!!.tips)
    }

    @Test
    fun `a mild dry day is pleasant`() {
        val mild = points { _, p -> p.copy(temperature = 18.0) }
        assertEquals(listOf(WeatherTip.PLEASANT), DayOutlookPolicy.outlook(mild, at(5, 7))!!.tips)
    }

    @Test
    fun `timeline every three hours from six`() {
        val outlook = DayOutlookPolicy.outlook(points(), at(5, 6))!!
        assertEquals(listOf(6, 9, 12, 15, 18, 21), outlook.timeline.map { it.time.hour })
    }

    @Test
    fun `no hourly data, no outlook`() {
        val sixHourly = points().map { it.copy(symbol1h = "", precipitation1h = null) }
        assertNull(DayOutlookPolicy.outlook(sixHourly, at(5, 7)))
    }
}
