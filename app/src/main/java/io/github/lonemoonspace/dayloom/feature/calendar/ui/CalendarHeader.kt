package io.github.lonemoonspace.dayloom.feature.calendar.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
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
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

/**
 * The clock header (no card around it), in two aligned rows: the time with the solar term on the right, then the date and
 * week with the stem-branch year and lunar date on the right; holidays below.
 * 时钟页头（不套卡片），两行对齐：时间与右侧的节气；日期、周数与右侧的干支年和农历日期；节日在下面。
 */
@Composable
internal fun CalendarHeader(showLunar: Boolean, countries: Set<HolidayCountry>, lunar: LunarProvider) {
    val now = rememberMinuteTick()
    val date = now.toLocalDate()
    val day = remember(date, showLunar) { if (showLunar) lunar.lunarDate(date) else null }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = now.format(rememberTimeFormatter()),
                fontSize = 44.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.weight(1f))
            if (day != null) SolarTermText(date, lunar, Modifier.alignByBaseline())
        }
        Row(Modifier.fillMaxWidth()) {
            // ISO 8601 weeks (Monday first), as Norwegian calendars count them. / 周数按 ISO 8601（周一为一周开始），与挪威日历一致。
            // Unweighted, so the date is measured first and the lunar text gives way on a narrow screen.
            // 不加权重，日期先测量，窄屏上让农历文字让位。
            Text(
                text = stringResource(
                    R.string.calendar_date_week,
                    date.format(rememberPatternFormatter("MMMMEEEEd")),
                    date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                ),
                style = MaterialTheme.typography.bodyMedium,
                // The header sits on the page, not in a card, so it names its colour instead of inheriting one.
                // 页头直接放在页面上而不是卡片里，所以明确指定颜色，不依赖继承。
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            if (day != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.calendar_lunar_line, lunarYearText(day), lunarDateText(day)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline(),
                )
            }
        }
        if (countries.isNotEmpty()) HolidayRow(date, countries, lunar, solarTermsShown = showLunar)
    }
}

/**
 * Today's solar term, highlighted, or a countdown to the next one.
 * 当天的节气（高亮），否则是下一个节气的倒计时。
 */
@Composable
private fun SolarTermText(date: LocalDate, lunar: LunarProvider, modifier: Modifier) {
    val termToday = remember(date) { lunar.solarTermOn(date) }
    val next = remember(date) { if (termToday == null) lunar.nextSolarTerm(date) else null }
    val terms = stringArrayResource(R.array.calendar_solar_terms)
    val text = termToday?.let { stringResource(R.string.calendar_solar_term_today, terms[it.ordinal]) }
        ?: next?.let { countdown(terms[it.term.ordinal], ChronoUnit.DAYS.between(date, it.date).toInt()) }
        ?: return
    // A pill like the other statuses; today's term is highlighted. / 与其他状态一样是胶囊；当天的节气高亮。
    val color = if (termToday != null) MaterialTheme.statusColors.green else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * Today's holidays as a red chip; the next one as a countdown when it is within [HolidayPolicy.COUNTDOWN_DAYS]. Takes no
 * space when there is neither.
 * 当天的节日显示为红色标签；下一个节日在 [HolidayPolicy.COUNTDOWN_DAYS] 天以内时显示倒计时。两者都没有时不占位。
 */
@Composable
private fun HolidayRow(date: LocalDate, countries: Set<HolidayCountry>, lunar: LunarProvider, solarTermsShown: Boolean) {
    val today = remember(date, countries, solarTermsShown) { HolidayPolicy.on(date, countries, lunar, solarTermsShown) }
    val upcoming = remember(date, countries, solarTermsShown) { HolidayPolicy.upcoming(date, countries, lunar, solarTermsShown) }
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
