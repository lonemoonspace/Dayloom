package io.github.lonemoonspace.dayloom.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG contrast of the status colours on the card, tile and chip backgrounds. / 状态色在卡片、小块与徽标底色上的 WCAG 对比度。 */
class StatusColorsContrastTest {

    @Test
    fun `status colours reach AA text contrast on cards, tiles and chips in both themes`() {
        for ((theme, colors, surfaces) in listOf(
            Triple("light", LightStatusColors, LightAppSurfaces),
            Triple("dark", DarkStatusColors, DarkAppSurfaces),
        )) {
            for ((name, color) in colors.named()) {
                for ((bgName, bg) in listOf("card" to surfaces.card, "tile" to surfaces.tile)) {
                    assertTrue("$theme $name on $bgName", contrast(color, bg) >= AA_TEXT)
                    // Status chips are the colour at 12% over the background. / 状态徽标底色是状态色 12% 叠在背景上。
                    val chip = blend(color, bg, 0.12f)
                    assertTrue("$theme $name chip on $bgName", contrast(color, chip) >= AA_TEXT)
                }
            }
        }
    }

    @Test
    fun `the contrast formula matches known WCAG values`() {
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.001)
        assertEquals(4.54, contrast(Color(0xFF767676), Color.White), 0.01)
    }

    private fun StatusColors.named() =
        listOf("green" to green, "amber" to amber, "red" to red, "gray" to gray, "orange" to orange, "rain" to rain)

    private fun blend(fg: Color, bg: Color, alpha: Float) = Color(
        red = fg.red * alpha + bg.red * (1 - alpha),
        green = fg.green * alpha + bg.green * (1 - alpha),
        blue = fg.blue * alpha + bg.blue * (1 - alpha),
    )

    private fun luminance(c: Color): Double {
        fun lin(v: Float): Double {
            val s = Math.round(v * 255f) / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private companion object {
        const val AA_TEXT = 4.5
    }
}
