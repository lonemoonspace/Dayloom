package io.github.lonemoonspace.dayloom.feature.weather

import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherCondition
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherIcon
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherSymbolPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WeatherSymbolPolicyTest {

    /** Every base symbol code MET documents, including the historical "lights…" spellings. / MET 公布的全部基础符号名，含历史拼写「lights…」。 */
    private val metCodes = listOf(
        "clearsky", "fair", "partlycloudy", "cloudy", "fog", "wind",
        "lightrain", "rain", "heavyrain", "lightrainshowers", "rainshowers", "heavyrainshowers",
        "lightrainshowersandthunder", "rainshowersandthunder", "heavyrainshowersandthunder",
        "lightsleetshowers", "sleetshowers", "heavysleetshowers",
        "lightssleetshowersandthunder", "sleetshowersandthunder", "heavysleetshowersandthunder",
        "lightsnowshowers", "snowshowers", "heavysnowshowers",
        "lightssnowshowersandthunder", "snowshowersandthunder", "heavysnowshowersandthunder",
        "lightrainandthunder", "rainandthunder", "heavyrainandthunder",
        "lightsleet", "sleet", "heavysleet", "lightsleetandthunder", "sleetandthunder", "heavysleetandthunder",
        "lightsnow", "snow", "heavysnow", "lightsnowandthunder", "snowandthunder", "heavysnowandthunder",
    )

    @Test
    fun `every MET code has a condition`() {
        metCodes.forEach { assertNotEquals("unmapped MET code: $it", WeatherCondition.UNKNOWN, WeatherSymbolPolicy.condition("${it}_day")) }
    }

    @Test
    fun `every known code gets a specific icon, not the fallback`() {
        // The fallback is the cloudy icon, so no known code except cloudy may get it. / 兜底是阴天图标，除 cloudy 外的已知符号都不应得到它。
        metCodes.filter { it != "cloudy" }.forEach {
            assertNotEquals("fallback icon for $it", WeatherIcon.CLOUDY, WeatherSymbolPolicy.icon("${it}_day"))
        }
    }

    @Test
    fun `thunder wins over precipitation, snow and sleet win over rain`() {
        assertEquals(WeatherIcon.THUNDER, WeatherSymbolPolicy.icon("lightssnowshowersandthunder_day"))
        assertEquals(WeatherIcon.THUNDER, WeatherSymbolPolicy.icon("rainandthunder"))
        assertEquals(WeatherIcon.SNOW, WeatherSymbolPolicy.icon("heavysnowshowers_night"))
        assertEquals(WeatherIcon.SLEET, WeatherSymbolPolicy.icon("lightsleetshowers_day"))
        assertEquals(WeatherIcon.LIGHT_RAIN, WeatherSymbolPolicy.icon("lightrainshowers_day"))
        assertEquals(WeatherIcon.RAIN, WeatherSymbolPolicy.icon("rainshowers_day"))
        assertEquals(WeatherIcon.HEAVY_RAIN, WeatherSymbolPolicy.icon("heavyrain"))
        assertEquals(WeatherCondition.THUNDER, WeatherSymbolPolicy.condition("lightssleetshowersandthunder_day"))
        assertEquals(WeatherCondition.SLEET_SHOWERS, WeatherSymbolPolicy.condition("heavysleetshowers_day"))
        assertEquals(WeatherCondition.LIGHT_RAIN_SHOWERS, WeatherSymbolPolicy.condition("lightrainshowers_night"))
        assertEquals(WeatherCondition.HEAVY_SNOW, WeatherSymbolPolicy.condition("heavysnow"))
    }

    @Test
    fun `the night suffix only matters for clear and partly cloudy`() {
        assertEquals(WeatherIcon.CLEAR_NIGHT, WeatherSymbolPolicy.icon("clearsky_night"))
        assertEquals(WeatherIcon.CLEAR_DAY, WeatherSymbolPolicy.icon("clearsky_polartwilight"))
        assertEquals(WeatherIcon.PARTLY_NIGHT, WeatherSymbolPolicy.icon("fair_night"))
        assertEquals(WeatherIcon.PARTLY_DAY, WeatherSymbolPolicy.icon("partlycloudy_day"))
        assertEquals(WeatherIcon.RAIN, WeatherSymbolPolicy.icon("rainshowers_night"))
    }

    @Test
    fun `unknown or empty codes fall back`() {
        assertEquals(WeatherIcon.CLOUDY, WeatherSymbolPolicy.icon(""))
        assertEquals(WeatherIcon.CLOUDY, WeatherSymbolPolicy.icon("somethingnew_day"))
        assertEquals(WeatherCondition.UNKNOWN, WeatherSymbolPolicy.condition("somethingnew_day"))
    }
}
