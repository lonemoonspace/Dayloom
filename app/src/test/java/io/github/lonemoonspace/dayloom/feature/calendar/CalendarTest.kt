package io.github.lonemoonspace.dayloom.feature.calendar

import io.github.lonemoonspace.dayloom.feature.calendar.data.LunarJavaProvider
import io.github.lonemoonspace.dayloom.feature.calendar.domain.Holiday
import io.github.lonemoonspace.dayloom.feature.calendar.domain.HolidayCountdown
import io.github.lonemoonspace.dayloom.feature.calendar.domain.HolidayCountry
import io.github.lonemoonspace.dayloom.feature.calendar.domain.HolidayPolicy
import io.github.lonemoonspace.dayloom.feature.calendar.domain.LunarDate
import io.github.lonemoonspace.dayloom.feature.calendar.domain.SolarTerm
import io.github.lonemoonspace.dayloom.feature.calendar.domain.SolarTermDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Holiday rules run against the real lunar-java provider: it is plain Java, and the lunar dates are part of what is tested.
 * 节日规则直接用真实的 lunar-java 实现测试：它是纯 Java，而且农历日期本身就是被测内容的一部分。
 */
class CalendarTest {

    private val lunar = LunarJavaProvider()
    private val both = setOf(HolidayCountry.CN, HolidayCountry.NO)

    private fun on(y: Int, m: Int, d: Int, countries: Set<HolidayCountry> = both) = HolidayPolicy.on(LocalDate.of(y, m, d), countries, lunar)

    @Test
    fun `lunar dates, leap months and the stem-branch year`() {
        // 2025-10-06 is 8/15 (Mid-Autumn) of yi-si, year of the Snake. / 2025-10-06 是乙巳蛇年八月十五（中秋）。
        assertEquals(LunarDate(8, 15, false, 42), lunar.lunarDate(LocalDate.of(2025, 10, 6)))
        assertEquals(1, lunar.lunarDate(LocalDate.of(2025, 10, 6))!!.stem)
        assertEquals(5, lunar.lunarDate(LocalDate.of(2025, 10, 6))!!.branch)
        // 2025 has a leap 6th month starting on 2025-07-25 (Hong Kong Observatory table). / 2025 年闰六月，初一是 2025-07-25（香港天文台表）。
        assertEquals(LunarDate(6, 1, true, 42), lunar.lunarDate(LocalDate.of(2025, 7, 25)))
        // The year switches at the Spring Festival, not on 1 January: 2026-02-17 starts bing-wu. / 年份在春节而不是 1 月 1 日切换：2026-02-17 进入丙午年。
        assertEquals(42, lunar.lunarDate(LocalDate.of(2026, 2, 16))!!.cycleYear)
        assertEquals(LunarDate(1, 1, false, 43), lunar.lunarDate(LocalDate.of(2026, 2, 17)))
        assertNull(lunar.lunarDate(LocalDate.of(2101, 6, 1)))
    }

    @Test
    fun `solar terms on their day and the next one`() {
        assertEquals(SolarTerm.CLEAR_AND_BRIGHT, lunar.solarTermOn(LocalDate.of(2025, 4, 4)))
        assertEquals(SolarTerm.WINTER_SOLSTICE, lunar.solarTermOn(LocalDate.of(2025, 12, 21)))
        assertNull(lunar.solarTermOn(LocalDate.of(2025, 4, 5)))
        assertEquals(SolarTermDay(SolarTerm.GRAIN_RAIN, LocalDate.of(2025, 4, 20)), lunar.nextSolarTerm(LocalDate.of(2025, 4, 4)))
        // After the winter solstice comes next year's Minor Cold. / 冬至之后是次年小寒。
        assertEquals(SolarTermDay(SolarTerm.MINOR_COLD, LocalDate.of(2026, 1, 5)), lunar.nextSolarTerm(LocalDate.of(2025, 12, 21)))
    }

    @Test
    fun `same-named holidays of both countries appear once`() {
        assertEquals(listOf(Holiday.NEW_YEAR), on(2026, 1, 1))
        assertEquals(listOf(Holiday.LABOUR_DAY), on(2026, 5, 1))
    }

    @Test
    fun `chinese and norwegian holidays are merged`() {
        assertEquals(listOf(Holiday.NATIONAL_DAY), on(2026, 10, 1))
        assertEquals(listOf(Holiday.SPRING_FESTIVAL), on(2024, 2, 10))
        assertEquals(listOf(Holiday.NEW_YEARS_EVE), on(2024, 2, 9))
        assertEquals(listOf(Holiday.CONSTITUTION_DAY), on(2026, 5, 17))
        assertEquals(listOf(Holiday.EASTER_SUNDAY), on(2024, 3, 31))
        assertEquals(listOf(Holiday.QINGMING), on(2025, 4, 4))
        assertEquals(emptyList<Holiday>(), on(2026, 9, 16))
    }

    @Test
    fun `qingming is left out, also from the countdown, when the lunar block shows solar terms`() {
        val cn = setOf(HolidayCountry.CN)
        assertEquals(emptyList<Holiday>(), HolidayPolicy.on(LocalDate.of(2025, 4, 4), cn, lunar, solarTermsShown = true))
        assertEquals(
            HolidayCountdown(listOf(Holiday.QINGMING), LocalDate.of(2025, 4, 4), 3),
            HolidayPolicy.upcoming(LocalDate.of(2025, 4, 1), cn, lunar),
        )
        assertNull(HolidayPolicy.upcoming(LocalDate.of(2025, 4, 1), cn, lunar, solarTermsShown = true))
        // Other holidays stay. / 其他节日照常显示。
        assertEquals(listOf(Holiday.NATIONAL_DAY), HolidayPolicy.on(LocalDate.of(2026, 10, 1), cn, lunar, solarTermsShown = true))
    }

    @Test
    fun `only the selected countries count`() {
        assertEquals(emptyList<Holiday>(), on(2026, 5, 17, setOf(HolidayCountry.CN)))
        assertEquals(emptyList<Holiday>(), on(2026, 10, 1, setOf(HolidayCountry.NO)))
        assertEquals(emptyList<Holiday>(), on(2026, 1, 1, emptySet()))
    }

    @Test
    fun `a leap month repeats no festival`() {
        // 2025-08-08 is leap 6/15 by the lunar calendar; only real 7/15 is the Ghost Festival. / 2025-08-08 是闰六月十五，不是中元节。
        assertEquals(LunarDate(6, 15, true, 42), lunar.lunarDate(LocalDate.of(2025, 8, 8)))
        assertEquals(emptyList<Holiday>(), on(2025, 8, 8))
    }

    @Test
    fun `norwegian holidays around easter 2026`() {
        assertEquals(LocalDate.of(2024, 3, 31), HolidayPolicy.easterSunday(2024))
        assertEquals(LocalDate.of(2025, 4, 20), HolidayPolicy.easterSunday(2025))
        assertEquals(LocalDate.of(2026, 4, 5), HolidayPolicy.easterSunday(2026))
        val no = setOf(HolidayCountry.NO)
        assertEquals(listOf(Holiday.MAUNDY_THURSDAY), on(2026, 4, 2, no))
        assertEquals(listOf(Holiday.GOOD_FRIDAY), on(2026, 4, 3, no))
        assertEquals(listOf(Holiday.EASTER_MONDAY), on(2026, 4, 6, no))
        assertEquals(listOf(Holiday.ASCENSION_DAY), on(2026, 5, 14, no))
        assertEquals(listOf(Holiday.WHIT_SUNDAY), on(2026, 5, 24, no))
        assertEquals(listOf(Holiday.WHIT_MONDAY), on(2026, 5, 25, no))
        assertEquals(listOf(Holiday.BOXING_DAY), on(2026, 12, 26, no))
    }

    @Test
    fun `countdown starts seven days ahead and picks the nearest`() {
        assertEquals(
            HolidayCountdown(listOf(Holiday.CHRISTMAS_DAY), LocalDate.of(2026, 12, 25), 7),
            HolidayPolicy.upcoming(LocalDate.of(2026, 12, 18), both, lunar),
        )
        assertNull(HolidayPolicy.upcoming(LocalDate.of(2026, 12, 17), both, lunar))
        assertEquals(
            HolidayCountdown(listOf(Holiday.ASCENSION_DAY), LocalDate.of(2026, 5, 14), 4),
            HolidayPolicy.upcoming(LocalDate.of(2026, 5, 10), both, lunar),
        )
    }

    @Test
    fun `countdown skips today and lists every holiday on the day`() {
        assertEquals(
            HolidayCountdown(listOf(Holiday.SPRING_FESTIVAL), LocalDate.of(2026, 2, 17), 1),
            HolidayPolicy.upcoming(LocalDate.of(2026, 2, 16), both, lunar),
        )
        // 2020-10-01 was both National Day and Mid-Autumn. / 2020-10-01 国庆节与中秋节同一天。
        assertEquals(
            HolidayCountdown(listOf(Holiday.NATIONAL_DAY, Holiday.MID_AUTUMN), LocalDate.of(2020, 10, 1), 3),
            HolidayPolicy.upcoming(LocalDate.of(2020, 9, 28), both, lunar),
        )
    }
}
