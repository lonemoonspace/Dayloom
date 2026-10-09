package io.github.lonemoonspace.dayloom.feature.weather.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which day the weather card describes. / 天气卡片说的是哪一天。 */
enum class OutlookDay { TODAY, TOMORROW }

/** What to wear, from the coldest feels-like temperature of the day. / 穿什么，按当天最低的体感温度判断。 */
enum class ClothingLevel { HOT, WARM, MILD, COOL, COLD, FREEZING }

/**
 * Friendly tips, most important first. The UI maps each to one sentence.
 * 温馨提示，按重要性排序；界面把每一项映射为一句话。
 */
enum class WeatherTip { THUNDER, HEAVY_RAIN, UMBRELLA, SNOW, SLIPPERY, STRONG_WIND, HEAT, SUNSCREEN, LAYERS, PLEASANT }

/** A stretch of hours with rain worth an umbrella; [end] is exclusive. / 一段需要带伞的降雨时段；[end] 不含。 */
data class RainSpell(val start: ZonedDateTime, val end: ZonedDateTime)

/** One hour of the day's timeline. / 当天时间线上的一个小时。 */
data class HourWeather(val time: ZonedDateTime, val symbolCode: String, val temperature: Double?, val precipMm: Double)

/**
 * The weather of the day the card shows: its daytime hours (06:00–22:00, only what is left of them today), summarised.
 * 卡片所说那一天的天气：白天时段（06:00–22:00，今天只算剩下的部分）的汇总。
 */
data class DayOutlook(
    val date: LocalDate,
    val day: OutlookDay,
    /** The symbol of the wettest hour if it rains, else of the hour closest to noon. / 有雨时取最湿那小时的符号，否则取最接近正午的。 */
    val symbolCode: String,
    val minTemp: Double,
    val maxTemp: Double,
    val feelsMin: Double,
    val feelsMax: Double,
    /** Total over the daytime hours, mm. / 白天时段的累计降水，毫米。 */
    val precipMm: Double,
    /** Highest hourly chance of rain, 0–100; null without data. / 单小时降水概率最大值，0–100；没有数据时为 null。 */
    val precipChance: Int?,
    val rainSpells: List<RainSpell>,
    val maxWind: Double,
    val maxGust: Double?,
    val uvMax: Double?,
    val clothing: ClothingLevel,
    val tips: List<WeatherTip>,
    /** Every [DayOutlookPolicy.TIMELINE_STEP_HOURS] hours across the window. / 时间窗内每 [DayOutlookPolicy.TIMELINE_STEP_HOURS] 小时一个点。 */
    val timeline: List<HourWeather>,
)

/**
 * Turns the forecast into "what today is like" — or tomorrow, once the evening starts, because by then the rest of today no
 * longer needs planning. Works on hourly points only: six-hour amounts must not be read as hourly.
 * 把预报变成「今天怎么样」；入夜后改说明天，因为那时今天剩下的时间已经不需要安排了。只用逐小时数据：6 小时降水量不能当一小时用。
 */
object DayOutlookPolicy {
    /** From this hour on the card shows tomorrow. / 从这个钟点起卡片改说明天。 */
    const val EVENING_HOUR = 18
    const val DAY_START_HOUR = 6
    const val DAY_END_HOUR = 22

    /** Same threshold as the umbrella everywhere else: below it MET is mostly drizzle or model noise. / 与别处带伞门槛一致：低于它基本是毛毛雨或模型噪声。 */
    const val UMBRELLA_MM = 0.2
    private const val HEAVY_HOURLY_MM = 4.0
    private const val HEAVY_DAY_MM = 10.0
    private const val THUNDER_CHANCE = 20.0
    private const val STRONG_GUST = 15.0
    private const val STRONG_WIND = 10.0
    private const val HEAT_C = 28.0
    private const val SUNSCREEN_UV = 3.0
    private const val LAYERS_SPREAD = 8.0
    private const val SLIPPERY_C = 1.0
    const val TIMELINE_STEP_HOURS = 3L

    fun outlook(points: List<ForecastPoint>, now: ZonedDateTime): DayOutlook? {
        val day = if (now.hour >= EVENING_HOUR) OutlookDay.TOMORROW else OutlookDay.TODAY
        val date = if (day == OutlookDay.TOMORROW) now.toLocalDate().plusDays(1) else now.toLocalDate()
        val windowStart = date.atTime(DAY_START_HOUR, 0).atZone(now.zone)
        val windowEnd = date.atTime(DAY_END_HOUR, 0).atZone(now.zone)
        // The hour under way still counts: at 14:20 the 14:00 step describes the next forty minutes.
        // 正在进行的那一小时也算：14:20 时 14:00 那一步说的是接下来四十分钟。
        val from = maxOf(windowStart, now.withMinute(0).withSecond(0).withNano(0))
        val hours = points
            .filter { it.symbol1h.isNotEmpty() || it.precipitation1h != null }
            .map { Instant.ofEpochMilli(it.time).atZone(now.zone) to it }
            .filter { (t, _) -> !t.isBefore(from) && t.isBefore(windowEnd) }
            .sortedBy { it.first }
        val temps = hours.mapNotNull { it.second.temperature }
        if (temps.isEmpty()) return null
        val feels = hours.mapNotNull { (_, p) -> p.apparentTemperature ?: p.temperature }
        val precip = hours.map { it.second.precipMm }
        val spells = rainSpells(hours)
        val wettest = hours.maxByOrNull { it.second.precipMm }?.takeIf { it.second.precipMm >= UMBRELLA_MM }
        val noon = date.atTime(12, 0).atZone(now.zone)
        val shown = wettest ?: hours.minByOrNull { (t, _) -> abs(t.toEpochSecond() - noon.toEpochSecond()) }
        val summary = Summary(
            symbols = hours.map { it.second.symbol1h },
            minTemp = temps.min(),
            maxTemp = temps.max(),
            feelsMin = feels.minOrNull() ?: temps.min(),
            feelsMax = feels.maxOrNull() ?: temps.max(),
            precipMm = precip.sum(),
            maxHourlyMm = precip.maxOrNull() ?: 0.0,
            thunderChance = hours.mapNotNull { it.second.thunderProbability1h }.maxOrNull(),
            maxWind = hours.mapNotNull { it.second.windSpeed }.maxOrNull() ?: 0.0,
            maxGust = hours.mapNotNull { it.second.windGust }.maxOrNull(),
            uvMax = hours.mapNotNull { it.second.uvIndex }.maxOrNull(),
            sunny = shown?.second?.symbol1h.orEmpty().let(::isSunny),
        )
        return DayOutlook(
            date = date,
            day = day,
            symbolCode = shown?.second?.symbol1h.orEmpty(),
            minTemp = summary.minTemp,
            maxTemp = summary.maxTemp,
            feelsMin = summary.feelsMin,
            feelsMax = summary.feelsMax,
            precipMm = summary.precipMm,
            precipChance = hours.mapNotNull { it.second.precipProbability1h }.maxOrNull()?.roundToInt(),
            rainSpells = spells,
            maxWind = summary.maxWind,
            maxGust = summary.maxGust,
            uvMax = summary.uvMax,
            clothing = clothing(summary.feelsMin),
            tips = tips(summary),
            timeline = hours
                .filter { (t, _) -> (t.hour - DAY_START_HOUR) % TIMELINE_STEP_HOURS == 0L }
                .map { (t, p) -> HourWeather(t, p.symbol1h, p.temperature, p.precipMm) },
        )
    }

    /**
     * People dress for the coldest part of the day they are out in, so the lowest feels-like temperature decides.
     * 人们按出门时最冷的时段穿衣，所以由最低体感温度决定。
     */
    fun clothing(feelsLike: Double): ClothingLevel = when {
        feelsLike >= 26 -> ClothingLevel.HOT
        feelsLike >= 20 -> ClothingLevel.WARM
        feelsLike >= 14 -> ClothingLevel.MILD
        feelsLike >= 7 -> ClothingLevel.COOL
        feelsLike >= 0 -> ClothingLevel.COLD
        else -> ClothingLevel.FREEZING
    }

    internal data class Summary(
        val symbols: List<String>,
        val minTemp: Double,
        val maxTemp: Double,
        val feelsMin: Double,
        val feelsMax: Double,
        val precipMm: Double,
        val maxHourlyMm: Double,
        val thunderChance: Double?,
        val maxWind: Double,
        val maxGust: Double?,
        val uvMax: Double?,
        val sunny: Boolean,
    )

    internal fun tips(s: Summary): List<WeatherTip> {
        val frozen = s.symbols.any { "snow" in it || "sleet" in it }
        val wet = s.maxHourlyMm >= UMBRELLA_MM
        val tips = buildList {
            if (s.symbols.any { "thunder" in it } || (s.thunderChance ?: 0.0) >= THUNDER_CHANCE) add(WeatherTip.THUNDER)
            if (s.maxHourlyMm >= HEAVY_HOURLY_MM || s.precipMm >= HEAVY_DAY_MM) add(WeatherTip.HEAVY_RAIN)
            // Snow is not an umbrella day; it gets its own tip. / 下雪不算带伞天，单独提示。
            if (wet && !frozen) add(WeatherTip.UMBRELLA)
            if (frozen) add(WeatherTip.SNOW)
            if (s.minTemp <= SLIPPERY_C && (s.precipMm > 0.0 || frozen)) add(WeatherTip.SLIPPERY)
            if ((s.maxGust ?: 0.0) >= STRONG_GUST || s.maxWind >= STRONG_WIND) add(WeatherTip.STRONG_WIND)
            if (s.maxTemp >= HEAT_C) add(WeatherTip.HEAT)
            // The UV figure assumes a clear sky, so it only counts when the day is actually sunny. / 紫外线数值假设晴空，只有真是晴天才算。
            if ((s.uvMax ?: 0.0) >= SUNSCREEN_UV && s.sunny) add(WeatherTip.SUNSCREEN)
            if (s.maxTemp - s.minTemp >= LAYERS_SPREAD) add(WeatherTip.LAYERS)
        }
        if (tips.isEmpty() && !wet && s.feelsMin >= 14 && s.feelsMax < 26) return listOf(WeatherTip.PLEASANT)
        return tips
    }

    private fun rainSpells(hours: List<Pair<ZonedDateTime, ForecastPoint>>): List<RainSpell> {
        val spells = mutableListOf<RainSpell>()
        var start: ZonedDateTime? = null
        var last: ZonedDateTime? = null
        for ((t, p) in hours) {
            val raining = p.precipMm >= UMBRELLA_MM
            // A gap in the data ends a spell too. / 数据中断也结束一段降雨。
            if (start != null && (!raining || last?.plusHours(1) != t)) {
                spells += RainSpell(start, last!!.plusHours(1))
                start = null
            }
            if (raining && start == null) start = t
            last = t
        }
        if (start != null && last != null) spells += RainSpell(start, last.plusHours(1))
        return spells
    }

    private fun isSunny(symbol: String): Boolean = symbol.substringBefore('_') in setOf("clearsky", "fair", "partlycloudy")

    private val ForecastPoint.precipMm: Double get() = precipitation1h ?: 0.0
}
