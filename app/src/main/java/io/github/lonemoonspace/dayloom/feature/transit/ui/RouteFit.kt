package io.github.lonemoonspace.dayloom.feature.transit.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What one route line needs besides its stop name: [fixed] is its line chips and the gaps between its items, [platforms]
 * its platform pills each with its gap; [hasStop] is false for a direct route, which names no stop.
 * 一条路线除站名外需要的宽度：[fixed] 是线路胶囊与各项之间的间距，[platforms] 是站台胶囊（各带一个间距）；直达不写站名，
 * [hasStop] 为 false。
 */
internal data class RouteWidths(val fixed: Dp, val platforms: Dp, val hasStop: Boolean)

/**
 * Whether the card keeps its platform pills: only when every route line still fits in [route] with them, leaving its stop
 * name at least [minStop]. One answer for the whole card, so platforms never show on some rows and not on others.
 * 卡片是否保留站台胶囊：只有每一条路线带着它们仍放得进 [route]、站名还剩至少 [minStop] 时才保留。整张卡片一个答案，不会有的
 * 行有站台、有的行没有。
 */
internal fun keepPlatforms(route: Dp, lines: List<RouteWidths>, minStop: Dp): Boolean =
    lines.all { route - it.fixed - it.platforms >= if (it.hasStop) minStop else 0.dp }
