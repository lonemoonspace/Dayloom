package io.github.lonemoonspace.dayloom.feature.weather.domain

/**
 * The weather icons; MET's 40-odd symbol codes fold into these thirteen vector drawables.
 * 天气图标的种类；MET 的四十多个符号名收敛成这十三种矢量图标。
 */
enum class WeatherIcon {
    CLEAR_DAY, CLEAR_NIGHT, PARTLY_DAY, PARTLY_NIGHT, CLOUDY, FOG, WIND,
    LIGHT_RAIN, RAIN, HEAVY_RAIN, SLEET, SNOW, THUNDER,
}

/**
 * What the weather text says; each value has one string resource. Finer than [WeatherIcon] (showers vs. rain) but coarser
 * than MET's codes, whose thunder variants all read as "thunder".
 * 天气文字描述的种类，每个值对应一个字符串资源。比 [WeatherIcon] 细（区分阵雨与雨），比 MET 符号粗（各种雷暴都叫「雷暴」）。
 */
enum class WeatherCondition {
    CLEAR, FAIR, PARTLY_CLOUDY, CLOUDY, FOG, WIND,
    LIGHT_RAIN, RAIN, HEAVY_RAIN, LIGHT_RAIN_SHOWERS, RAIN_SHOWERS, HEAVY_RAIN_SHOWERS,
    LIGHT_SLEET, SLEET, HEAVY_SLEET, SLEET_SHOWERS,
    LIGHT_SNOW, SNOW, HEAVY_SNOW, SNOW_SHOWERS,
    THUNDER, UNKNOWN,
}

/**
 * Maps MET symbol codes (`<base>_<day|night|polartwilight>`) to icons and conditions. Unknown codes fall back instead of
 * failing, because MET may add codes.
 * 把 MET 符号名（`<基础名>_<day|night|polartwilight>`）映射为图标与天气描述。未知符号走兜底而不报错，因为 MET 可能新增符号。
 */
object WeatherSymbolPolicy {

    /**
     * Thunder wins over the precipitation type ("snow showers and thunder" draws lightning), snow and sleet win over rain;
     * the `_night` suffix only matters for clear and partly cloudy. Unknown codes get the cloudy icon rather than nothing.
     * 雷暴优先于降水类型（「阵雪雷暴」画闪电），雪、冰雨优先于雨；`_night` 后缀只影响晴和少云/多云。未知符号退回阴天图标，不留空。
     */
    fun icon(code: String): WeatherIcon {
        val key = code.substringBefore("_")
        val night = code.endsWith("_night")
        return when {
            key == "clearsky" -> if (night) WeatherIcon.CLEAR_NIGHT else WeatherIcon.CLEAR_DAY
            key == "fair" || key == "partlycloudy" -> if (night) WeatherIcon.PARTLY_NIGHT else WeatherIcon.PARTLY_DAY
            key == "cloudy" -> WeatherIcon.CLOUDY
            key == "fog" -> WeatherIcon.FOG
            key == "wind" -> WeatherIcon.WIND
            "thunder" in key -> WeatherIcon.THUNDER
            "snow" in key -> WeatherIcon.SNOW
            "sleet" in key -> WeatherIcon.SLEET
            key.startsWith("heavyrain") -> WeatherIcon.HEAVY_RAIN
            key.startsWith("lightrain") -> WeatherIcon.LIGHT_RAIN
            "rain" in key -> WeatherIcon.RAIN
            else -> WeatherIcon.CLOUDY
        }
    }

    fun condition(code: String): WeatherCondition {
        val key = code.substringBefore("_")
        val showers = "showers" in key
        val light = key.startsWith("light")
        val heavy = key.startsWith("heavy")
        return when {
            key in SIMPLE -> SIMPLE.getValue(key)
            // MET's historical spellings "lightssleet…" and "lightssnow…" still contain "thunder", so they land here.
            // MET 历史符号名「lightssleet…」「lightssnow…」里也有 thunder，同样落在这里。
            "thunder" in key -> WeatherCondition.THUNDER
            "sleet" in key -> when {
                showers -> WeatherCondition.SLEET_SHOWERS
                light -> WeatherCondition.LIGHT_SLEET
                heavy -> WeatherCondition.HEAVY_SLEET
                else -> WeatherCondition.SLEET
            }
            "snow" in key -> when {
                showers -> WeatherCondition.SNOW_SHOWERS
                light -> WeatherCondition.LIGHT_SNOW
                heavy -> WeatherCondition.HEAVY_SNOW
                else -> WeatherCondition.SNOW
            }
            "rain" in key -> when {
                showers && light -> WeatherCondition.LIGHT_RAIN_SHOWERS
                showers && heavy -> WeatherCondition.HEAVY_RAIN_SHOWERS
                showers -> WeatherCondition.RAIN_SHOWERS
                light -> WeatherCondition.LIGHT_RAIN
                heavy -> WeatherCondition.HEAVY_RAIN
                else -> WeatherCondition.RAIN
            }
            else -> WeatherCondition.UNKNOWN
        }
    }

    private val SIMPLE = mapOf(
        "clearsky" to WeatherCondition.CLEAR,
        "fair" to WeatherCondition.FAIR,
        "partlycloudy" to WeatherCondition.PARTLY_CLOUDY,
        "cloudy" to WeatherCondition.CLOUDY,
        "fog" to WeatherCondition.FOG,
        "wind" to WeatherCondition.WIND,
    )
}
