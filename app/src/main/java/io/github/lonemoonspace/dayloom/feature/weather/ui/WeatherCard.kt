package io.github.lonemoonspace.dayloom.feature.weather.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
import io.github.lonemoonspace.dayloom.core.ui.rememberMinuteTick
import io.github.lonemoonspace.dayloom.core.ui.rememberPatternFormatter
import io.github.lonemoonspace.dayloom.core.ui.rememberTimeFormatter
import io.github.lonemoonspace.dayloom.core.ui.theme.statusColors
import io.github.lonemoonspace.dayloom.core.ui.userMessage
import io.github.lonemoonspace.dayloom.feature.weather.domain.ClothingLevel
import io.github.lonemoonspace.dayloom.feature.weather.domain.DailyForecast
import io.github.lonemoonspace.dayloom.feature.weather.domain.DailyForecastBuilder
import io.github.lonemoonspace.dayloom.feature.weather.domain.DayOutlook
import io.github.lonemoonspace.dayloom.feature.weather.domain.DayOutlookPolicy
import io.github.lonemoonspace.dayloom.feature.weather.domain.Forecast
import io.github.lonemoonspace.dayloom.feature.weather.domain.ForecastPoint
import io.github.lonemoonspace.dayloom.feature.weather.domain.OutlookDay
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherCondition
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherIcon
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherPointPicker
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherSymbolPolicy
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherTip
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The weather card for Home, one block in the theme's primary colour (the same on every day, whatever the weather): the
 * weather now, the day's temperature curve with rain bars (today, or tomorrow from the evening on), one row of advice
 * pills for rain, clothing and tips, and the next four days.
 * 家所在地的天气卡，一整块主题主色（不随天气变色）：现在的天气、这一天（今天；入夜后为明天）的温度曲线与雨量柱、
 * 一行降雨、穿衣与提示的建议胶囊，以及之后四天。
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
        InfoCard(title = stringResource(R.string.weather_title), icon = R.drawable.ic_wx_partly_day) {
            if (error == null) SkeletonLines() else Text(error.userMessage().asString(), color = MaterialTheme.statusColors.red, style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val outlook = remember(forecast, now) { DayOutlookPolicy.outlook(forecast.points, now) }
    val days = remember(forecast, now.toLocalDate(), now.zone) {
        DailyForecastBuilder.build(forecast.points, now.toLocalDate(), now.zone)
    }
    val colors = heroColors()
    CompositionLocalProvider(LocalContentColor provides colors.content) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(Brush.linearGradient(listOf(colors.container, lerp(colors.container, Color.Black, GRADIENT_DARKEN))))
                .animateContentSize()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
        ) {
            NowRow(current, outlook, stale = isStale(snapshot, now.toInstant()))
            val chart = outlook?.takeIf { o -> o.hours.count { it.temperature != null } >= MIN_CHART_HOURS }
            if (chart != null) {
                Spacer(Modifier.height(6.dp))
                DayChart(chart)
            }
            outlook?.let { AdvicePills(it, amountsInChart = chart != null) }
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                HeroPill(error.userMessage().asString(), icon = R.drawable.ic_status_warn, strong = true)
            }
            val strip = days.filter { it.date > (outlook?.date ?: now.toLocalDate()) }.take(STRIP_DAYS)
            if (strip.isNotEmpty()) {
                Divider()
                Row {
                    strip.forEach { DayCell(it, Modifier.weight(1f)) }
                    repeat(STRIP_DAYS - strip.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = stringResource(R.string.weather_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private data class HeroColors(val container: Color, val content: Color)

/**
 * The wallpaper's primary in light mode, its primary container in dark mode, so the card is the brightest block on the
 * page in both and its text keeps the scheme's own contrast pair.
 * 浅色时用壁纸主色，深色时用主色容器色，两种模式下这张卡都是页面上最醒目的一块，文字沿用配色自带的对比色。
 */
@Composable
private fun heroColors(): HeroColors {
    val scheme = MaterialTheme.colorScheme
    return if (isSystemInDarkTheme()) HeroColors(scheme.primaryContainer, scheme.onPrimaryContainer) else HeroColors(scheme.primary, scheme.onPrimary)
}

@Composable
private fun NowRow(current: ForecastPoint, outlook: DayOutlook?, stale: Boolean) {
    val content = LocalContentColor.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        WeatherIconImage(current.symbol, size = 40.dp)
        Spacer(Modifier.width(8.dp))
        current.temperature?.let { Text("${it.roundToInt()}°", fontSize = 44.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold) }
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
                    HeroPill(stringResource(R.string.common_cached))
                }
            }
            val feels = current.apparentTemperature ?: current.temperature
            val wind = (current.windSpeed ?: 0.0).roundToInt()
            val gust = current.windGust?.roundToInt()
            Text(
                text = listOfNotNull(
                    feels?.let { stringResource(R.string.weather_feels_like, it.roundToInt()) },
                    if (gust != null && gust > wind) stringResource(R.string.weather_wind_gusts, wind, gust) else stringResource(R.string.weather_wind_speed, wind),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.85f),
                maxLines = 2,
            )
        }
        outlook?.let {
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.weather_range, it.minTemp.roundToInt(), it.maxTemp.roundToInt()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    stringResource(if (it.day == OutlookDay.TODAY) R.string.common_today else R.string.common_tomorrow),
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/**
 * Rain first (when and how much, or that it stays dry), then what to wear, then the tips; safety tips are bold.
 * 先说雨（什么时候、多少，或者不会下），再说穿什么，最后是提示；与安全有关的提示加粗。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvicePills(outlook: DayOutlook, amountsInChart: Boolean) {
    val time = rememberTimeFormatter()
    val wet = outlook.rainSpells.isNotEmpty()
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (wet) {
            val spells = outlook.rainSpells.take(MAX_SPELLS).map {
                stringResource(R.string.weather_rain_spell, it.start.format(time), it.end.format(time))
            }
            // The chart already labels amount and chance. / 图表上已经标了雨量与概率。
            val amounts = if (amountsInChart) emptyList() else rainAmounts(outlook)
            val text = (spells + amounts).joinToString(" · ")
            HeroPill(text, icon = R.drawable.ic_status_umbrella, strong = true)
        } else {
            HeroPill(stringResource(R.string.weather_no_rain))
        }
        HeroPill(stringResource(clothingText(outlook.clothing)))
        // The rain pill already says to take an umbrella. / 雨的那颗胶囊已经说了要带伞。
        outlook.tips.filterNot { it == WeatherTip.UMBRELLA && wet }.forEach { tip ->
            HeroPill(stringResource(tipText(tip)), strong = tip in WARNING_TIPS)
        }
    }
}

@Composable
private fun HeroPill(text: String, @DrawableRes icon: Int? = null, strong: Boolean = false) {
    val content = LocalContentColor.current
    Row(
        modifier = Modifier
            .background(content.copy(alpha = if (strong) 0.26f else 0.16f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = if (strong) FontWeight.SemiBold else null)
    }
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalContentColor.current.copy(alpha = 0.18f)))
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun rainAmounts(outlook: DayOutlook): List<String> = listOfNotNull(
    stringResource(R.string.weather_rain_total, millimetres(outlook.precipMm)),
    outlook.precipChance?.let { stringResource(R.string.weather_rain_chance, it) },
)

/**
 * The day at a glance: a temperature curve with its high and low marked, rain bars on their own scale underneath, the
 * rain spells shaded, and the hour every three hours. Drawn rather than laid out in columns, so the curve and the bars
 * share one time axis whatever the screen width.
 * 一眼看全天：标出最高与最低的温度曲线，下方是按自己比例画的雨量柱，降雨时段加底色，每三小时标一个钟点。用画的而不是
 * 按列排布，曲线与雨量柱在任何屏幕宽度下都共用同一条时间轴。
 */
@Composable
private fun DayChart(outlook: DayOutlook) {
    val hours = outlook.hours.filter { it.temperature != null }
    val content = LocalContentColor.current
    val rainColor = lerp(content, RAIN_TINT, RAIN_TINT_AMOUNT)
    val measurer = rememberTextMeasurer()
    val hourFormat = rememberPatternFormatter("HH")
    val time = rememberTimeFormatter()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = content.copy(alpha = 0.75f))
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = content, fontWeight = FontWeight.SemiBold)
    val rainStyle = MaterialTheme.typography.labelSmall.copy(color = content.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold)
    val rainLabel = rainAmounts(outlook).joinToString(" · ").takeIf { outlook.rainSpells.isNotEmpty() }
    val ticks = DayOutlookPolicy.ticks(hours).map { it.time to it.time.format(hourFormat) }
    val description = (
        listOf(stringResource(R.string.weather_range, outlook.minTemp.roundToInt(), outlook.maxTemp.roundToInt())) +
            outlook.rainSpells.map { stringResource(R.string.weather_rain_spell, it.start.format(time), it.end.format(time)) }
        ).joinToString(", ")
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(if (rainLabel != null) CHART_HEIGHT_WET else CHART_HEIGHT_DRY)
            .semantics { contentDescription = description },
    ) {
        val labelHeight = measurer.measure("0", labelStyle).size.height.toFloat()
        val valueHeight = measurer.measure("0", valueStyle).size.height.toFloat()
        val side = 10.dp.toPx()
        val first = hours.first().time.toEpochSecond().toFloat()
        val span = (hours.last().time.toEpochSecond() - hours.first().time.toEpochSecond()).toFloat().coerceAtLeast(1f)
        val step = (size.width - 2 * side) / (hours.size - 1).coerceAtLeast(1)
        fun x(at: java.time.ZonedDateTime) = side + (at.toEpochSecond() - first) / span * (size.width - 2 * side)

        // Bottom up: hour labels, rain bars, the rain label, then the curve in what is left.
        // 自下而上：钟点、雨量柱、雨量标注，剩下的空间画曲线。
        val tickTop = size.height - labelHeight
        val rainBottom = tickTop - 3.dp.toPx()
        val rainTop = rainBottom - RAIN_BAND.toPx()
        val rainLabelTop = rainTop - labelHeight - 1.dp.toPx()
        val tempBottom = (if (rainLabel != null) rainLabelTop else rainTop) - 6.dp.toPx()
        val tempTop = valueHeight + 4.dp.toPx()

        val temps = hours.map { it.temperature!! }
        val low = temps.min()
        val high = temps.max()
        // At least a few degrees of range, or a flat day would be drawn as a wild curve. / 至少几度的范围，否则平稳的一天会被画成剧烈起伏。
        val pad = ((MIN_RANGE_C - (high - low)) / 2).coerceAtLeast(0.0)
        val lo = low - pad
        val hi = high + pad
        fun y(t: Double) = (tempBottom - (t - lo) / (hi - lo) * (tempBottom - tempTop)).toFloat()

        outlook.rainSpells.forEach { spell ->
            val left = x(spell.start) - step / 2
            val right = x(spell.end.minusHours(1)) + step / 2
            drawRoundRect(
                color = content.copy(alpha = 0.08f),
                topLeft = Offset(left.coerceAtLeast(0f), tempTop - 4.dp.toPx()),
                size = Size((right - left).coerceAtMost(size.width), rainBottom - tempTop + 4.dp.toPx()),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )
        }

        val line = Path()
        hours.forEachIndexed { i, h -> if (i == 0) line.moveTo(x(h.time), y(h.temperature!!)) else line.lineTo(x(h.time), y(h.temperature!!)) }
        val area = Path().apply {
            addPath(line)
            lineTo(x(hours.last().time), tempBottom)
            lineTo(x(hours.first().time), tempBottom)
            close()
        }
        drawPath(area, content.copy(alpha = 0.12f))
        drawPath(line, content, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // High above its point; low too, unless it sits right next to the high. / 最高温标在点上方；最低温同样，除非紧挨着最高温。
        val highHour = hours[temps.indexOf(high)]
        val lowHour = hours[temps.indexOf(low)]
        drawCircle(content, radius = 3.dp.toPx(), center = Offset(x(highHour.time), y(high)))
        fun label(text: String, at: Offset, style: androidx.compose.ui.text.TextStyle, above: Boolean = true) {
            val layout = measurer.measure(text, style)
            val left = (at.x - layout.size.width / 2).coerceIn(0f, size.width - layout.size.width)
            val top = if (above) at.y - layout.size.height - 2.dp.toPx() else at.y + 2.dp.toPx()
            drawText(layout, topLeft = Offset(left, top.coerceAtLeast(0f)))
        }
        label("${high.roundToInt()}°", Offset(x(highHour.time), y(high)), valueStyle)
        if (high - low >= 1 && kotlin.math.abs(x(lowHour.time) - x(highHour.time)) > 2.5f * step) {
            label("${low.roundToInt()}°", Offset(x(lowHour.time), y(low)), labelStyle.copy(color = content.copy(alpha = 0.85f)))
        }

        val barWidth = (step * 0.6f).coerceAtMost(12.dp.toPx())
        val maxRain = maxOf(RAIN_SCALE_MM, hours.maxOf { it.precipMm })
        hours.filter { it.precipMm >= DayOutlookPolicy.UMBRELLA_MM }.forEach { h ->
            val height = (h.precipMm / maxRain * (rainBottom - rainTop)).toFloat().coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = rainColor,
                topLeft = Offset(x(h.time) - barWidth / 2, rainBottom - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
        if (rainLabel != null) {
            val wettest = outlook.rainSpells.maxBy { spell -> hours.filter { !it.time.isBefore(spell.start) && it.time.isBefore(spell.end) }.sumOf { it.precipMm } }
            val centre = (x(wettest.start) + x(wettest.end.minusHours(1))) / 2
            label(rainLabel, Offset(centre, rainLabelTop), rainStyle, above = false)
        }

        ticks.forEach { (at, text) ->
            val layout = measurer.measure(text, labelStyle)
            val left = (x(at) - layout.size.width / 2).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(left, tickTop))
        }
    }
}

@Composable
private fun DayCell(day: DailyForecast, modifier: Modifier) {
    // Weekday names only: "Tomorrow" is too wide for a quarter of the row in English. / 只用星期：英文的「Tomorrow」在四分之一行里放不下。
    val label = day.date.format(rememberPatternFormatter("EEE"))
    val content = LocalContentColor.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.75f), maxLines = 1)
        WeatherIconImage(day.symbolCode, size = 22.dp, modifier = Modifier.padding(vertical = 1.dp))
        Text(
            "${day.maxTemp.roundToInt()}°/${day.minTemp.roundToInt()}°",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (day.precipMm >= HEAVY_PRECIP_MM) FontWeight.Bold else null,
            maxLines = 1,
        )
    }
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

/** How much darker the bottom-right corner is than the top-left. / 右下角比左上角暗多少。 */
private const val GRADIENT_DARKEN = 0.22f

/** Fewer hours than this (late in the evening window) make no curve worth drawing. / 少于这么多小时（时间窗快结束时）画不出有意义的曲线。 */
private const val MIN_CHART_HOURS = 3

private val CHART_HEIGHT_WET = 118.dp
private val CHART_HEIGHT_DRY = 100.dp
private val RAIN_BAND = 20.dp

/** The curve spans at least this many degrees. / 曲线至少覆盖这么多度。 */
private const val MIN_RANGE_C = 6.0

/** An hour this wet fills the rain band; heavier hours rescale it. / 一小时达到这个雨量就占满雨量带；更大的雨会重新按比例缩放。 */
private const val RAIN_SCALE_MM = 1.5

/** Rain bars are the card's text colour pulled towards blue, so they read on any wallpaper colour. / 雨量柱是卡片文字色向蓝色偏移，任何壁纸色上都看得清。 */
private val RAIN_TINT = Color(0xFF8AB4F8)
private const val RAIN_TINT_AMOUNT = 0.55f

/** More rain spells than this do not fit one line. / 降雨时段超过这个数一行放不下。 */
private const val MAX_SPELLS = 2

/** Four columns still fit an icon and two temperatures on a narrow phone. / 四列在窄屏上还放得下图标和两个温度。 */
private const val STRIP_DAYS = 4

/** A day with at least this much precipitation is shown bold. / 当天累计降水达到该值时加粗显示。 */
private const val HEAVY_PRECIP_MM = 5.0
