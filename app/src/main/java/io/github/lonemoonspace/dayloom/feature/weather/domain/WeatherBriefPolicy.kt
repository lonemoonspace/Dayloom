package io.github.lonemoonspace.dayloom.feature.weather.domain

import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/** The weather line of the morning brief. / 早间简报里的天气一行。 */
data class WeatherBrief(val condition: WeatherCondition, val temperature: Int, val umbrella: Boolean, val clothing: ClothingLevel?)

/**
 * The morning brief's weather: the current reading, whether the rest of the day calls for an umbrella and what to wear,
 * taken from the same outlook as the card.
 * 早间简报的天气：当前读数、今天余下时间要不要带伞以及穿什么，与卡片取自同一份当天概况。
 */
object WeatherBriefPolicy {

    fun brief(forecast: Forecast, now: ZonedDateTime): WeatherBrief? {
        val current = WeatherPointPicker.pick(forecast.points, now) { Instant.ofEpochMilli(it.time).atZone(now.zone) } ?: return null
        val temperature = current.temperature ?: return null
        val outlook = DayOutlookPolicy.outlook(forecast.points, now)
        val umbrella = outlook?.tips.orEmpty().any { it == WeatherTip.UMBRELLA || it == WeatherTip.HEAVY_RAIN }
        return WeatherBrief(WeatherSymbolPolicy.condition(current.symbol), temperature.roundToInt(), umbrella, outlook?.clothing)
    }
}
