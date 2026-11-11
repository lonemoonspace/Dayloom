package io.github.lonemoonspace.dayloom.core.i18n

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * User-facing text that is resolved in the current language only when it is shown.
 * ViewModels, errors and notifications pass this instead of a finished string, so a language switch never leaves stale text behind.
 * 展示时才按当前语言解析的界面文字。ViewModel、错误与通知传递它而不是拼好的字符串，切换语言后不会残留旧语言的文字。
 */
sealed interface UiText {
    /**
     * A string resource; arguments may themselves be [UiText] and are resolved first.
     * 字符串资源；参数也可以是 [UiText]，会先被解析。
     */
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    /**
     * Text that must not be translated: proper names, server messages, user input.
     * 不应翻译的文字：专有名词、服务端消息、用户输入。
     */
    data class Raw(val text: String) : UiText

    /**
     * A clock time, written in the user's 12/24-hour preference when shown ("14:05" or "2:05 PM").
     * 一个时刻，显示时按用户的 12/24 小时制偏好书写（「14:05」或「下午2:05」）。
     */
    data class Time(val time: LocalTime) : UiText

    /** A date with its clock time, e.g. "Nov 10, 2026, 14:05". / 日期加时刻，如「2026年11月10日 14:05」。 */
    data class DateTime(val at: LocalDateTime) : UiText

    /** Several texts, one per line (the morning brief). / 多段文字，每段一行（早间简报）。 */
    data class Lines(val lines: List<UiText>) : UiText
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

/**
 * [clock24] is the device's 12/24-hour setting; it lives outside [Resources], so the caller that has a Context passes it.
 * [clock24] 是设备的 12/24 小时制设置；它不在 [Resources] 里，由手里有 Context 的调用方传入。
 */
fun UiText.resolve(resources: Resources, clock24: Boolean = true): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> if (args.isEmpty()) resources.getString(id) else resources.getString(id, *resolveArgs(args, resources, clock24))
    is UiText.Plural -> resources.getQuantityString(id, count, *resolveArgs(args, resources, clock24))
    is UiText.Time -> time.format(formatter(resources, if (clock24) "Hm" else "hm"))
    is UiText.DateTime -> at.format(formatter(resources, if (clock24) "yMMMdHm" else "yMMMdhm"))
    is UiText.Lines -> lines.joinToString("\n") { it.resolve(resources, clock24) }
}

private fun formatter(resources: Resources, skeleton: String): DateTimeFormatter {
    val locale = resources.configuration.locales[0] ?: Locale.ROOT
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

private fun resolveArgs(args: List<Any>, resources: Resources, clock24: Boolean): Array<Any> =
    args.map { if (it is UiText) it.resolve(resources, clock24) else it }.toTypedArray()

@Composable
@ReadOnlyComposable
fun UiText.asString(): String = resolve(LocalResources.current, DateFormat.is24HourFormat(LocalContext.current))
