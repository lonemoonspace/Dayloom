package io.github.lonemoonspace.dayloom.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

// surfaceContainer* and outlineVariant are defined explicitly: left at the M3 baseline they keep a purple tint that clashes
// with the blue-grey surfaces here. Card colours are in AppSurfaces.
// surfaceContainer* 与 outlineVariant 必须显式定义：停留在 M3 基线时带紫调，与这里的蓝灰底色冲突。卡片底色见 AppSurfaces。
private val LightColors = lightColorScheme(
    primary = Color(0xFF185FA7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E8FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF006B63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9CF2E8),
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFF6D5900),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE1E6EE),
    onSurfaceVariant = Color(0xFF424750),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF3F9),
    surfaceContainer = Color(0xFFE9EEF5),
    surfaceContainerHigh = Color(0xFFE3E9F1),
    surfaceContainerHighest = Color(0xFFDDE3EC),
    surfaceBright = Color(0xFFF7F9FC),
    surfaceDim = Color(0xFFD7DCE4),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C8D1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C9F7),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF004880),
    onPrimaryContainer = Color(0xFFD8E8FF),
    secondary = Color(0xFF80DBD1),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF005049),
    onSecondaryContainer = Color(0xFF9CF2E8),
    tertiary = Color(0xFFE6C64F),
    background = Color(0xFF101419),
    onBackground = Color(0xFFE1E5EC),
    surface = Color(0xFF101419),
    onSurface = Color(0xFFE1E5EC),
    surfaceVariant = Color(0xFF42474F),
    onSurfaceVariant = Color(0xFFC2C7D0),
    surfaceContainerLowest = Color(0xFF0B0E12),
    surfaceContainerLow = Color(0xFF181C22),
    surfaceContainer = Color(0xFF1C2027),
    surfaceContainerHigh = Color(0xFF262A32),
    surfaceContainerHighest = Color(0xFF31353D),
    surfaceBright = Color(0xFF363A41),
    surfaceDim = Color(0xFF101419),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474F),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
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

@Composable
fun DayloomTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalStatusColors provides if (dark) DarkStatusColors else LightStatusColors,
        LocalAppSurfaces provides if (dark) DarkAppSurfaces else LightAppSurfaces,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
