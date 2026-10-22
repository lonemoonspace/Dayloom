package io.github.lonemoonspace.dayloom.feature.transit

import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.feature.transit.ui.RouteFit
import io.github.lonemoonspace.dayloom.feature.transit.ui.fitRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteFitTest {

    /** Typical measured columns: L1 chip, "3站台", R12 chip, "→ 8:54 PM", " · R12". / 常见的量测列宽。 */
    private fun fit(route: Int, next: Int = 31, suffix: Int = 25) = fitRoute(
        route = route.dp, first = 22.dp, platform = 25.dp, next = next.dp, arrival = 60.dp,
        mergedSuffix = suffix.dp, gap = 3.dp, minStop = 40.dp,
    )

    @Test
    fun `with room every column stays`() {
        // 22 + 25 + 31 + 60 + 4 × 3 = 150, leaving 50 for the stop. / 剩下 50 给站名。
        assertEquals(RouteFit(platform = true, mergeNext = false), fit(route = 200))
    }

    @Test
    fun `merging the line comes before dropping the platform, since it loses nothing`() {
        // All columns leave 35; merged: 185 - (22 + 25 + 60 + 3 × 3) - 25 = 44. / 全部列只剩 35；合并后站名还有 44。
        assertEquals(RouteFit(platform = true, mergeNext = true), fit(route = 185))
    }

    @Test
    fun `the platform goes when merging alone is not enough, then both`() {
        // A long merged suffix makes merging alone too tight; dropping the platform is enough.
        // 合并的后缀很长时单靠合并不够；去掉站台就够了。
        assertEquals(RouteFit(platform = false, mergeNext = false), fit(route = 185, suffix = 60))
        // 12-hour clock, a wide pill and two changes: only both together leave room. / 12 小时制、宽胶囊、两次换乘：两者都做才够。
        assertEquals(RouteFit(platform = false, mergeNext = true), fit(route = 160, next = 59))
    }

    @Test
    fun `when nothing fits the narrowest layout is used, and direct-only cards never merge`() {
        assertEquals(RouteFit(platform = false, mergeNext = true), fit(route = 100))
        assertEquals(RouteFit(platform = false, mergeNext = false), fit(route = 100, next = 0))
    }
}
