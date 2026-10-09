package io.github.lonemoonspace.dayloom.feature.weather.domain

import io.github.lonemoonspace.dayloom.core.routine.Routine
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/** The weather line of the morning brief. / 早间简报里的天气一行。 */
data class WeatherBrief(val condition: WeatherCondition, val temperature: Int, val umbrella: Boolean)

/**
 * The morning brief's weather: the current reading, and whether either commute of the day calls for an umbrella. Only a
 * window with hourly data can ask for one, as on the card.
 * 早间简报的天气：当前读数，以及当天上下班是否需要带伞。与卡片一样，只有覆盖到逐小时数据的时间窗才可能要求带伞。
 */
object WeatherBriefPolicy {

    fun brief(forecast: Forecast, routine: Routine, now: ZonedDateTime): WeatherBrief? {
        val current = WeatherPointPicker.pick(forecast.points, now) { Instant.ofEpochMilli(it.time).atZone(now.zone) } ?: return null
        val temperature = current.temperature ?: return null
        val umbrella = CommuteWeatherPolicy.evaluate(forecast.points, routine, now).legs.any { it.hasData && it.umbrella }
        return WeatherBrief(WeatherSymbolPolicy.condition(current.symbol), temperature.roundToInt(), umbrella)
    }
}
