package io.github.lonemoonspace.dayloom.feature.calendar.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.ui.StatusChip
import io.github.lonemoonspace.dayloom.core.ui.rememberMinuteTick
import io.github.lonemoonspace.dayloom.core.ui.rememberPatternFormatter
import io.github.lonemoonspace.dayloom.core.ui.rememberTimeFormatter
import io.github.lonemoonspace.dayloom.core.ui.theme.statusColors
import io.github.lonemoonspace.dayloom.feature.calendar.domain.Holiday
import io.github.lonemoonspace.dayloom.feature.calendar.domain.HolidayCountry
import io.github.lonemoonspace.dayloom.feature.calendar.domain.HolidayPolicy
import io.github.lonemoonspace.dayloom.feature.calendar.domain.LunarDate
import io.github.lonemoonspace.dayloom.feature.calendar.domain.LunarProvider
import io.github.lonemoonspace.dayloom.feature.calendar.domain.SolarTerm
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

/**
 * The clock header (no card around it): time, date, week number and holidays on the left; lunar date, stem-branch year and
 * solar term on the right.
 * 时钟页头（不套卡片）：左侧时间、日期、周数与节日，右侧农历日期、干支年与节气。
 */
@Composable
internal fun CalendarHeader(showLunar: Boolean, countries: Set<HolidayCountry>, lunar: LunarProvider) {
    val now = rememberMinuteTick()
    val date = now.toLocalDate()
    val termToday = remember(date, showLunar) { if (showLunar) lunar.solarTermOn(date) else null }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = now.format(rememberTimeFormatter()),
                fontSize = 36.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            // ISO 8601 weeks (Monday first), as Norwegian calendars count them. / 周数按 ISO 8601（周一为一周开始），与挪威日历一致。
            Text(
                text = stringResource(
                    R.string.calendar_date_week,
                    date.format(rememberPatternFormatter("MMMMEEEEd")),
                    date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (countries.isNotEmpty()) HolidayRow(date, countries, lunar, hideQingming = termToday == SolarTerm.CLEAR_AND_BRIGHT)
        }
        if (showLunar) {
            Spacer(Modifier.width(12.dp))
            LunarBlock(date, lunar, termToday)
        }
    }
}

/**
 * Today's holidays as a red chip; the next one as a countdown when it is within [HolidayPolicy.COUNTDOWN_DAYS]. Takes no
 * space when there is neither. Qingming is hidden on the day when the lunar block already shows it as today's solar term.
 * 当天的节日显示为红色标签；下一个节日在 [HolidayPolicy.COUNTDOWN_DAYS] 天以内时显示倒计时。两者都没有时不占位。
 * 清明当天如果农历区已作为节气显示，这里不重复。
 */
@Composable
private fun HolidayRow(date: LocalDate, countries: Set<HolidayCountry>, lunar: LunarProvider, hideQingming: Boolean) {
    val today = remember(date, countries, hideQingming) {
        HolidayPolicy.on(date, countries, lunar).filterNot { hideQingming && it == Holiday.QINGMING }
    }
    val upcoming = remember(date, countries) { HolidayPolicy.upcoming(date, countries, lunar) }
    if (today.isEmpty() && upcoming == null) return
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (today.isNotEmpty()) {
            StatusChip(today.map { stringResource(it.nameRes()) }.joinToString(" · "), MaterialTheme.statusColors.red)
            if (upcoming != null) Spacer(Modifier.width(6.dp))
        }
        upcoming?.let { next ->
            Text(
                text = countdown(next.holidays.map { stringResource(it.nameRes()) }.joinToString(" · "), next.daysUntil),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Lunar date, stem-branch year and solar term, as a calendar in China shows them for the same Gregorian date.
 * 农历日期、干支年与节气，与国内日历在同一公历日期显示的内容一致。
 */
@Composable
private fun LunarBlock(date: LocalDate, lunar: LunarProvider, termToday: SolarTerm?) {
    val day = remember(date) { lunar.lunarDate(date) } ?: return
    val next = remember(date) { if (termToday == null) lunar.nextSolarTerm(date) else null }
    val terms = stringArrayResource(R.array.calendar_solar_terms)
    Column(horizontalAlignment = Alignment.End) {
        Text(text = lunarDateText(day), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val termText = termToday?.let { stringResource(R.string.calendar_solar_term_today, terms[it.ordinal]) }
            ?: next?.let { countdown(terms[it.term.ordinal], ChronoUnit.DAYS.between(date, it.date).toInt()) }
        Row {
            Text(
                text = lunarYearText(day) + if (termText != null) " · " else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            termText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (termToday != null) MaterialTheme.statusColors.green else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (termToday != null) FontWeight.SemiBold else null,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun lunarDateText(day: LunarDate): String {
    val month = stringArrayResource(R.array.calendar_lunar_months)[day.month - 1]
    val monthText = if (day.isLeapMonth) stringResource(R.string.calendar_lunar_leap_month, month) else month
    return stringResource(R.string.calendar_lunar_date, monthText, stringArrayResource(R.array.calendar_lunar_days)[day.day - 1])
}

@Composable
private fun lunarYearText(day: LunarDate): String = stringResource(
    R.string.calendar_lunar_year,
    stringArrayResource(R.array.calendar_heavenly_stems)[day.stem],
    stringArrayResource(R.array.calendar_earthly_branches)[day.branch],
    stringArrayResource(R.array.calendar_zodiac)[day.branch],
)

/** Shared by holidays and solar terms: "tomorrow" for one day, "in N days" otherwise. / 节日与节气共用：1 天写「明天」，其余写「还有 N 天」。 */
@Composable
private fun countdown(name: String, days: Int): String =
    if (days == 1) stringResource(R.string.calendar_countdown_tomorrow, name) else pluralStringResource(R.plurals.calendar_countdown_days, days, name, days)

@StringRes
private fun Holiday.nameRes(): Int = when (this) {
    Holiday.NEW_YEAR -> R.string.holiday_new_year
    Holiday.LABOUR_DAY -> R.string.holiday_labour_day
    Holiday.NEW_YEARS_EVE -> R.string.holiday_new_years_eve
    Holiday.SPRING_FESTIVAL -> R.string.holiday_spring_festival
    Holiday.LANTERN -> R.string.holiday_lantern
    Holiday.DRAGON_HEAD -> R.string.holiday_dragon_head
    Holiday.QINGMING -> R.string.holiday_qingming
    Holiday.DRAGON_BOAT -> R.string.holiday_dragon_boat
    Holiday.QIXI -> R.string.holiday_qixi
    Holiday.GHOST -> R.string.holiday_ghost
    Holiday.MID_AUTUMN -> R.string.holiday_mid_autumn
    Holiday.DOUBLE_NINTH -> R.string.holiday_double_ninth
    Holiday.NATIONAL_DAY -> R.string.holiday_national_day
    Holiday.LABA -> R.string.holiday_laba
    Holiday.MAUNDY_THURSDAY -> R.string.holiday_maundy_thursday
    Holiday.GOOD_FRIDAY -> R.string.holiday_good_friday
    Holiday.EASTER_SUNDAY -> R.string.holiday_easter_sunday
    Holiday.EASTER_MONDAY -> R.string.holiday_easter_monday
    Holiday.CONSTITUTION_DAY -> R.string.holiday_constitution_day
    Holiday.ASCENSION_DAY -> R.string.holiday_ascension_day
    Holiday.WHIT_SUNDAY -> R.string.holiday_whit_sunday
    Holiday.WHIT_MONDAY -> R.string.holiday_whit_monday
    Holiday.CHRISTMAS_DAY -> R.string.holiday_christmas_day
    Holiday.BOXING_DAY -> R.string.holiday_boxing_day
}
