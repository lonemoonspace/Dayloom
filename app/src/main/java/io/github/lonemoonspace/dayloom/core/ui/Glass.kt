package io.github.lonemoonspace.dayloom.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

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

@Composable
@ReadOnlyComposable
private fun glassStyle(): HazeStyle {
    val surface = MaterialTheme.colorScheme.surface
    return HazeStyle(
        backgroundColor = surface,
        tints = listOf(HazeTint(surface.copy(alpha = 0.55f))),
        blurRadius = 24.dp,
        noiseFactor = 0f,
        fallbackTint = HazeTint(surface.copy(alpha = 0.9f)),
    )
}

/**
 * Glass background for a bar. Without a blur source, a translucent fill: hazeEffect with no state would blur the bar's own content.
 * 栏的玻璃底。没有模糊源时画半透明底色：不给 state 的 hazeEffect 会去模糊栏自己的内容。
 */
@Composable
fun Modifier.glass(): Modifier {
    val state = LocalHazeState.current
    return if (state != null) {
        hazeEffect(state = state, style = glassStyle())
    } else {
        background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    }
}
