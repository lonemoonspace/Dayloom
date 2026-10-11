package io.github.lonemoonspace.dayloom.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource

/**
 * Draws one of the app's woven line icons: black strokes take [tint], green strokes take [accent].
 * The gaps where lines cross are cut into the paths, so a plain single-colour `Icon(tint = …)` still draws them correctly.
 * 绘制 App 的编织线条图标：黑色线条用 [tint]，绿色线条用 [accent]。
 * 线条交叉处的缝切在路径里，所以普通单色 `Icon(tint = …)` 也能正确显示。
 */
@Composable
fun DuotoneIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    accent: Color = MaterialTheme.colorScheme.secondary,
) {
    val filter = remember(tint, accent) { ColorFilter.colorMatrix(ColorMatrix(duotoneMatrix(tint, accent))) }
    Image(painter = painterResource(id), contentDescription = contentDescription, modifier = modifier, colorFilter = filter)
}

/**
 * A colour matrix mapping black to [tint] and pure green to [accent]; alpha is kept and scaled by the tint's.
 * The source's green channel chooses between the two, so anti-aliased pixels where they meet blend smoothly.
 * 把黑色映射为 [tint]、纯绿映射为 [accent] 的颜色矩阵；保留 alpha，并乘以 tint 的 alpha。
 * 由源图的绿色通道在两色之间取值，两色相接处的抗锯齿像素会平滑过渡。
 */
internal fun duotoneMatrix(tint: Color, accent: Color): FloatArray = floatArrayOf(
    0f, accent.red - tint.red, 0f, 0f, tint.red * 255f,
    0f, accent.green - tint.green, 0f, 0f, tint.green * 255f,
    0f, accent.blue - tint.blue, 0f, 0f, tint.blue * 255f,
    0f, 0f, 0f, tint.alpha, 0f,
)
