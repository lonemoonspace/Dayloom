package io.github.lonemoonspace.dayloom.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

// Only the two bars are blurred; cards stay solid, because small times and status colours lose contrast on a blurred background.
// 只有两条栏做模糊，卡片保持实色：小字号的时刻与状态色在模糊底上对比度会明显下降。

/** The whole page is the blur source; null in previews. / 整页内容是模糊源；预览里为 null。 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Height taken by the top and bottom bars (including system bars). Pages scroll under the bars, so lists add this to their padding.
 * 顶栏与底栏占用的高度（含系统栏）。页面从栏下面滚过，列表要把它加进内边距。
 */
val LocalBarInsets = compositionLocalOf { PaddingValues(0.dp) }

/** Adds the bottom bar (and with [top], the top bar) to a list's content padding. / 给列表内边距加上底栏（[top] 时也加顶栏）。 */
@Composable
@ReadOnlyComposable
fun PaddingValues.plusBars(top: Boolean = false): PaddingValues {
    val bars = LocalBarInsets.current
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(dir),
        end = calculateEndPadding(dir),
        top = calculateTopPadding() + if (top) bars.calculateTopPadding() else 0.dp,
        bottom = calculateBottomPadding() + bars.calculateBottomPadding(),
    )
}

/**
 * Blur Styles compare by identity, so one is kept per surface colour; a new one each composition would redo the effect.
 * 模糊样式按对象身份比较，所以每种表面色只建一个；每次重组都新建会让效果重新配置。
 */
private fun glassStyle(surface: Color): HazeBlurStyle = HazeBlurStyle {
    backgroundColor(surface)
    colorEffects(listOf(HazeColorEffect.tint(surface.copy(alpha = 0.55f))))
    blurRadius(24.dp)
    noiseFactor(0f)
    fallbackColorEffect(HazeColorEffect.tint(surface.copy(alpha = 0.9f)))
}

/**
 * Glass background for a bar. Without a blur source, a translucent fill, since blurring with no source would blur the bar's own content.
 * 栏的玻璃底。没有模糊源时画半透明底色：没有模糊源的模糊会去模糊栏自己的内容。
 */
@Composable
fun Modifier.glass(): Modifier {
    val state = LocalHazeState.current
    val surface = MaterialTheme.colorScheme.surface
    return if (state != null) {
        val input = remember(state) { HazeInput.Sources(state) }
        val style = remember(surface) { glassStyle(surface) }
        hazeBlur(input = input, style = style)
    } else {
        background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    }
}
