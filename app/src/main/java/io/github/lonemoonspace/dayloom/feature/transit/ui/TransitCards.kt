package io.github.lonemoonspace.dayloom.feature.transit.ui

import androidx.annotation.StringRes
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.i18n.asString
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.SkeletonLines
import io.github.lonemoonspace.dayloom.core.ui.STATUS_CHIP_PADDING
import io.github.lonemoonspace.dayloom.core.ui.StatusChip
import io.github.lonemoonspace.dayloom.core.ui.statusChipStyle
import io.github.lonemoonspace.dayloom.core.ui.rememberMinuteTick
import io.github.lonemoonspace.dayloom.core.ui.rememberTimeFormatter
import io.github.lonemoonspace.dayloom.core.ui.theme.DayloomTheme
import io.github.lonemoonspace.dayloom.core.ui.theme.statusColors
import io.github.lonemoonspace.dayloom.core.ui.updatedAgo
import io.github.lonemoonspace.dayloom.core.ui.userMessage
import io.github.lonemoonspace.dayloom.feature.transit.domain.BoardDeparture
import io.github.lonemoonspace.dayloom.feature.transit.domain.Boards
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteKind
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteMode
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteRoute
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteTrips
import io.github.lonemoonspace.dayloom.feature.transit.domain.FavouriteBoard
import io.github.lonemoonspace.dayloom.feature.transit.domain.LegState
import io.github.lonemoonspace.dayloom.feature.transit.domain.LegStatus
import io.github.lonemoonspace.dayloom.feature.transit.domain.OpposingHint
import io.github.lonemoonspace.dayloom.feature.transit.domain.OpposingPolicy
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitLeg
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitPolicy
import io.github.lonemoonspace.dayloom.feature.transit.domain.TripOption
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One commute card (train or bus): the next options towards the destination before noon, back from noon on. One row per
 * option: departure time, its route in aligned columns (see [RouteLine]), and the worst real-time status of its legs.
 * 一张通勤卡片（火车或公交）：中午前显示去程、中午起显示回程的接下来几个方案。每个方案一行：出发时刻、按列对齐的路线（见
 * [RouteLine]），以及各段中最差的实时状态。
 */
@Composable
internal fun CommuteCard(
    kind: CommuteKind,
    snapshot: Snapshot<CommuteTrips>?,
    error: AppError?,
    isStale: (Snapshot<CommuteTrips>, Instant) -> Boolean,
    route: CommuteRoute,
) {
    val now = rememberMinuteTick()
    val origin = route.origin
    val destination = route.destination
    val trips = snapshot?.value?.takeIf { route.isSet }
    // A snapshot fetched before the switch was turned off may still hold departures. / 开关关掉之前抓的快照可能还带着发车数据。
    val opposing = trips?.opposing?.takeIf { kind == CommuteKind.TRAIN && route.opposingHint }.orEmpty()
    // Before the first snapshot the direction comes from the clock, so the title does not change once data arrives.
    // 第一份快照到来之前按时钟定方向，数据到了标题也不会变。
    val subtitle = when {
        !route.isSet -> null
        (trips?.mode ?: TransitPolicy.commuteMode(now)) == CommuteMode.OUTBOUND -> stringResource(R.string.transit_to_work, origin.name, destination.name)
        else -> stringResource(R.string.transit_back_home, destination.name, origin.name)
    }
    InfoCard(
        title = stringResource(kindTitle(kind)),
        icon = kindIcon(kind),
        subtitle = subtitle,
        meta = if (route.isSet) snapshot?.let { updatedAgo(it.fetchedAt, now) } else null,
        stale = route.isSet && snapshot?.let { isStale(it, now.toInstant()) } == true,
    ) {
        if (!route.isSet) {
            Hint(stringResource(R.string.transit_setup_prompt), maxLines = 3)
            return@InfoCard
        }
        when (trips?.mode) {
            null -> if (error == null) SkeletonLines()
            CommuteMode.OUTBOUND -> OptionList(trips.outbound, route.options, now, snapshot?.fetchedAt, opposing)
            CommuteMode.INBOUND -> OptionList(trips.inbound, route.options, now, snapshot?.fetchedAt, opposing)
        }
        if (error != null) ErrorLine(error)
        Attribution()
    }
}

@StringRes
internal fun kindTitle(kind: CommuteKind): Int = when (kind) {
    CommuteKind.TRAIN -> R.string.transit_train_title
    CommuteKind.BUS -> R.string.transit_bus_title
}

@DrawableRes
private fun kindIcon(kind: CommuteKind): Int = when (kind) {
    CommuteKind.TRAIN -> R.drawable.ic_train
    CommuteKind.BUS -> R.drawable.ic_bus
}

/** [all] is the snapshot's list: it still holds the direct options folded into transfers. / [all] 是快照里的列表：里面还有被并入换乘方案的直达方案。 */
@Composable
private fun OptionList(all: List<TripOption>, limit: Int, now: ZonedDateTime, fetchedAt: Long?, opposing: List<BoardDeparture>) {
    val options = TransitPolicy.visibleOptions(all, now, limit, fetchedAt)
    if (options.isEmpty()) {
        Hint(stringResource(R.string.transit_no_options))
        return
    }
    val textWidth = rememberTextWidth()
    val pillStyle = statusChipStyle()
    val rows = options.map { option ->
        val worst = TransitPolicy.worst(option)
        val status = worst?.let { (leg, legStatus) ->
            // With several legs, name the line the status belongs to, before the status. When the two do not fit the pill,
            // the line goes: it is on the route already, and a cut status ("12 min…") would read as a countdown.
            // 多段时在状态前面写出是哪条线。两者放不进胶囊时去掉线路：路线里本来就有它，而截断的状态（「12 min…」）会被读成倒计时。
            val prefix = leg.line.takeIf { option.legs.size > 1 && legStatus.state != LegState.ON_TIME }
            val labelled = statusLabel(legStatus, prefix)
            val plain = statusLabel(legStatus)
            if (prefix != null && textWidth(labelled.text, pillStyle) + STATUS_CHIP_PADDING * 2 > BADGE_MAX_WIDTH) plain else labelled
        }
        CommuteRow(
            option = option,
            folded = TransitPolicy.foldedDirect(option, all),
            status = status,
            cancelled = worst?.second?.state == LegState.CANCELLED,
            hint = OpposingPolicy.hint(option, opposing),
        )
    }
    BoxWithConstraints {
        val columns = routeColumns(rows, now, maxWidth, textWidth)
        Column { rows.forEachIndexed { i, row -> OptionRow(row, columns, now, divider = i > 0) } }
    }
}

/** One option as the card shows it, worked out before measuring so the columns fit every row. / 卡片上的一个方案，先算好再量列宽，列宽才能容下每一行。 */
private class CommuteRow(
    val option: TripOption,
    val folded: TripOption?,
    val status: StatusLabel?,
    val cancelled: Boolean,
    val hint: OpposingHint? = null,
)

/**
 * One option in one row: the expected departure large on the left (the timetable time struck through under it when late),
 * the route in the middle, the arrival as large on its right, then the status pill. A transfer that replaced a direct
 * option on the same first train shows that direct option dimmed on a second line, so the two arrivals line up for
 * comparison. Cancelled options stay, struck through, so the user knows why they are gone.
 * 一个方案一行：左边大字是预计出发时刻（晚点时下面划掉时刻表时间），中间是路线，路线右边是同样大小的到达时刻，最后是状态胶囊。
 * 取代了同一班首车直达方案的换乘方案，在第二行淡色显示那个直达方案，两个到达时刻上下对齐便于比较。被取消的方案保留并划掉，
 * 用户才知道它为什么没了。
 */
@Composable
private fun OptionRow(row: CommuteRow, columns: RouteColumns, now: ZonedDateTime, divider: Boolean) {
    TransitRow(
        expected = row.option.departure,
        aimed = row.option.legs.first().aimedDeparture,
        cancelled = row.cancelled,
        now = now,
        divider = divider,
        timeWidth = columns.time,
        badgeWidth = columns.badge,
        badge = { row.status?.let { StatusChip(it.text, it.color, maxLines = 2, shape = RoundedCornerShape(PILL_CORNER)) } },
    ) {
        RouteLine(row.option, columns, now, struck = row.cancelled)
        if (row.folded != null) {
            RouteLine(row.folded, columns, now, struck = false, modifier = Modifier.alpha(FOLDED_ALPHA))
        }
        if (row.hint != null) Hint(opposingText(row.hint), maxLines = 2, color = MaterialTheme.statusColors.amber)
    }
}

/**
 * The line under a row whose first train waits, or may have to wait, for a late opposing train: only the line and its delay,
 * which is all the user needs to judge a tight change.
 * 第一班车在等、或可能要等晚点的对向车会车时，行下面的那一句：只写线路和晚点多少，用户凭这个就能判断换乘来不来得及。
 */
@Composable
private fun opposingText(hint: OpposingHint): String =
    pluralStringResource(R.plurals.transit_opposing, hint.delayMinutes, hint.line, hint.delayMinutes)

/** Dimmed enough to read as the alternative, still about 4.5:1 on the card. / 淡到看得出是备选，在卡片上仍约有 4.5:1 的对比度。 */
private const val FOLDED_ALPHA = 0.7f

/**
 * Column widths for one card, measured from the texts it shows: the departure time, the arrival and the status pill each
 * fit their widest entry, so the times and pills line up on every row. The route in between is packed, not columned; when
 * its stop name would get under [STOP_MIN_WIDTH] on some row (large fonts, 12-hour clocks, wide pills), [keepPlatforms]
 * drops the platform pills on the whole card, and only then does the stop name shorten.
 * 一张卡片的列宽，按卡片上实际显示的文字量出来：出发时刻、到达时刻、状态胶囊各取最宽的一项，每一行的时刻与胶囊都对得齐。
 * 中间的路线紧挨着排、不分列；某一行的站名会不到 [STOP_MIN_WIDTH] 时（大字号、12 小时制、宽胶囊），由 [keepPlatforms]
 * 去掉整张卡片的站台胶囊，之后才省略站名。
 */
private class RouteColumns(val time: Dp, val arrival: Dp, val badge: Dp, val platforms: Boolean)

@Composable
private fun routeColumns(rows: List<CommuteRow>, now: ZonedDateTime, width: Dp, textWidth: TextWidth): RouteColumns {
    val formatter = rememberTimeFormatter()
    fun at(millis: Long) = Instant.ofEpochMilli(millis).atZone(now.zone).format(formatter)
    val chip = lineChipWidth(textWidth)
    val pill = platformPillWidth(textWidth)
    val lines = rows.flatMap { listOfNotNull(it.option, it.folded) }
    val pillStyle = statusChipStyle()
    val time = timeColumnWidth(rows.map { it.option.departure to it.option.legs.first().aimedDeparture }, now, textWidth)
    val timeStyle = timeStyle()
    val arrival = lines.maxOf { textWidth(at(it.arrival), timeStyle) }
    val badge = rows.mapNotNull { it.status }.maxOfOrNull { textWidth(it.text, pillStyle) + STATUS_CHIP_PADDING * 2 }
        ?.coerceAtMost(BADGE_MAX_WIDTH) ?: 0.dp
    val route = width - time - TIME_GAP - ARRIVAL_GAP - arrival - BADGE_GAP - badge
    val widths = lines.map { option ->
        val rest = option.legs.drop(1)
        val chips = (listOf(option.legs.first()) + rest).map(chip)
        val items = chips.size + if (rest.isEmpty()) 0 else 1
        val platforms = platformsOf(option).map(pill)
        RouteWidths(
            fixed = chips.fold(0.dp) { sum, w -> sum + w } + ROUTE_GAP * (items - 1),
            platforms = platforms.fold(0.dp) { sum, w -> sum + w + ROUTE_GAP },
            hasStop = rest.isNotEmpty(),
        )
    }
    return RouteColumns(time = time, arrival = arrival, badge = badge, platforms = keepPlatforms(route, widths, STOP_MIN_WIDTH))
}

/**
 * The time column of a card: wide enough for every expected and timetable time in it, so a 12-hour "10:54 PM" does not push
 * one row's middle further right than the next.
 * 卡片的时刻列：放得下其中每一个预计时刻与时刻表时刻，12 小时制的「10:54 PM」也不会让某一行的中间部分比别的行靠右。
 */
@Composable
private fun timeColumnWidth(times: List<Pair<Long, Long>>, now: ZonedDateTime, textWidth: TextWidth): Dp {
    val formatter = rememberTimeFormatter()
    fun at(millis: Long) = Instant.ofEpochMilli(millis).atZone(now.zone).format(formatter)
    val timeStyle = timeStyle()
    val aimedStyle = MaterialTheme.typography.labelSmall
    return times.fold(TIME_MIN_WIDTH) { widest, (expected, aimed) ->
        maxOf(widest, textWidth(at(expected), timeStyle), textWidth(at(aimed), aimedStyle))
    }
}

/** The large departure and arrival times, the same size so the two read as a pair. / 出发与到达的大字时刻，同样大小，两者一看就是一对。 */
@Composable
private fun timeStyle(): TextStyle = LocalTextStyle.current.copy(fontSize = TIME_SIZE, lineHeight = 20.sp, fontWeight = FontWeight.Bold)

/**
 * One route on one line, packed: the first line and the platform it leaves from, then for a change the stop, the line
 * changed to and its platform, e.g. "L1 (1) Asker R12 (3)"; a direct route is just "L1 (1)". The arrival follows in its
 * own column, large and without an arrow: its place across from the departure says what it is. The duration and the gain
 * over the direct option are left out: the arrivals already show them.
 * 一条路线一行、紧挨着排：先坐的线路和出发站台，换乘时再写换乘站、换乘的线路和它的站台，如「L1 ① Asker R12 ③」；直达只有
 * 「L1 ①」。到达时刻在自己的一列，大字、不带箭头：它和出发时刻左右相对，位置已说明是什么。用时和比直达早多少都不写：到达
 * 时刻已经说明。
 */
@Composable
private fun RouteLine(option: TripOption, columns: RouteColumns, now: ZonedDateTime, struck: Boolean, modifier: Modifier = Modifier) {
    val time = rememberTimeFormatter()
    val color = if (struck) MaterialTheme.colorScheme.outline else LocalContentColor.current
    val decoration = if (struck) TextDecoration.LineThrough else null
    val first = option.legs.first()
    val rest = option.legs.drop(1)
    val arrival = Instant.ofEpochMilli(option.arrival).atZone(now.zone).format(time)
    val arrivalDescription = stringResource(R.string.transit_arrives, arrival)
    // One focus stop for the whole route. / 整条路线是一个读屏焦点。
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ROUTE_GAP)) {
            LineChip(first.line, first.replacementBus, compact = true)
            if (columns.platforms && first.platform.isNotEmpty()) PlatformPill(first.platform, color)
            if (rest.isNotEmpty()) {
                // Several changes: the last stop is where the final train is boarded, the one worth naming.
                // 多次换乘时写最后一个换乘站：在那里上最后一班车，最值得写出来。
                val stop = TransitPolicy.shortStopName(option.legs[option.legs.size - 2].toName)
                Text(
                    text = stop,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    textDecoration = decoration,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                rest.forEach { LineChip(it.line, it.replacementBus, compact = true) }
                val last = rest.last()
                if (columns.platforms && last.platform.isNotEmpty()) PlatformPill(last.platform, color)
            }
        }
        Spacer(Modifier.width(ARRIVAL_GAP))
        Text(
            text = arrival,
            style = timeStyle(),
            color = color,
            textDecoration = decoration,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(columns.arrival).semantics { contentDescription = arrivalDescription },
        )
    }
}

/**
 * The platforms a route shows: the one it leaves from, and for a change the one where the last train is boarded, at the
 * stop the route names.
 * 路线上显示的站台：出发站台；换乘时再加在路线写出的那个换乘站上最后一班车的站台。
 */
private fun platformsOf(option: TripOption): List<String> =
    listOfNotNull(option.legs.first().platform, option.legs.drop(1).lastOrNull()?.platform).filter { it.isNotEmpty() }

/**
 * A platform as an outlined number, so it reads apart from the filled line chips and takes less room than "Pl. 3". The
 * screen reader still hears "Pl. 3".
 * 站台写成描边的数字，和实心的线路胶囊一眼分开，也比「3站台」省地方。读屏仍念「3站台」。
 */
@Composable
private fun PlatformPill(platform: String, color: Color) {
    val description = stringResource(R.string.transit_platform, platform)
    Text(
        text = platform,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .semantics { contentDescription = description }
            .widthIn(min = PLATFORM_PILL_MIN_WIDTH)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(horizontal = PLATFORM_PILL_PADDING),
    )
}

/** How wide a [PlatformPill] is, from the same style, padding and minimum it draws with. / [PlatformPill] 有多宽，用的是它绘制时同样的样式、内边距与最小宽度。 */
@Composable
private fun platformPillWidth(textWidth: TextWidth): (String) -> Dp {
    val style = MaterialTheme.typography.labelSmall
    return { platform -> (textWidth(platform, style) + PLATFORM_PILL_PADDING * 2).coerceAtLeast(PLATFORM_PILL_MIN_WIDTH) }
}

private val PLATFORM_PILL_MIN_WIDTH = 17.dp
private val PLATFORM_PILL_PADDING = 4.dp

/** Width of a text on one line; the measuring side of the fixed columns. / 文字排成一行的宽度；定宽各列靠它来量。 */
private fun interface TextWidth {
    operator fun invoke(text: String, style: TextStyle): Dp
}

@Composable
private fun rememberTextWidth(): TextWidth {
    // A card measures a few dozen strings; the default cache of 8 would miss most of them. / 一张卡片要量几十条文字，默认 8 条的缓存大多命中不了。
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val density = LocalDensity.current
    return remember(measurer, density) {
        TextWidth { text, style -> with(density) { measurer.measure(text, style, softWrap = false).size.width.toDp() } }
    }
}

private val ROUTE_GAP = 3.dp
private val ARRIVAL_GAP = 6.dp

/** Below this the stop name is barely readable, so the platform pills give way first. / 站名再窄就几乎看不清，所以先让站台胶囊让位。 */
private val STOP_MIN_WIDTH = 40.dp

/**
 * Wide enough for "L1 晚 5 分" on one line; a labelled status that does not fit (the longer English one, mostly) drops its
 * line instead, and a single status wider than this wraps.
 * 够在一行放下「L1 晚 5 分」；带线路放不下的（多半是更长的英文）就去掉线路，单独的状态比这还宽就折行。
 */
private val BADGE_MAX_WIDTH = 88.dp
private val PILL_CORNER = 10.dp
private val TIME_SIZE = 17.sp
private val TIME_MIN_WIDTH = 52.dp
private val TIME_GAP = 8.dp
private val BADGE_GAP = 6.dp

/**
 * The row shared by the train, bus and departure board cards, so the three stay identical: the expected time large on the
 * left (the timetable time struck through under it when late), [content] in the middle, [badge] on the right, and a divider
 * above every row but a group's first. [timeWidth] and [badgeWidth], when given, fix those columns so the middle is the
 * same width on every row of the card.
 * 火车、公交与发车板共用的行，三张卡片才会始终一致：左边大字是预计时刻（晚点时下面划掉时刻表时间），中间是 [content]，
 * 右边是 [badge]，除每组第一行外上方都有分隔线。给了 [timeWidth] 与 [badgeWidth] 时这两列定宽，卡片每一行中间部分一样宽。
 */
@Composable
private fun TransitRow(
    expected: Long,
    aimed: Long,
    cancelled: Boolean,
    now: ZonedDateTime,
    divider: Boolean,
    timeWidth: Dp? = null,
    badgeWidth: Dp? = null,
    badge: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val time = rememberTimeFormatter()
    fun at(millis: Long) = Instant.ofEpochMilli(millis).atZone(now.zone).format(time)
    val faint = MaterialTheme.colorScheme.outline
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        // The timetable time goes under the expected one, which keeps the row narrow enough for 12-hour clocks.
        // 时刻表时间放在预计时间下面，12 小时制时这一行也放得下。
        Column(if (timeWidth != null) Modifier.width(timeWidth) else Modifier.widthIn(min = TIME_MIN_WIDTH)) {
            Text(
                text = at(expected),
                fontSize = TIME_SIZE,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (cancelled) faint else LocalContentColor.current,
                textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                maxLines = 1,
            )
            // Compared as shown: a delay of a few seconds would otherwise strike "08:24" under "08:24".
            // 按显示出来的时刻比较：否则晚几秒也会在「08:24」下面划掉「08:24」。
            val aimedText = at(aimed)
            if (!cancelled && aimedText != at(expected)) {
                Text(aimedText, style = MaterialTheme.typography.labelSmall, color = faint, textDecoration = TextDecoration.LineThrough, maxLines = 1)
            }
        }
        Spacer(Modifier.width(TIME_GAP))
        Column(Modifier.weight(1f), content = content)
        Spacer(Modifier.width(BADGE_GAP))
        if (badgeWidth != null) Box(Modifier.width(badgeWidth), contentAlignment = Alignment.CenterEnd) { badge() } else badge()
    }
}

/**
 * A line code as a small tinted tag, as on station signs. A replacement bus keeps the train's code, so it gets a bus icon
 * and a different tint. [compact] narrows the padding for the commute card's columns.
 * 线路号做成小底色标签，像车站的指示牌。替代巴士沿用火车的线路号，所以加巴士图标并换一种底色。[compact] 收窄内边距，
 * 用于通勤卡片的列。
 */
@Composable
private fun LineChip(line: String, replacementBus: Boolean = false, compact: Boolean = false) {
    val container = if (replacementBus) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (replacementBus) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(container, RoundedCornerShape(6.dp))
            .padding(horizontal = if (compact) LINE_CHIP_COMPACT_PADDING else LINE_CHIP_PADDING, vertical = 1.dp),
    ) {
        if (replacementBus) {
            Icon(
                painterResource(R.drawable.ic_bus),
                contentDescription = stringResource(R.string.transit_replacement_bus),
                tint = content,
                modifier = Modifier.size(LINE_CHIP_ICON).padding(end = 2.dp),
            )
        }
        Text(
            text = line.ifEmpty { "–" },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How wide a compact [LineChip] is for a leg, from the same padding and icon size it draws with. / 紧凑 [LineChip] 显示某一段时有多宽，用的是它绘制时同样的内边距与图标尺寸。 */
@Composable
private fun lineChipWidth(textWidth: TextWidth): (TransitLeg) -> Dp {
    val style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
    return { leg -> textWidth(leg.line.ifEmpty { "–" }, style) + LINE_CHIP_COMPACT_PADDING * 2 + if (leg.replacementBus) LINE_CHIP_ICON else 0.dp }
}

private val LINE_CHIP_PADDING = 6.dp
private val LINE_CHIP_COMPACT_PADDING = 4.dp
private val LINE_CHIP_ICON = 12.dp

/**
 * The favourite stops: the next departures of each, filtered as configured, in the same rows as the train and bus cards.
 * Without one it asks for one.
 * 收藏站点：每个站点按设置过滤后的下几班，行的样式与火车、公交卡片相同。一个都没有时提示去添加。
 */
@Composable
internal fun BoardsCard(
    snapshot: Snapshot<Boards>?,
    error: AppError?,
    isStale: (Snapshot<Boards>, Instant) -> Boolean,
    favourites: List<FavouriteBoard>,
) {
    val now = rememberMinuteTick()
    val usable = favourites.filter { it.stop.isSet }
    InfoCard(
        title = stringResource(R.string.transit_boards_title),
        icon = R.drawable.ic_transit,
        meta = if (usable.isEmpty()) null else snapshot?.let { updatedAgo(it.fetchedAt, now) },
        stale = usable.isNotEmpty() && snapshot?.let { isStale(it, now.toInstant()) } == true,
    ) {
        if (usable.isEmpty()) {
            Hint(stringResource(R.string.transit_boards_prompt), maxLines = 3)
            return@InfoCard
        }
        val results = snapshot?.value?.boards.orEmpty().associateBy { it.boardId }
        if (snapshot == null && error == null) SkeletonLines()
        val visible = usable.associate { board -> board.id to results[board.id]?.let { TransitPolicy.visibleDepartures(it.departures, now) } }
        // One time column for the whole card, so 12-hour times do not shift some rows. / 整张卡片共用一个时刻列宽，12 小时制时各行不会错开。
        val timeWidth = timeColumnWidth(visible.values.filterNotNull().flatten().map { it.expected to it.aimed }, now, rememberTextWidth())
        usable.forEach { board ->
            val departures = visible[board.id] ?: return@forEach
            Text(
                text = board.stop.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (departures.isEmpty()) Hint(stringResource(R.string.transit_no_departures))
            departures.forEachIndexed { i, departure -> DepartureRow(departure, now, timeWidth, divider = i > 0) }
        }
        if (error != null) ErrorLine(error)
        Attribution()
    }
}

/**
 * One departure in a [TransitRow]: the line and destination with the countdown within the hour, the platform under them.
 * 一班车，用 [TransitRow] 排：线路与终点、一小时内再加倒计时，下面是站台。
 */
@Composable
private fun DepartureRow(departure: BoardDeparture, now: ZonedDateTime, timeWidth: Dp, divider: Boolean) {
    val status = TransitPolicy.legStatus(departure)
    val cancelled = status.state == LegState.CANCELLED
    TransitRow(
        expected = departure.expected,
        aimed = departure.aimed,
        cancelled = cancelled,
        now = now,
        divider = divider,
        timeWidth = timeWidth,
        badge = { StatusBadge(status) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LineChip(departure.line, departure.replacementBus)
            val color = if (cancelled) MaterialTheme.colorScheme.outline else LocalContentColor.current
            val decoration = if (cancelled) TextDecoration.LineThrough else null
            // A long destination gives way first, so the countdown stays readable.
            // 终点名太长时先省略它，倒计时始终看得见。
            Text(
                text = "→ ${departure.frontText}",
                style = MaterialTheme.typography.bodySmall,
                color = color,
                textDecoration = decoration,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            TransitPolicy.minutesUntil(departure.expected, now)?.let { minutes ->
                Text(
                    text = "· " + if (minutes < 1) stringResource(R.string.transit_now) else pluralStringResource(R.plurals.transit_minutes, minutes, minutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    textDecoration = decoration,
                    maxLines = 1,
                )
            }
        }
        val detail = listOfNotNull(
            stringResource(R.string.transit_replacement_bus).takeIf { departure.replacementBus },
            departure.platform.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.transit_platform, it) },
        )
        if (detail.isNotEmpty()) Hint(detail.joinToString(" · "))
    }
}

/** A status pill's text and colour. / 状态胶囊的文字与颜色。 */
private class StatusLabel(val text: String, val color: Color)

/**
 * The four states, each with its own colour; the wording alone tells them apart, so no leading symbol is added. No real-time
 * data is grey, never green. A [prefix] line goes before the status on the same line.
 * 四种状态各有颜色；文字本身就能区分，不加前置符号。没有实时数据是灰色，绝不是绿色。[prefix] 线路写在状态前面，同一行。
 */
@Composable
private fun statusLabel(status: LegStatus, prefix: String? = null): StatusLabel {
    val (text, color) = when (status.state) {
        LegState.ON_TIME -> stringResource(R.string.transit_on_time) to MaterialTheme.statusColors.green
        LegState.DELAYED -> pluralStringResource(R.plurals.transit_late, status.delayMinutes, status.delayMinutes) to MaterialTheme.statusColors.amber
        LegState.NO_REALTIME -> stringResource(R.string.transit_no_realtime) to MaterialTheme.statusColors.gray
        LegState.CANCELLED -> stringResource(R.string.transit_cancelled) to MaterialTheme.statusColors.red
    }
    return StatusLabel(if (prefix.isNullOrEmpty()) text else stringResource(R.string.transit_line_status_compact, prefix, text), color)
}

@Composable
private fun StatusBadge(status: LegStatus) = statusLabel(status).let { StatusChip(it.text, it.color) }

@Composable
private fun Hint(text: String, maxLines: Int = 1, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Hint(AnnotatedString(text), maxLines, color)

@Composable
private fun Hint(text: AnnotatedString, maxLines: Int = 1, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ErrorLine(error: AppError) {
    Text(error.userMessage().asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.statusColors.red)
}

@Composable
private fun Attribution() {
    Text(
        stringResource(R.string.transit_attribution),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * The commute rows at 360 and 384 dp (and larger fonts or 320 dp, where [keepPlatforms] drops the platform pills): a late
 * first train with a faster change folded over its direct option, an on-time change and a direct train without live data, to
 * check that the rows fit in both languages. The first train is held for a late opposing train, so its row names it, and the
 * on-time change may still have to wait for another one. Stops are made up.
 * 360 与 384 dp 宽（以及大字号或 320 dp，这时 [keepPlatforms] 去掉站台胶囊）的通勤行：首车晚点、换乘更快并叠在直达上面的
 * 一行，准点换乘一行，没有实时数据的直达一行，用来检查两种语言下都放得下。首车在等晚点的对向车会车，所以第一行下面写出对向车；
 * 准点的换乘也可能要等另一班晚点的对向车。站名是虚构的。
 */
@Preview(name = "en", widthDp = 360, locale = "en")
@Preview(name = "zh", widthDp = 360, locale = "zh")
@Preview(name = "zh-384", widthDp = 384, locale = "zh")
@Preview(name = "en-large", widthDp = 360, locale = "en", fontScale = 1.3f)
@Preview(name = "en-narrow", widthDp = 320, locale = "en")
@Composable
private fun CommuteRowsPreview() {
    val now = ZonedDateTime.of(2026, 10, 20, 8, 20, 0, 0, ZoneId.of("Europe/Oslo"))
    fun at(h: Int, m: Int) = now.withHour(h).withMinute(m).toInstant().toEpochMilli()
    fun ride(line: String, dep: Long, late: Int, arr: Long, from: String, to: String, platform: String, realtime: Boolean = true) = TransitLeg(
        mode = "rail", line = line, fromName = from, toName = to,
        aimedDeparture = dep, expectedDeparture = dep + late * 60_000L, aimedArrival = arr, expectedArrival = arr + late * 60_000L,
        realtime = realtime, platform = platform,
    )
    val all = listOf(
        TripOption(listOf(ride("L1", at(8, 24), 6, at(8, 52), "Lindvik stasjon", "Nordby stasjon", "1"))),
        TripOption(
            listOf(
                ride("L1", at(8, 24), 6, at(8, 36), "Lindvik stasjon", "Sentrum stasjon", "1"),
                ride("R12", at(8, 44), 0, at(8, 54), "Sentrum stasjon", "Nordby stasjon", "3"),
            ),
            alternative = true,
        ),
        TripOption(
            listOf(
                ride("L1", at(8, 55), 0, at(9, 7), "Lindvik stasjon", "Sentrum stasjon", "1"),
                ride("R12", at(9, 20), 0, at(9, 30), "Sentrum stasjon", "Nordby stasjon", "3"),
            ),
        ),
        TripOption(listOf(ride("L1", at(9, 25), 0, at(9, 58), "Lindvik stasjon", "Nordby stasjon", "1", realtime = false))),
    )
    val opposing = listOf(
        BoardDeparture(line = "L1", mode = "rail", frontText = "Østby", platform = "2", aimed = at(8, 25), expected = at(8, 30), realtime = true),
        BoardDeparture(line = "L1", mode = "rail", frontText = "Østby", platform = "2", aimed = at(8, 56), expected = at(9, 2), realtime = true),
    )
    DayloomTheme {
        Column(Modifier.padding(12.dp)) {
            InfoCard(title = stringResource(R.string.transit_train_title)) { OptionList(all, 3, now, fetchedAt = null, opposing = opposing) }
        }
    }
}
