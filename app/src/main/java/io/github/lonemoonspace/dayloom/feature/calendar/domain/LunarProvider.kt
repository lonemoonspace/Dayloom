package io.github.lonemoonspace.dayloom.feature.calendar.domain

import java.time.LocalDate

/**
 * A date in the Chinese lunar calendar. [cycleYear] numbers the 60-year cycle with 1 = jia-zi (1984, 2044); it switches at
 * the Spring Festival, like the lunar date itself.
 * 中国农历中的一天。[cycleYear] 是 60 年周期序号，1 = 甲子（1984、2044 年）；与农历日期一样在春节切换。
 */
data class LunarDate(val month: Int, val day: Int, val isLeapMonth: Boolean, val cycleYear: Int) {
    /** 0–9: jia … gui. / 0–9：甲 … 癸。 */
    val stem: Int get() = (cycleYear - 1) % 10

    /** 0–11: zi … hai; also the zodiac animal (rat … pig). / 0–11：子 … 亥；也是生肖（鼠 … 猪）。 */
    val branch: Int get() = (cycleYear - 1) % 12
}

/** The 24 solar terms in calendar-year order; the i-th always falls in month i / 2 + 1. / 二十四节气，按公历年内先后排列；第 i 个总在第 i / 2 + 1 月。 */
enum class SolarTerm {
    MINOR_COLD, MAJOR_COLD, START_OF_SPRING, RAIN_WATER, AWAKENING_OF_INSECTS, SPRING_EQUINOX,
    CLEAR_AND_BRIGHT, GRAIN_RAIN, START_OF_SUMMER, GRAIN_BUDS, GRAIN_IN_EAR, SUMMER_SOLSTICE,
    MINOR_HEAT, MAJOR_HEAT, START_OF_AUTUMN, END_OF_HEAT, WHITE_DEW, AUTUMN_EQUINOX,
    COLD_DEW, FROSTS_DESCENT, START_OF_WINTER, MINOR_SNOW, MAJOR_SNOW, WINTER_SOLSTICE,
}

data class SolarTermDay(val term: SolarTerm, val date: LocalDate)

/**
 * Lunar calendar data by Gregorian date. The input is the user's local date, the answer is what a calendar in China shows
 * for that same date (China Standard Time), so it matches calendars there. An interface so the implementation can change
 * without touching the module (design §11.5).
 * 按公历日期查询农历。输入是用户当地的公历日期，输出的是中国（北京时间）日历上同一公历日期显示的内容，与国内日历一致。
 * 抽成接口，换实现时不用改模块（设计文档 §11.5）。
 */
interface LunarProvider {
    /** Null outside the supported range. / 超出支持范围时为 null。 */
    fun lunarDate(date: LocalDate): LunarDate?

    /** The solar term starting on [date], if any. / [date] 当天交节的节气，没有则为 null。 */
    fun solarTermOn(date: LocalDate): SolarTerm?

    /** The first solar term strictly after [date]. / 严格晚于 [date] 的第一个节气。 */
    fun nextSolarTerm(date: LocalDate): SolarTermDay?
}
