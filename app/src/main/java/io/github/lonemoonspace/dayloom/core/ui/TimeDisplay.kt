package io.github.lonemoonspace.dayloom.core.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The current time from [LocalAppClock], updated at every minute boundary so clocks and "in N minutes" text stay current
 * while the screen is shown.
 * 来自 [LocalAppClock] 的当前时刻，每到整分钟更新一次，屏幕显示期间时钟与「N 分钟后」之类的文字保持最新。
 */
@Composable
fun rememberMinuteTick(): ZonedDateTime {
    val clock = LocalAppClock.current
    val now by produceState(clock.now().truncatedTo(ChronoUnit.MINUTES), clock) {
        while (true) {
            val current = clock.now()
            value = current.truncatedTo(ChronoUnit.MINUTES)
            val untilNextMinute = Duration.between(current, current.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1))
            delay(untilNextMinute.toMillis().coerceAtLeast(1))
        }
    }
    return now
}

/** The UI language's locale (the per-app language when set). / 界面语言的 Locale（设置了按应用语言时就是它）。 */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT

/**
 * Hours and minutes in the user's 12/24-hour preference and language, e.g. "14:05" or "2:05 PM".
 * 按用户的 12/24 小时制偏好与语言显示时分，如「14:05」或「下午2:05」。
 */
@Composable
fun rememberTimeFormatter(): DateTimeFormatter = rememberPatternFormatter(if (is24Hour()) "Hm" else "hm")

/** Hour only, for compact hourly strips: "14" or "2 PM". / 只显示小时，用于紧凑的逐小时条：「14」或「下午2时」。 */
@Composable
fun rememberHourFormatter(): DateTimeFormatter = rememberPatternFormatter(if (is24Hour()) "H" else "ha")

@Composable
private fun is24Hour(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

/** A localized pattern from a CLDR skeleton, e.g. "MMMEd". / 由 CLDR skeleton（如「MMMEd」）生成的本地化格式。 */
@Composable
fun rememberPatternFormatter(skeleton: String): DateTimeFormatter {
    val locale = currentLocale()
    return remember(locale, skeleton) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}
