package io.github.lonemoonspace.dayloom.core.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** The colour matrix behind DuotoneIcon, applied the way the platform does (channels 0..255). / DuotoneIcon 的颜色矩阵，按平台的方式（通道 0..255）计算。 */
class DuotoneIconTest {

    private val ink = Color(0xFF185FA7)
    private val accent = Color(0xFF006B63)

    private fun apply(m: FloatArray, r: Float, g: Float, b: Float, a: Float): List<Float> = (0 until 4).map { row ->
        val o = row * 5
        (m[o] * r + m[o + 1] * g + m[o + 2] * b + m[o + 3] * a + m[o + 4]).coerceIn(0f, 255f)
    }

    private fun assertRgba(expected: Color, actual: List<Float>) {
        val want = listOf(expected.red, expected.green, expected.blue, expected.alpha).map { it * 255f }
        want.zip(actual).forEach { (w, a) -> assertEquals(w, a, 0.5f) }
    }

    @Test
    fun `black strokes take the tint and green strokes take the accent`() {
        val m = duotoneMatrix(ink, accent)
        assertRgba(ink, apply(m, 0f, 0f, 0f, 255f))
        assertRgba(accent, apply(m, 0f, 255f, 0f, 255f))
    }

    @Test
    fun `an anti-aliased pixel between the two colours blends halfway`() {
        val mixed = apply(duotoneMatrix(ink, accent), 0f, 127.5f, 0f, 255f)
        assertRgba(Color((ink.red + accent.red) / 2, (ink.green + accent.green) / 2, (ink.blue + accent.blue) / 2), mixed)
    }

    @Test
    fun `transparent stays transparent and the tint alpha scales the icon`() {
        assertEquals(0f, apply(duotoneMatrix(ink, accent), 0f, 0f, 0f, 0f)[3], 0f)
        val faded = ink.copy(alpha = 0.38f)
        assertEquals(0.38f * 255f, apply(duotoneMatrix(faded, accent), 0f, 255f, 0f, 255f)[3], 0.5f)
    }

    @Test
    fun `the same tint and accent give a plain single-colour icon`() {
        val m = duotoneMatrix(ink, ink)
        assertRgba(ink, apply(m, 0f, 0f, 0f, 255f))
        assertRgba(ink, apply(m, 0f, 255f, 0f, 255f))
    }
}
