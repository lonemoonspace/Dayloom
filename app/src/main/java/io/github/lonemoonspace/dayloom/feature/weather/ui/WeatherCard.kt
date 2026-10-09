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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.SkeletonLines
import io.github.lonemoonspace.dayloom.core.ui.StatusChip
import io.github.lonemoonspace.dayloom.core.ui.rememberMinuteTick
import io.github.lonemoonspace.dayloom.core.ui.rememberPatternFormatter
import io.github.lonemoonspace.dayloom.core.ui.rememberTimeFormatter
import io.github.lonemoonspace.dayloom.core.ui.theme.WET_TILE_RAIN_ALPHA
import io.github.lonemoonspace.dayloom.core.ui.theme.appSurfaces
import io.github.lonemoonspace.dayloom.core.ui.theme.statusColors
import io.github.lonemoonspace.dayloom.core.ui.userMessage
import io.github.lonemoonspace.dayloom.feature.weather.domain.ClothingLevel
import io.github.lonemoonspace.dayloom.feature.weather.domain.DailyForecast
import io.github.lonemoonspace.dayloom.feature.weather.domain.DailyForecastBuilder
import io.github.lonemoonspace.dayloom.feature.weather.domain.DayOutlook
import io.github.lonemoonspace.dayloom.feature.weather.domain.DayOutlookPolicy
import io.github.lonemoonspace.dayloom.feature.weather.domain.Forecast
import io.github.lonemoonspace.dayloom.feature.weather.domain.HourWeather
import io.github.lonemoonspace.dayloom.feature.weather.domain.OutlookDay
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherCondition
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherIcon
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherPointPicker
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherSymbolPolicy
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherTip
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The weather card for Home: the weather now on top, then what the day is like (today, or tomorrow from the evening on) —
 * temperatures, a few hours across the day, rain, what to wear and tips — and a four-day strip at the bottom.
 * 家所在地的天气卡：上面是现在的天气，然后是这一天（今天；入夜后为明天）的情况——气温、几个时间点、降雨、穿衣与温馨提示，
 * 最下面是四天预报横排。
 */
@Composable
internal fun WeatherCard(
    snapshot: Snapshot<Forecast>?,
    error: AppError?,
    isStale: (Snapshot<Forecast>, Instant) -> Boolean,
) {
    val now = rememberMinuteTick()
    val forecast = snapshot?.value
    val current = forecast?.let { f -> WeatherPointPicker.pick(f.points, now) { Instant.ofEpochMilli(it.time).atZone(now.zone) } }
    if (snapshot == null || forecast == null || current == null) {
        InfoCard(title = stringResource(R.string.weather_title)) {
            if (error == null) SkeletonLines() else ErrorLine(error)
        }
        return
    }
    val outlook = remember(forecast, now) { DayOutlookPolicy.outlook(forecast.points, now) }
    val days = remember(forecast, now.toLocalDate(), now.zone) {
        DailyForecastBuilder.build(forecast.points, now.toLocalDate(), now.zone)
    }
    val stale = isStale(snapshot, now.toInstant())
    InfoCard(title = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIconImage(current.symbol, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(conditionText(WeatherSymbolPolicy.condition(current.symbol))),
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
                val feels = current.apparentTemperature ?: current.temperature
                val wind = (current.windSpeed ?: 0.0).roundToInt()
                val gust = current.windGust?.roundToInt()
                Text(
                    text = listOfNotNull(
                        feels?.let { stringResource(R.string.weather_feels_like, it.roundToInt()) },
                        if (gust != null && gust > wind) {
                            stringResource(R.string.weather_wind_gusts, wind, gust)
                        } else {
                            stringResource(R.string.weather_wind_speed, wind)
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            current.temperature?.let { Text(text = "${it.roundToInt()}°", fontSize = 34.sp, fontWeight = FontWeight.SemiBold) }
        }
        outlook?.let { DayOutlookSection(it) }
        val strip = days.take(STRIP_DAYS)
        if (strip.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
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
private fun DayOutlookSection(outlook: DayOutlook) {
    val time = rememberTimeFormatter()
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(if (outlook.day == OutlookDay.TODAY) R.string.common_today else R.string.common_tomorrow),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        if (outlook.symbolCode.isNotEmpty()) {
            WeatherIconImage(outlook.symbolCode, size = 18.dp)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = stringResource(R.string.weather_range, outlook.minTemp.roundToInt(), outlook.maxTemp.roundToInt()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.weather_feels_like, outlook.feelsMin.roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
    if (outlook.timeline.size >= 2) {
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            outlook.timeline.take(TIMELINE_POINTS).forEach { HourCell(it, Modifier.weight(1f)) }
        }
    }
    Spacer(Modifier.height(6.dp))
    val rain = MaterialTheme.statusColors.rain
    val wet = outlook.rainSpells.isNotEmpty()
    InfoTile(
        icon = if (wet) R.drawable.ic_status_umbrella else R.drawable.ic_status_ok,
        iconTint = if (wet) rain else MaterialTheme.statusColors.green,
        background = if (wet) rain.copy(alpha = WET_TILE_RAIN_ALPHA).compositeOver(MaterialTheme.appSurfaces.tile) else MaterialTheme.appSurfaces.tile,
    ) {
        if (wet) {
            val spells = outlook.rainSpells.take(MAX_SPELLS).map {
                stringResource(R.string.weather_rain_spell, it.start.format(time), it.end.format(time))
            }.joinToString(" · ")
            Text(spells, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = listOfNotNull(
                    stringResource(R.string.weather_rain_total, millimetres(outlook.precipMm)),
                    outlook.precipChance?.let { stringResource(R.string.weather_rain_chance, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(stringResource(R.string.weather_no_rain), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Spacer(Modifier.height(6.dp))
    InfoTile(icon = null, iconTint = null, background = MaterialTheme.appSurfaces.tile) {
        Text(
            text = stringResource(R.string.weather_clothing_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(clothingText(outlook.clothing)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        outlook.tips.filterNot { it == WeatherTip.UMBRELLA && wet }.forEach { tip ->
            Text(
                text = "· " + stringResource(tipText(tip)),
                style = MaterialTheme.typography.bodySmall,
                color = if (tip in WARNING_TIPS) MaterialTheme.statusColors.amber else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (tip in WARNING_TIPS) FontWeight.SemiBold else null,
            )
        }
    }
}

/** A tinted block with an optional leading status icon. / 带可选状态图标的底色块。 */
@Composable
private fun InfoTile(@DrawableRes icon: Int?, iconTint: Color?, background: Color, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, TileShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null && iconTint != null) {
            Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun HourCell(hour: HourWeather, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            hour.time.format(rememberTimeFormatter()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hour.symbolCode.isNotEmpty()) WeatherIconImage(hour.symbolCode, size = 22.dp)
        Text(hour.temperature?.let { "${it.roundToInt()}°" }.orEmpty(), style = MaterialTheme.typography.bodySmall)
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
internal fun clothingText(level: ClothingLevel): Int = when (level) {
    ClothingLevel.HOT -> R.string.weather_clothing_hot
    ClothingLevel.WARM -> R.string.weather_clothing_warm
    ClothingLevel.MILD -> R.string.weather_clothing_mild
    ClothingLevel.COOL -> R.string.weather_clothing_cool
    ClothingLevel.COLD -> R.string.weather_clothing_cold
    ClothingLevel.FREEZING -> R.string.weather_clothing_freezing
}

@StringRes
private fun tipText(tip: WeatherTip): Int = when (tip) {
    WeatherTip.THUNDER -> R.string.weather_tip_thunder
    WeatherTip.HEAVY_RAIN -> R.string.weather_tip_heavy_rain
    WeatherTip.UMBRELLA -> R.string.weather_tip_umbrella
    WeatherTip.SNOW -> R.string.weather_tip_snow
    WeatherTip.SLIPPERY -> R.string.weather_tip_slippery
    WeatherTip.STRONG_WIND -> R.string.weather_tip_strong_wind
    WeatherTip.HEAT -> R.string.weather_tip_heat
    WeatherTip.SUNSCREEN -> R.string.weather_tip_sunscreen
    WeatherTip.LAYERS -> R.string.weather_tip_layers
    WeatherTip.PLEASANT -> R.string.weather_tip_pleasant
}

/** Tips about safety get the warning colour. / 与安全有关的提示用警示色。 */
private val WARNING_TIPS = setOf(WeatherTip.THUNDER, WeatherTip.HEAVY_RAIN, WeatherTip.SLIPPERY, WeatherTip.STRONG_WIND, WeatherTip.HEAT)

@StringRes
internal fun conditionText(condition: WeatherCondition): Int = when (condition) {
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

/** Every three hours from 06:00 to 21:00 fits six columns. / 06:00 到 21:00 每三小时一列，共六列。 */
private const val TIMELINE_POINTS = 6

/** More rain spells than this do not fit one line. / 降雨时段超过这个数一行放不下。 */
private const val MAX_SPELLS = 2

/** Four columns still fit an icon and two temperatures on a narrow phone. / 四列在窄屏上还放得下图标和两个温度。 */
private const val STRIP_DAYS = 4

/** A day with at least this much precipitation gets the warning colour. / 当天累计降水达到该值时用警示色。 */
private const val HEAVY_PRECIP_MM = 5.0
