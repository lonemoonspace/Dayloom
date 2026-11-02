package io.github.lonemoonspace.dayloom.core.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Curated color theme presets. Empty or "system" follows the Android wallpaper dynamic color scheme.
 * 精选主题配色预设。空字符串或 "system" 跟随 Android 壁纸动态配色。
 */
object ThemePresets {
    const val SYSTEM = "system"
    const val NORDIC_DAWN = "nordic_dawn"
    const val WARM_LINEN = "warm_linen"
    const val SAGE_FOREST = "sage_forest"
    const val OLED_OBSIDIAN = "oled_obsidian"
    const val AURORA_TWILIGHT = "aurora_twilight"

    val ALL = listOf(SYSTEM, NORDIC_DAWN, WARM_LINEN, SAGE_FOREST, OLED_OBSIDIAN, AURORA_TWILIGHT)

    // Nordic Dawn (Glacier Blue & Crisp Morning) / 北欧晨曦 (冰川蓝与晨光)
    private val NordicDawnLight = lightColorScheme(
        primary = Color(0xFF2563EB),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDBEAFE),
        onPrimaryContainer = Color(0xFF1E3A8A),
        secondary = Color(0xFF0284C7),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE0F2FE),
        onSecondaryContainer = Color(0xFF075985),
        surface = Color(0xFFF1F5F9),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFE2E8F0),
        onSurfaceVariant = Color(0xFF475569),
        surfaceContainer = Color(0xFFE8EEF5),
        surfaceContainerLow = Color(0xFFEDF2F7),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFDFE7F0),
        outline = Color(0xFF94A3B8),
        outlineVariant = Color(0xFFCBD5E1),
    )

    private val NordicDawnDark = darkColorScheme(
        primary = Color(0xFF60A5FA),
        onPrimary = Color(0xFF0F172A),
        primaryContainer = Color(0xFF1D4ED8),
        onPrimaryContainer = Color(0xFFDBEAFE),
        secondary = Color(0xFF38BDF8),
        onSecondary = Color(0xFF0C4A6E),
        secondaryContainer = Color(0xFF0369A1),
        onSecondaryContainer = Color(0xFFE0F2FE),
        surface = Color(0xFF0B0F19),
        onSurface = Color(0xFFF8FAFC),
        surfaceVariant = Color(0xFF1E293B),
        onSurfaceVariant = Color(0xFF94A3B8),
        surfaceContainer = Color(0xFF111827),
        surfaceContainerLow = Color(0xFF0D131F),
        surfaceContainerLowest = Color(0xFF080C14),
        surfaceContainerHigh = Color(0xFF1F2937),
        outline = Color(0xFF64748B),
        outlineVariant = Color(0xFF334155),
    )

    // Warm Linen (Sunny Amber & Paper texture) / 暖阳木棉 (日暖橙与米白纸质手账感)
    private val WarmLinenLight = lightColorScheme(
        primary = Color(0xFFEA580C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFEDD5),
        onPrimaryContainer = Color(0xFF7C2D12),
        secondary = Color(0xFFD97706),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFEF3C7),
        onSecondaryContainer = Color(0xFF78350F),
        surface = Color(0xFFFAF5EE),
        onSurface = Color(0xFF292524),
        surfaceVariant = Color(0xFFF0EAE1),
        onSurfaceVariant = Color(0xFF78716C),
        surfaceContainer = Color(0xFFF4EDE3),
        surfaceContainerLow = Color(0xFFF9F3EA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFEAE2D6),
        outline = Color(0xFFA8A29E),
        outlineVariant = Color(0xFFD6CEB5),
    )

    private val WarmLinenDark = darkColorScheme(
        primary = Color(0xFFFB923C),
        onPrimary = Color(0xFF431407),
        primaryContainer = Color(0xFF9A3412),
        onPrimaryContainer = Color(0xFFFFEDD5),
        secondary = Color(0xFFFBBF24),
        onSecondary = Color(0xFF451A03),
        secondaryContainer = Color(0xFFB45309),
        onSecondaryContainer = Color(0xFFFEF3C7),
        surface = Color(0xFF120E0B),
        onSurface = Color(0xFFFAF7F5),
        surfaceVariant = Color(0xFF29221C),
        onSurfaceVariant = Color(0xFFA8A29E),
        surfaceContainer = Color(0xFF1C1612),
        surfaceContainerLow = Color(0xFF16110D),
        surfaceContainerLowest = Color(0xFF0D0A08),
        surfaceContainerHigh = Color(0xFF28201A),
        outline = Color(0xFF78716C),
        outlineVariant = Color(0xFF443D37),
    )

    // Sage Forest (Calming Nature & Morning Dew) / 森林晨露 (鼠尾草绿与舒缓清爽)
    private val SageForestLight = lightColorScheme(
        primary = Color(0xFF059669),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD1FAE5),
        onPrimaryContainer = Color(0xFF064E3B),
        secondary = Color(0xFF0D9488),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCCFBF1),
        onSecondaryContainer = Color(0xFF115E59),
        surface = Color(0xFFF2F8F5),
        onSurface = Color(0xFF064E3B),
        surfaceVariant = Color(0xFFE2EFE9),
        onSurfaceVariant = Color(0xFF4D7C6F),
        surfaceContainer = Color(0xFFE5F1EB),
        surfaceContainerLow = Color(0xFFEDF5F0),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFDBEAE2),
        outline = Color(0xFF86A89C),
        outlineVariant = Color(0xFFBFE0D4),
    )

    private val SageForestDark = darkColorScheme(
        primary = Color(0xFF34D399),
        onPrimary = Color(0xFF022C22),
        primaryContainer = Color(0xFF047857),
        onPrimaryContainer = Color(0xFFD1FAE5),
        secondary = Color(0xFF2DD4BF),
        onSecondary = Color(0xFF042F2E),
        secondaryContainer = Color(0xFF0F766E),
        onSecondaryContainer = Color(0xFFCCFBF1),
        surface = Color(0xFF06100C),
        onSurface = Color(0xFFF2FDF8),
        surfaceVariant = Color(0xFF162B22),
        onSurfaceVariant = Color(0xFF86A89C),
        surfaceContainer = Color(0xFF0E1F18),
        surfaceContainerLow = Color(0xFF0A1813),
        surfaceContainerLowest = Color(0xFF040A07),
        surfaceContainerHigh = Color(0xFF152C22),
        outline = Color(0xFF4D7C6F),
        outlineVariant = Color(0xFF234438),
    )

    // OLED Obsidian (True Pitch Black & Electric Aurora) / 曜石极夜 (OLED 纯黑与极光蓝绿)
    private val OledObsidianDark = darkColorScheme(
        primary = Color(0xFF38BDF8),
        onPrimary = Color(0xFF082F49),
        primaryContainer = Color(0xFF0284C7),
        onPrimaryContainer = Color(0xFFE0F2FE),
        secondary = Color(0xFF34D399),
        onSecondary = Color(0xFF022C22),
        secondaryContainer = Color(0xFF047857),
        onSecondaryContainer = Color(0xFFD1FAE5),
        surface = Color(0xFF000000), // Pure OLED Black
        onSurface = Color(0xFFF8FAFC),
        surfaceVariant = Color(0xFF131722),
        onSurfaceVariant = Color(0xFF94A3B8),
        surfaceContainer = Color(0xFF0D111A),
        surfaceContainerLow = Color(0xFF06080D),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerHigh = Color(0xFF181F2E),
        outline = Color(0xFF64748B),
        outlineVariant = Color(0xFF2B3648),
    )

    // Aurora Twilight (Cyber Violet & Deep Night) / 深空暮紫 (群青紫与赛博极夜)
    private val AuroraTwilightDark = darkColorScheme(
        primary = Color(0xFFC084FC),
        onPrimary = Color(0xFF3B0764),
        primaryContainer = Color(0xFF7E22CE),
        onPrimaryContainer = Color(0xFFFAF5FF),
        secondary = Color(0xFF818CF8),
        onSecondary = Color(0xFF1E1B4B),
        secondaryContainer = Color(0xFF4338CA),
        onSecondaryContainer = Color(0xFFEEF2FF),
        surface = Color(0xFF05030A),
        onSurface = Color(0xFFFAF5FF),
        surfaceVariant = Color(0xFF1A1428),
        onSurfaceVariant = Color(0xFFA8A29E),
        surfaceContainer = Color(0xFF110E1C),
        surfaceContainerLow = Color(0xFF0A0713),
        surfaceContainerLowest = Color(0xFF020104),
        surfaceContainerHigh = Color(0xFF1E1730),
        outline = Color(0xFF7E7496),
        outlineVariant = Color(0xFF352B4D),
    )

    fun resolveColorScheme(preset: String, dark: Boolean, context: Context): ColorScheme {
        return when (preset) {
            NORDIC_DAWN -> if (dark) NordicDawnDark else NordicDawnLight
            WARM_LINEN -> if (dark) WarmLinenDark else WarmLinenLight
            SAGE_FOREST -> if (dark) SageForestDark else SageForestLight
            OLED_OBSIDIAN -> if (dark) OledObsidianDark else NordicDawnLight
            AURORA_TWILIGHT -> if (dark) AuroraTwilightDark else NordicDawnLight
            else -> {
                // SYSTEM_WALLPAPER fallback
                val wallpaper = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                wallpaper.copy(background = if (dark) wallpaper.surface else wallpaper.surfaceContainer)
            }
        }
    }
}
