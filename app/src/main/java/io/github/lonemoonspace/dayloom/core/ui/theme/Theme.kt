package io.github.lonemoonspace.dayloom.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** Material 3 Expressive proportions: cards at 20 dp, the weather card at 24 dp. / Material 3 Expressive 的比例：卡片 20 dp，天气主卡 24 dp。 */
private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
)

/**
 * Tabular figures everywhere, so times and countdowns do not jitter as they tick and timetable rows line up.
 * 所有文字样式都用等宽数字，时刻与倒计时跳动时不会左右抖动，时刻表各行也能对齐。
 */
private val AppTypography: Typography = Typography().run {
    fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")
    copy(
        displayLarge = displayLarge.tabular(),
        displayMedium = displayMedium.tabular(),
        displaySmall = displaySmall.tabular(),
        headlineLarge = headlineLarge.tabular(),
        headlineMedium = headlineMedium.tabular(),
        headlineSmall = headlineSmall.tabular(),
        titleLarge = titleLarge.tabular(),
        titleMedium = titleMedium.tabular(),
        titleSmall = titleSmall.tabular(),
        bodyLarge = bodyLarge.tabular(),
        bodyMedium = bodyMedium.tabular(),
        bodySmall = bodySmall.tabular(),
        labelLarge = labelLarge.tabular(),
        labelMedium = labelMedium.tabular(),
        labelSmall = labelSmall.tabular(),
    )
}

/**
 * Colours come from the wallpaper (Material You, always available from minSdk 33). Only the tones are fixed: the page is
 * one step darker than the cards in light mode and one step lighter in dark mode, so cards stand out on any wallpaper, and
 * status colours keep their contrast because neutral tones do not depend on the hue (see StatusColorsContrastTest).
 * 颜色取自壁纸（Material You，minSdk 33 起总是可用）。固定的只有明度：浅色时页面比卡片暗一级，深色时卡片比页面亮一级，
 * 任何壁纸下卡片都分得开；中性色的明度与色相无关，状态色的对比度因此不变（见 StatusColorsContrastTest）。
 */
@Composable
fun DayloomTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val wallpaper = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val scheme = wallpaper.copy(background = if (dark) wallpaper.surface else wallpaper.surfaceContainer)
    val surfaces = if (dark) {
        AppSurfaces(card = scheme.surfaceContainer, tile = scheme.surfaceContainerHigh)
    } else {
        AppSurfaces(card = scheme.surfaceContainerLowest, tile = scheme.surfaceContainerLow)
    }
    CompositionLocalProvider(
        LocalStatusColors provides if (dark) DarkStatusColors else LightStatusColors,
        LocalAppSurfaces provides surfaces,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
