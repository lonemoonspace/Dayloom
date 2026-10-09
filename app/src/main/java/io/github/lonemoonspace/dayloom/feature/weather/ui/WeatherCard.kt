package io.github.lonemoonspace.dayloom.feature.weather.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.i18n.asString
import io.github.lonemoonspace.dayloom.core.location.Place
import io.github.lonemoonspace.dayloom.core.routine.Routine
import io.github.lonemoonspace.dayloom.core.routine.WindowKind
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.SkeletonLines
import io.github.lonemoonspace.dayloom.core.ui.StatusChip
import io.github.lonemoonspace.dayloom.core.ui.placeTitle
import io.github.lonemoonspace.dayloom.core.ui.rememberMinuteTick
import io.github.lonemoonspace.dayloom.core.ui.rememberPatternFormatter
import io.github.lonemoonspace.dayloom.core.ui.rememberTimeFormatter
import io.github.lonemoonspace.dayloom.core.ui.theme.WET_TILE_RAIN_ALPHA
import io.github.lonemoonspace.dayloom.core.ui.theme.appSurfaces
import io.github.lonemoonspace.dayloom.core.ui.theme.statusColors
import io.github.lonemoonspace.dayloom.core.ui.userMessage
import io.github.lonemoonspace.dayloom.feature.weather.domain.CommuteWeatherPolicy
import io.github.lonemoonspace.dayloom.feature.weather.domain.DailyForecast
import io.github.lonemoonspace.dayloom.feature.weather.domain.DailyForecastBuilder
import io.github.lonemoonspace.dayloom.feature.weather.domain.Forecast
import io.github.lonemoonspace.dayloom.feature.weather.domain.LegWeather
import io.github.lonemoonspace.dayloom.feature.weather.domain.RainChange
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherCondition
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherIcon
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherPointPicker
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherSymbolPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The commute-oriented weather card: the weather now on top, whether the to-work and back-home windows need an umbrella in
 * the middle, and a four-day strip at the bottom.
 * 以通勤为导向的天气卡：上面是现在的天气，中间是去程、返程两段时间窗要不要带伞，下面是四天预报横排。
 */
@Composable
internal fun WeatherCard(
    snapshot: Snapshot<Forecast>?,
    error: AppError?,
    isStale: (Snapshot<Forecast>, Instant) -> Boolean,
    routine: Routine,
    place: Place?,
) {
    val now = rememberMinuteTick()
    val title = stringResource(R.string.weather_title)
    val forecast = snapshot?.value
    val current = forecast?.let { f -> WeatherPointPicker.pick(f.points, now) { Instant.ofEpochMilli(it.time).atZone(now.zone) } }
    if (snapshot == null || forecast == null || current == null) {
        InfoCard(title = place?.let { "$title · ${placeTitle(it)}" } ?: title) {
            if (error == null) SkeletonLines() else ErrorLine(error)
        }
        return
    }
    val commute = remember(forecast, routine, now) { CommuteWeatherPolicy.evaluate(forecast.points, routine, now) }
    val days = remember(forecast, now.toLocalDate(), now.zone) {
        DailyForecastBuilder.build(forecast.points, now.toLocalDate(), now.zone)
    }
    val today = days.firstOrNull { it.date == now.toLocalDate() }
    val stale = isStale(snapshot, now.toInstant())
    InfoCard(title = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIconImage(current.symbol, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val condition = stringResource(conditionText(WeatherSymbolPolicy.condition(current.symbol)))
                    Text(
                        text = place?.let { "$condition · ${placeTitle(it)}" } ?: condition,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (stale) {
                        Spacer(Modifier.width(6.dp))
                        StatusChip(stringResource(R.string.common_cached), MaterialTheme.statusColors.amber)
                    }
                }
                val details = stringResource(
                    R.string.weather_wind_precip,
                    (current.windSpeed ?: 0.0).roundToInt(),
                    millimetres(current.precipitation1h ?: current.precipitation6h?.div(6) ?: 0.0),
                )
                val range = today?.let { " · ${it.minTemp.roundToInt()}°–${it.maxTemp.roundToInt()}°" }.orEmpty()
                Text(
                    text = details + range,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            current.temperature?.let { Text(text = "${it.roundToInt()}°", fontSize = 32.sp, fontWeight = FontWeight.SemiBold) }
        }
        if (commute.legs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                commute.legs.forEach { LegTile(it, now, Modifier.weight(1f)) }
            }
        }
        commute.rainChange?.let { change ->
            val at = change.at.format(rememberTimeFormatter())
            Text(
                text = when (change) {
                    is RainChange.Stops -> stringResource(R.string.weather_rain_stops, at)
                    is RainChange.Starts -> stringResource(R.string.weather_rain_starts, at)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.statusColors.rain,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        val strip = days.take(STRIP_DAYS)
        if (strip.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                strip.forEach { DayCell(it, now.toLocalDate(), Modifier.weight(1f)) }
            }
        }
        if (error != null) ErrorLine(error)
        Text(
            text = stringResource(R.string.weather_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun LegTile(leg: LegWeather, now: ZonedDateTime, modifier: Modifier) {
    val tile = MaterialTheme.appSurfaces.tile
    val rain = MaterialTheme.statusColors.rain
    val background = if (leg.umbrella) rain.copy(alpha = WET_TILE_RAIN_ALPHA).compositeOver(tile) else tile
    val time = rememberTimeFormatter()
    val weekday = rememberPatternFormatter("EEE")
    val name = stringResource(if (leg.kind == WindowKind.TO_WORK) R.string.weather_leg_to_work else R.string.weather_leg_back_home)
    val day = leg.start.toLocalDate()
    val label = when (day) {
        now.toLocalDate() -> name
        now.toLocalDate().plusDays(1) -> stringResource(R.string.weather_leg_on_day, stringResource(R.string.common_tomorrow), name)
        else -> stringResource(R.string.weather_leg_on_day, day.format(weekday), name)
    }
    Column(
        modifier = modifier
            .background(background, TileShape)
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Text(
            text = "$label ${leg.start.format(time)}–${leg.end.format(time)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        if (!leg.hasData) {
            Text(
                text = stringResource(R.string.weather_leg_no_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leg.symbolCode.isNotEmpty()) {
                WeatherIconImage(leg.symbolCode, size = 18.dp)
                Spacer(Modifier.width(4.dp))
            }
            leg.temperature?.let {
                Text("${it.roundToInt()}°", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            if (leg.umbrella) {
                StatusChip(stringResource(R.string.weather_umbrella), rain, icon = R.drawable.ic_status_umbrella)
            } else {
                StatusChip(stringResource(R.string.weather_dry), MaterialTheme.statusColors.green, icon = R.drawable.ic_status_ok)
            }
        }
    }
}

@Composable
private fun DayCell(day: DailyForecast, today: LocalDate, modifier: Modifier) {
    val weekday = rememberPatternFormatter("EEE")
    val label = when (day.date) {
        today -> stringResource(R.string.common_today)
        today.plusDays(1) -> stringResource(R.string.common_tomorrow)
        else -> day.date.format(weekday)
    }
    Column(
        modifier = modifier
            .then(if (day.date == today) Modifier.background(MaterialTheme.appSurfaces.tile, TileShape) else Modifier)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        WeatherIconImage(day.symbolCode, size = 26.dp)
        Spacer(Modifier.height(2.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("${day.maxTemp.roundToInt()}°", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Text(
                "${day.minTemp.roundToInt()}°",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // An empty line on dry days keeps the four columns the same height. / 没雨也占一行，四列的高度才对得齐。
        Text(
            text = if (day.precipMm >= 0.1) "${millimetres(day.precipMm)} mm" else "",
            style = MaterialTheme.typography.labelSmall,
            color = if (day.precipMm >= HEAVY_PRECIP_MM) MaterialTheme.statusColors.amber else MaterialTheme.statusColors.rain,
            fontWeight = if (day.precipMm >= HEAVY_PRECIP_MM) FontWeight.SemiBold else null,
            maxLines = 1,
        )
    }
}

@Composable
private fun ErrorLine(error: AppError) {
    Text(
        text = error.userMessage().asString(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.statusColors.red,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * Weather icon in full colour (not tinted). The text next to it always says the same, so the icon is not read out.
 * 彩色天气图标（不着色）。旁边总有文字描述，所以图标本身不朗读。
 */
@Composable
internal fun WeatherIconImage(symbolCode: String, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(WeatherSymbolPolicy.icon(symbolCode).drawable()),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/** Precipitation with one decimal and a dot in every language, like the unit it comes with. / 降水量保留一位小数，各语言都用小数点，与单位一致。 */
private fun millimetres(mm: Double): String = String.format(Locale.ROOT, "%.1f", mm)

@DrawableRes
private fun WeatherIcon.drawable(): Int = when (this) {
    WeatherIcon.CLEAR_DAY -> R.drawable.ic_wx_clear_day
    WeatherIcon.CLEAR_NIGHT -> R.drawable.ic_wx_clear_night
    WeatherIcon.PARTLY_DAY -> R.drawable.ic_wx_partly_day
    WeatherIcon.PARTLY_NIGHT -> R.drawable.ic_wx_partly_night
    WeatherIcon.CLOUDY -> R.drawable.ic_wx_cloudy
    WeatherIcon.FOG -> R.drawable.ic_wx_fog
    WeatherIcon.WIND -> R.drawable.ic_wx_wind
    WeatherIcon.LIGHT_RAIN -> R.drawable.ic_wx_light_rain
    WeatherIcon.RAIN -> R.drawable.ic_wx_rain
    WeatherIcon.HEAVY_RAIN -> R.drawable.ic_wx_heavy_rain
    WeatherIcon.SLEET -> R.drawable.ic_wx_sleet
    WeatherIcon.SNOW -> R.drawable.ic_wx_snow
    WeatherIcon.THUNDER -> R.drawable.ic_wx_thunder
}

@StringRes
private fun conditionText(condition: WeatherCondition): Int = when (condition) {
    WeatherCondition.CLEAR -> R.string.weather_clear
    WeatherCondition.FAIR -> R.string.weather_fair
    WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherCondition.CLOUDY -> R.string.weather_cloudy
    WeatherCondition.FOG -> R.string.weather_fog
    WeatherCondition.WIND -> R.string.weather_wind
    WeatherCondition.LIGHT_RAIN -> R.string.weather_light_rain
    WeatherCondition.RAIN -> R.string.weather_rain
    WeatherCondition.HEAVY_RAIN -> R.string.weather_heavy_rain
    WeatherCondition.LIGHT_RAIN_SHOWERS -> R.string.weather_light_rain_showers
    WeatherCondition.RAIN_SHOWERS -> R.string.weather_rain_showers
    WeatherCondition.HEAVY_RAIN_SHOWERS -> R.string.weather_heavy_rain_showers
    WeatherCondition.LIGHT_SLEET -> R.string.weather_light_sleet
    WeatherCondition.SLEET -> R.string.weather_sleet
    WeatherCondition.HEAVY_SLEET -> R.string.weather_heavy_sleet
    WeatherCondition.SLEET_SHOWERS -> R.string.weather_sleet_showers
    WeatherCondition.LIGHT_SNOW -> R.string.weather_light_snow
    WeatherCondition.SNOW -> R.string.weather_snow
    WeatherCondition.HEAVY_SNOW -> R.string.weather_heavy_snow
    WeatherCondition.SNOW_SHOWERS -> R.string.weather_snow_showers
    WeatherCondition.THUNDER -> R.string.weather_thunder
    WeatherCondition.UNKNOWN -> R.string.weather_unknown
}

private val TileShape = RoundedCornerShape(10.dp)

/** Four columns still fit an icon and two temperatures on a narrow phone. / 四列在窄屏上还放得下图标和两个温度。 */
private const val STRIP_DAYS = 4

/** A day with at least this much precipitation gets the warning colour. / 当天累计降水达到该值时用警示色。 */
private const val HEAVY_PRECIP_MM = 5.0
