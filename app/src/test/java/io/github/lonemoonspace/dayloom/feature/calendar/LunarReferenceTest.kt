package io.github.lonemoonspace.dayloom.feature.calendar

import io.github.lonemoonspace.dayloom.feature.calendar.data.LunarJavaProvider
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Day-by-day check of [LunarJavaProvider] against the Hong Kong Observatory tables. Runs only through
 * `scripts/verify_lunar.py`, which downloads the tables to a temporary file and passes it in DAYLOOM_LUNAR_REFERENCE; the
 * Observatory data is never committed (its terms do not allow redistribution, design §19). Skipped everywhere else.
 * [LunarJavaProvider] 与香港天文台对照表的逐日比对。只经 `scripts/verify_lunar.py` 运行：脚本把对照表下载到临时文件，
 * 通过 DAYLOOM_LUNAR_REFERENCE 传进来；天文台数据永不入库（其条款不允许再分发，见设计文档 §19）。其他场合一律跳过。
 */
class LunarReferenceTest {

    @Test
    fun `lunar-java matches the observatory tables`() {
        val path = System.getenv(ENV).orEmpty()
        assumeTrue("set $ENV (see scripts/verify_lunar.py)", path.isNotEmpty())
        val lunar = LunarJavaProvider()
        val mismatches = mutableListOf<String>()
        var rows = 0
        // Each line: yyyy-mm-dd,month,day,leap(0/1),term index or -1. / 每行：日期,月,日,是否闰月(0/1),节气序号或 -1。
        File(path).forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val (date, month, day, leap, term) = line.split(',')
            val at = LocalDate.parse(date)
            val actual = lunar.lunarDate(at)
            val expected = "$month/$day${if (leap == "1") " leap" else ""} term=$term"
            val got = "${actual?.month}/${actual?.day}${if (actual?.isLeapMonth == true) " leap" else ""} term=${lunar.solarTermOn(at)?.ordinal ?: -1}"
            if (expected != got && !isKnownDifference(at)) mismatches += "$date: observatory $expected, lunar-java $got"
            rows++
        }
        println("checked $rows days, ${mismatches.size} mismatches")
        assertEquals(mismatches.take(MAX_REPORTED).joinToString("\n"), 0, mismatches.size)
    }

    /**
     * The 42 days (of 73,029 in 1901–2100) where the two disagree, last checked 2026-10-09; none fall in 1980–2056.
     * Six solar terms land a day apart (each term's day and its neighbour), and in 2057 the 9th month starts on 29 September
     * in lunar-java instead of 28 September: that new moon falls within minutes of midnight China Standard Time. Any other
     * difference is new and fails the test.
     * 两者不一致的 42 天（1901–2100 年共 73,029 天），2026-10-09 核对；1980–2056 年一天都没有。
     * 六个节气差一天（节气当天及其相邻一天），以及 2057 年九月初一 lunar-java 算在 9 月 29 日而不是 28 日：那次朔日距北京时间
     * 零点只有几分钟。其他任何差异都是新出现的，测试失败。
     */
    private fun isKnownDifference(date: LocalDate): Boolean =
        date in KNOWN_TERM_DAYS || date in LocalDate.of(2057, 9, 28)..LocalDate.of(2057, 10, 27)

    private companion object {
        const val ENV = "DAYLOOM_LUNAR_REFERENCE"

        val KNOWN_TERM_DAYS: Set<LocalDate> = listOf(
            "1912-11-22", "1912-11-23", "1913-09-23", "1913-09-24", "1917-12-07", "1917-12-08",
            "1927-09-08", "1927-09-09", "1928-06-21", "1928-06-22", "1979-01-20", "1979-01-21",
        ).mapTo(mutableSetOf(), LocalDate::parse)
        const val MAX_REPORTED = 40
    }
}
