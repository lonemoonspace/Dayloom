package io.github.lonemoonspace.dayloom.feature.calendar.data

import com.nlf.calendar.Solar
import io.github.lonemoonspace.dayloom.feature.calendar.domain.LunarDate
import io.github.lonemoonspace.dayloom.feature.calendar.domain.LunarProvider
import io.github.lonemoonspace.dayloom.feature.calendar.domain.SolarTerm
import io.github.lonemoonspace.dayloom.feature.calendar.domain.SolarTermDay
import java.time.LocalDate

/**
 * [LunarProvider] backed by lunar-java (`cn.6tail:lunar`, MIT). It computes new moons and solar terms with the high-precision
 * Shouxing (sxwnl) algorithms in China Standard Time; `scripts/verify_lunar.py` checks it day by day against the Hong Kong
 * Observatory tables on a developer machine.
 * 基于 lunar-java（`cn.6tail:lunar`，MIT）的 [LunarProvider]。它用寿星万年历的高精度算法按北京时间计算朔日与节气；
 * `scripts/verify_lunar.py` 在开发机上与香港天文台的对照表逐日比对。
 */
class LunarJavaProvider : LunarProvider {

    override fun lunarDate(date: LocalDate): LunarDate? {
        if (date.year !in SUPPORTED_YEARS) return null
        val lunar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunar
        // lunar-java marks a leap month with a negative month number. / lunar-java 用负的月份数表示闰月。
        return LunarDate(
            month = kotlin.math.abs(lunar.month),
            day = lunar.day,
            isLeapMonth = lunar.month < 0,
            cycleYear = Math.floorMod(lunar.year - 4, 60) + 1,
        )
    }

    override fun solarTermOn(date: LocalDate): SolarTerm? {
        if (date.year !in SUPPORTED_YEARS) return null
        return BY_NAME[Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunar.jieQi]
    }

    override fun nextSolarTerm(date: LocalDate): SolarTermDay? =
        // Terms are 14–16 days apart, so the next one is always within this range. / 节气相隔 14–16 天，下一个一定在这个范围内。
        (1L..MAX_TERM_GAP_DAYS).asSequence()
            .map { date.plusDays(it) }
            .firstNotNullOfOrNull { day -> solarTermOn(day)?.let { SolarTermDay(it, day) } }

    private companion object {
        /** The range the Observatory tables cover, which is what the library has been checked against. / 天文台对照表覆盖的范围，也就是该库经过核对的范围。 */
        val SUPPORTED_YEARS = 1901..2100

        const val MAX_TERM_GAP_DAYS = 20L

        /** lunar-java returns the simplified Chinese name of the term. / lunar-java 返回节气的简体中文名。 */
        val BY_NAME: Map<String, SolarTerm> = listOf(
            "小寒", "大寒", "立春", "雨水", "惊蛰", "春分",
            "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
            "小暑", "大暑", "立秋", "处暑", "白露", "秋分",
            "寒露", "霜降", "立冬", "小雪", "大雪", "冬至",
        ).zip(SolarTerm.entries).toMap()
    }
}
