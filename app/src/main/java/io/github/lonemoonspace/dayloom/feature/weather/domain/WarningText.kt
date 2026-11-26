package io.github.lonemoonspace.dayloom.feature.weather.domain

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * The words for a warning, shared by the card, the summary line and the notification so all three say the same.
 * 预警的文字，卡片、摘要行与通知共用，三处说法一致。
 */
object WarningText {

    /** "Orange warning · Wind". / 「橙色预警 · 大风」。 */
    fun title(warning: WeatherWarning): UiText =
        UiText.Res(R.string.weather_warning_title, listOf(UiText.Res(levelText(warning.severity)), UiText.Res(eventText(warning.kind))))

    /**
     * "Wed 06:00–18:00", "Wed 06:00 – Thu 12:00", or "until 18:00" once it has begun.
     * 「周三 06:00–18:00」「周三 06:00 – 周四 12:00」，已经开始时为「至 18:00」。
     */
    fun span(window: WarningWindow, now: ZonedDateTime): UiText {
        val end = UiText.Time(window.end.toLocalTime().withSecond(0).withNano(0))
        val start = window.start
        if (start == null) {
            return if (sameDay(window.end, now)) UiText.Res(R.string.weather_warning_until, listOf(end))
            else UiText.Res(R.string.weather_warning_until_day, listOf(day(window.end, now), end))
        }
        val from = UiText.Time(start.toLocalTime().withSecond(0).withNano(0))
        return if (sameDay(window.end, start) || endsAtMidnight(window.end, start)) {
            UiText.Res(R.string.weather_warning_span, listOf(day(start, now), from, end))
        } else {
            UiText.Res(R.string.weather_warning_span_days, listOf(day(start, now), from, day(window.end, now), end))
        }
    }

    /** One line for the summary and notification: "Orange warning: wind, Wed 06:00–18:00". / 摘要与通知用的一行。 */
    fun line(warning: WeatherWarning, now: ZonedDateTime): UiText = UiText.Res(
        R.string.weather_brief_warning,
        listOf(UiText.Res(levelText(warning.severity)), UiText.Res(eventText(warning.kind)), span(WarningPolicy.window(warning, now), now)),
    )

    /** Today, tomorrow, or the weekday; warnings are issued at most a few days ahead. / 今天、明天或星期几；预警最多提前几天发布。 */
    private fun day(at: ZonedDateTime, now: ZonedDateTime): UiText =
        when (ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())) {
            0L -> UiText.Res(R.string.weather_warning_today)
            1L -> UiText.Res(R.string.weather_warning_tomorrow)
            else -> UiText.Weekday(at.dayOfWeek)
        }

    private fun sameDay(a: ZonedDateTime, b: ZonedDateTime) = a.toLocalDate() == b.toLocalDate()

    /** An end at 00:00 the next day still reads as one day ("Wed 06:00–00:00"). / 次日 00:00 结束仍算同一天（「周三 06:00–00:00」）。 */
    private fun endsAtMidnight(end: ZonedDateTime, start: ZonedDateTime) =
        end.toLocalTime().toSecondOfDay() == 0 && end.toLocalDate() == start.toLocalDate().plusDays(1)

    fun levelText(level: WarningLevel?): Int = when (level) {
        WarningLevel.YELLOW, null -> R.string.weather_warning_yellow
        WarningLevel.ORANGE -> R.string.weather_warning_orange
        WarningLevel.RED -> R.string.weather_warning_red
    }

    fun eventText(event: WarningEvent): Int = when (event) {
        WarningEvent.WIND -> R.string.weather_event_wind
        WarningEvent.RAIN -> R.string.weather_event_rain
        WarningEvent.RAIN_FLOOD -> R.string.weather_event_rain_flood
        WarningEvent.FLOOD -> R.string.weather_event_flood
        WarningEvent.SNOW -> R.string.weather_event_snow
        WarningEvent.BLOWING_SNOW -> R.string.weather_event_blowing_snow
        WarningEvent.ICE -> R.string.weather_event_ice
        WarningEvent.LIGHTNING -> R.string.weather_event_lightning
        WarningEvent.FOREST_FIRE -> R.string.weather_event_forest_fire
        WarningEvent.AVALANCHES -> R.string.weather_event_avalanches
        WarningEvent.DRIVING_CONDITIONS -> R.string.weather_event_driving
        WarningEvent.STORM_SURGE -> R.string.weather_event_storm_surge
        WarningEvent.GALE -> R.string.weather_event_gale
        WarningEvent.POLAR_LOW -> R.string.weather_event_polar_low
        WarningEvent.ICING -> R.string.weather_event_icing
        WarningEvent.OTHER -> R.string.weather_event_other
    }
}
