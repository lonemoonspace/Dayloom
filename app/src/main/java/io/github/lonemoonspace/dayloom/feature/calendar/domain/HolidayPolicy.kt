package io.github.lonemoonspace.dayloom.feature.calendar.domain

import java.time.LocalDate

/** Countries whose holidays can be shown; stored by name in the calendar settings. / 可显示其节假日的国家；按名称存在日历设置里。 */
enum class HolidayCountry { CN, NO }

/**
 * A holiday by meaning, not by country: New Year's Day and Labour Day are one value each, so the two countries' same-name,
 * same-day holidays merge into one entry.
 * 按含义而不是按国家区分的节日：元旦、劳动节各只有一个值，所以两国同名同日的节日自然合并为一条。
 */
enum class Holiday {
    NEW_YEAR, LABOUR_DAY,

    // China: statutory holidays and traditional lunar festivals. / 中国：法定节假日与农历传统节日。
    NEW_YEARS_EVE, SPRING_FESTIVAL, LANTERN, DRAGON_HEAD, QINGMING, DRAGON_BOAT, QIXI, GHOST, MID_AUTUMN, DOUBLE_NINTH,
    NATIONAL_DAY, LABA,

    // Norway: public holidays (offentlige fridager). / 挪威：法定节假日。
    MAUNDY_THURSDAY, GOOD_FRIDAY, EASTER_SUNDAY, EASTER_MONDAY, CONSTITUTION_DAY, ASCENSION_DAY, WHIT_SUNDAY, WHIT_MONDAY,
    CHRISTMAS_DAY, BOXING_DAY,
}

/** The next holiday, [daysUntil] (≥ 1) days away; all of them when several fall on that day. / 距今 [daysUntil]（≥ 1）天的下一个节日，同一天有多个时全部列出。 */
data class HolidayCountdown(val holidays: List<Holiday>, val date: LocalDate, val daysUntil: Int)

/**
 * Holidays of the selected countries, merged; the decision parts of the original `Holidays`, `NorwayHolidays` and the lunar
 * festival table, with names left to resources.
 * 所选国家的节日合并为一份；移植自原项目 `Holidays`、`NorwayHolidays` 与农历节日表的判定部分，名称交给资源。
 */
object HolidayPolicy {

    /** A countdown starts when the next holiday is at most this many days away. / 下一个节日不超过这么多天时开始倒计时。 */
    const val COUNTDOWN_DAYS = 7

    /**
     * Holidays on [date]. With [solarTermsShown] the holidays that are also solar terms (Qingming) are left out, on the day
     * and in the countdown, because the lunar block already shows them as solar terms.
     * [date] 当天的节日。[solarTermsShown] 为 true 时去掉同时也是节气的节日（清明），当天与倒计时都去掉，因为农历区已把它作为节气显示。
     */
    fun on(
        date: LocalDate,
        countries: Set<HolidayCountry>,
        lunar: LunarProvider,
        solarTermsShown: Boolean = false,
    ): List<Holiday> = buildList {
        if (HolidayCountry.CN in countries) addAll(china(date, lunar))
        if (HolidayCountry.NO in countries) norway(date)?.let(::add)
    }.distinct().filterNot { solarTermsShown && it in SOLAR_TERM_HOLIDAYS }

    /** The first holiday strictly after [today] and at most [withinDays] away. / 严格晚于 [today]、且不超过 [withinDays] 天的第一个节日。 */
    fun upcoming(
        today: LocalDate,
        countries: Set<HolidayCountry>,
        lunar: LunarProvider,
        solarTermsShown: Boolean = false,
        withinDays: Int = COUNTDOWN_DAYS,
    ): HolidayCountdown? = (1..withinDays).firstNotNullOfOrNull { days ->
        val date = today.plusDays(days.toLong())
        on(date, countries, lunar, solarTermsShown).takeIf { it.isNotEmpty() }?.let { HolidayCountdown(it, date, days) }
    }

    /**
     * Fixed-date statutory holidays first, then the lunar festival. A statutory holiday occasionally coincides with a lunar
     * festival (National Day and Mid-Autumn in 2020); both are shown.
     * 按公历日期定的法定节假日在前，农历节日在后。两者偶尔同一天（2020 年国庆节与中秋节），都显示。
     */
    private fun china(date: LocalDate, lunar: LunarProvider): List<Holiday> {
        val fixed = CHINA_FIXED[date.monthValue to date.dayOfMonth]
        val qingming = Holiday.QINGMING.takeIf { lunar.solarTermOn(date) == SolarTerm.CLEAR_AND_BRIGHT }
        val day = lunar.lunarDate(date) ?: return listOfNotNull(fixed, qingming)
        // The same month and day in a leap month is no festival (leap 5/5 is not Dragon Boat). / 闰月里的同月同日不过节（闰五月初五不是端午）。
        val festival = if (!day.isLeapMonth) LUNAR_FESTIVALS[day.month to day.day] else null
        // The 12th month has 29 or 30 days, so New Year's Eve is "tomorrow is the Spring Festival". / 腊月可能 29 天也可能 30 天，所以除夕看「明天是不是春节」。
        val eve = if (festival == null) {
            val tomorrow = lunar.lunarDate(date.plusDays(1))
            Holiday.NEW_YEARS_EVE.takeIf { tomorrow != null && tomorrow.month == 1 && tomorrow.day == 1 && !tomorrow.isLeapMonth }
        } else {
            null
        }
        return listOfNotNull(fixed, festival ?: eve, qingming)
    }

    private fun norway(date: LocalDate): Holiday? {
        NORWAY_FIXED[date.monthValue to date.dayOfMonth]?.let { return it }
        val easter = easterSunday(date.year)
        return when (date) {
            easter.minusDays(3) -> Holiday.MAUNDY_THURSDAY
            easter.minusDays(2) -> Holiday.GOOD_FRIDAY
            easter -> Holiday.EASTER_SUNDAY
            easter.plusDays(1) -> Holiday.EASTER_MONDAY
            easter.plusDays(39) -> Holiday.ASCENSION_DAY
            easter.plusDays(49) -> Holiday.WHIT_SUNDAY
            easter.plusDays(50) -> Holiday.WHIT_MONDAY
            else -> null
        }
    }

    /**
     * Gregorian Easter Sunday (first Sunday after the first full moon on or after the equinox), by the Meeus/Jones/Butcher
     * algorithm; valid from 1583, no table.
     * 公历复活节（春分月圆后的第一个周日），用 Meeus/Jones/Butcher 算法，1583 年起成立，不查表。
     */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    /** Holidays that are also solar terms. / 同时也是节气的节日。 */
    private val SOLAR_TERM_HOLIDAYS = setOf(Holiday.QINGMING)

    private val CHINA_FIXED = mapOf(
        (1 to 1) to Holiday.NEW_YEAR,
        (5 to 1) to Holiday.LABOUR_DAY,
        (10 to 1) to Holiday.NATIONAL_DAY,
    )

    private val LUNAR_FESTIVALS = mapOf(
        (1 to 1) to Holiday.SPRING_FESTIVAL,
        (1 to 15) to Holiday.LANTERN,
        (2 to 2) to Holiday.DRAGON_HEAD,
        (5 to 5) to Holiday.DRAGON_BOAT,
        (7 to 7) to Holiday.QIXI,
        (7 to 15) to Holiday.GHOST,
        (8 to 15) to Holiday.MID_AUTUMN,
        (9 to 9) to Holiday.DOUBLE_NINTH,
        (12 to 8) to Holiday.LABA,
    )

    private val NORWAY_FIXED = mapOf(
        (1 to 1) to Holiday.NEW_YEAR,
        (5 to 1) to Holiday.LABOUR_DAY,
        (5 to 17) to Holiday.CONSTITUTION_DAY,
        (12 to 25) to Holiday.CHRISTMAS_DAY,
        (12 to 26) to Holiday.BOXING_DAY,
    )
}
