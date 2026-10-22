package io.github.lonemoonspace.dayloom.feature.transit

import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.feature.transit.ui.RouteWidths
import io.github.lonemoonspace.dayloom.feature.transit.ui.keepPlatforms
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteFitTest {

    /** "L1 (1) Asker R12 (3)": chips 22 + 31 and three gaps; two pills of 17 with their gaps. / 常见的换乘行宽度。 */
    private val transfer = RouteWidths(fixed = 62.dp, platforms = 40.dp, hasStop = true)

    /** "L1 (1)": one chip, one pill. / 直达：一个线路胶囊、一个站台胶囊。 */
    private val direct = RouteWidths(fixed = 22.dp, platforms = 20.dp, hasStop = false)

    @Test
    fun `with room the platforms stay`() {
        // 160 - 62 - 40 = 58 left for the stop. / 站名还剩 58。
        assertTrue(keepPlatforms(160.dp, listOf(transfer, direct), minStop = 40.dp))
    }

    @Test
    fun `the platforms go when one stop name would get too narrow`() {
        // 140 - 62 - 40 = 38, under 40. / 只剩 38，不到 40。
        assertFalse(keepPlatforms(140.dp, listOf(direct, transfer), minStop = 40.dp))
    }

    @Test
    fun `direct-only cards keep them while they fit, with no stop to leave room for`() {
        assertTrue(keepPlatforms(42.dp, listOf(direct), minStop = 40.dp))
        assertFalse(keepPlatforms(41.dp, listOf(direct), minStop = 40.dp))
    }
}
