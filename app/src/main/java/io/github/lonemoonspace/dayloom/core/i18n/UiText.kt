package io.github.lonemoonspace.dayloom.core.i18n

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources

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

    /** Several texts, one per line (the morning brief). / 多段文字，每段一行（早间简报）。 */
    data class Lines(val lines: List<UiText>) : UiText
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

fun UiText.resolve(resources: Resources): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> if (args.isEmpty()) resources.getString(id) else resources.getString(id, *resolveArgs(args, resources))
    is UiText.Plural -> resources.getQuantityString(id, count, *resolveArgs(args, resources))
    is UiText.Lines -> lines.joinToString("\n") { it.resolve(resources) }
}

private fun resolveArgs(args: List<Any>, resources: Resources): Array<Any> =
    args.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray()

@Composable
@ReadOnlyComposable
fun UiText.asString(): String = resolve(LocalResources.current)
