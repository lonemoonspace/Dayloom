package io.github.lonemoonspace.dayloom.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Status colours (ok / warning / error / unknown, plus orange congestion and rain blue), which M3's ColorScheme has no roles for.
 * Both sets reach text contrast on the card colour; see StatusColorsContrastTest.
 * 状态语义色（正常/警告/错误/未知，外加拥堵橙与雨蓝），M3 的 ColorScheme 没有对应角色。
 * 深浅两套在卡片底色上都满足正文对比度，见 StatusColorsContrastTest。
 */
@Immutable
class StatusColors(
    val green: Color,
    val amber: Color,
    val red: Color,
    val gray: Color,
    val orange: Color,
    val rain: Color,
)

val LightStatusColors = StatusColors(
    green = Color(0xFF1B6139),
    amber = Color(0xFF774F00),
    red = Color(0xFFA51515),
    gray = Color(0xFF525960),
    orange = Color(0xFF933900),
    rain = Color(0xFF0D4A85),
)

val DarkStatusColors = StatusColors(
    green = Color(0xFF5FD08A),
    amber = Color(0xFFE6B547),
    red = Color(0xFFFFA49C),
    gray = Color(0xFFB8C0C8),
    orange = Color(0xFFFFB27A),
    rain = Color(0xFF9ACBFF),
)

/** Light by default, so previews render without [DayloomTheme]. / 默认浅色，预览不经过 [DayloomTheme] 也能渲染。 */
val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

val MaterialTheme.statusColors: StatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current
