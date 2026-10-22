package io.github.lonemoonspace.dayloom.feature.transit.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Which route columns stay when the card is narrow: [platform] keeps its column, [mergeNext] writes the line changed to after
 * the stop name instead of in its own column.
 * 卡片窄时保留哪些路线列：[platform] 站台保留自己的一列，[mergeNext] 把换乘的线路写在站名后面，而不是单独一列。
 */
internal data class RouteFit(val platform: Boolean, val mergeNext: Boolean)

/**
 * Picks the first layout that leaves the stop name at least [minStop], trying them from the one that loses least: all
 * columns; the line changed to merged after the stop name (nothing lost, it only moves; the stop then needs room for
 * [mergedSuffix] too); the platform column dropped; both. When none fits, both is the narrowest. Widths are the card's
 * measured columns, 0 for a column nobody uses; [route] is what the route line has, and every column but the stop name
 * is followed or preceded by one [gap].
 * 从损失最少的布局开始，挑第一个能让站名至少有 [minStop] 的：全部列；把换乘的线路并到站名后面（什么都没丢，只是挪了位置；
 * 站名这时还要放得下 [mergedSuffix]）；去掉站台列；两者都做。都放不下时两者都做是最窄的。宽度是卡片量出来的各列，没人用
 * 的列为 0；[route] 是路线这一行可用的宽度，除站名外每一列都带一个 [gap]。
 */
internal fun fitRoute(
    route: Dp,
    first: Dp,
    platform: Dp,
    next: Dp,
    arrival: Dp,
    mergedSuffix: Dp,
    gap: Dp,
    minStop: Dp,
): RouteFit {
    fun stopSpace(fit: RouteFit): Dp {
        val columns = listOf(first, if (fit.platform) platform else 0.dp, if (fit.mergeNext) 0.dp else next, arrival).filter { it > 0.dp }
        val used = columns.fold(0.dp) { sum, width -> sum + width } + gap * columns.size
        return route - used - if (fit.mergeNext) mergedSuffix else 0.dp
    }
    val canMerge = next > 0.dp
    val candidates = listOfNotNull(
        RouteFit(platform = true, mergeNext = false),
        RouteFit(platform = true, mergeNext = true).takeIf { canMerge },
        RouteFit(platform = false, mergeNext = false),
        RouteFit(platform = false, mergeNext = true).takeIf { canMerge },
    )
    return candidates.firstOrNull { stopSpace(it) >= minStop } ?: candidates.last()
}
