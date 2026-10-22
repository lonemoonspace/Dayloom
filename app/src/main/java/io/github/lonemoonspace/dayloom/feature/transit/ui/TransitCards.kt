package io.github.lonemoonspace.dayloom.feature.transit.ui

import androidx.annotation.StringRes
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.i18n.asString
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.SkeletonLines
import io.github.lonemoonspace.dayloom.core.ui.StatusChip
import io.github.lonemoonspace.dayloom.core.ui.rememberMinuteTick
import io.github.lonemoonspace.dayloom.core.ui.rememberTimeFormatter
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
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitLeg
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitPolicy
import io.github.lonemoonspace.dayloom.feature.transit.domain.TripOption
import java.time.Instant
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
            CommuteMode.OUTBOUND -> OptionList(trips.outbound, route.options, now, snapshot?.fetchedAt)
            CommuteMode.INBOUND -> OptionList(trips.inbound, route.options, now, snapshot?.fetchedAt)
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
private fun OptionList(all: List<TripOption>, limit: Int, now: ZonedDateTime, fetchedAt: Long?) {
    val options = TransitPolicy.visibleOptions(all, now, limit, fetchedAt)
    if (options.isEmpty()) {
        Hint(stringResource(R.string.transit_no_options))
        return
    }
    val rows = options.map { option ->
        val worst = TransitPolicy.worst(option)
        CommuteRow(
            option = option,
            folded = TransitPolicy.foldedDirect(option, all),
            // With several legs, name the line the status belongs to. / 多段时说明状态属于哪条线。
            status = worst?.let { (leg, status) -> statusLabel(status, leg.line.takeIf { option.legs.size > 1 && status.state != LegState.ON_TIME }) },
            cancelled = worst?.second?.state == LegState.CANCELLED,
        )
    }
    val columns = rememberRouteColumns(rows, now)
    rows.forEachIndexed { i, row -> OptionRow(row, columns, now, divider = i > 0) }
}

/** One option as the card shows it, worked out before measuring so the columns fit every row. / 卡片上的一个方案，先算好再量列宽，列宽才能容下每一行。 */
private class CommuteRow(val option: TripOption, val folded: TripOption?, val status: StatusLabel?, val cancelled: Boolean)

/**
 * One option in one row: the expected departure large on the left (the timetable time struck through under it when late),
 * the route in the middle, the status pill on the right. A transfer that replaced a direct option on the same first train
 * shows that direct option dimmed on a second line, so the two arrivals line up for comparison. Cancelled options stay,
 * struck through, so the user knows why they are gone.
 * 一个方案一行：左边大字是预计出发时刻（晚点时下面划掉时刻表时间），中间是路线，右边是状态胶囊。取代了同一班首车直达方案的
 * 换乘方案，在第二行淡色显示那个直达方案，两个到达时刻上下对齐便于比较。被取消的方案保留并划掉，用户才知道它为什么没了。
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
        badge = { row.status?.let { StatusChip(it.text, it.color, maxLines = 2) } },
    ) {
        RouteLine(row.option, columns, now, struck = row.cancelled)
        if (row.folded != null) {
            RouteLine(row.folded, columns, now, struck = false, modifier = Modifier.padding(top = 2.dp).alpha(FOLDED_ALPHA))
        }
    }
}

/** Dimmed enough to read as the alternative, still about 4.5:1 on the card. / 淡到看得出是备选，在卡片上仍约有 4.5:1 的对比度。 */
private const val FOLDED_ALPHA = 0.7f

/**
 * Column widths for one card, measured from the texts it shows, so every line of every row aligns: the time column, the
 * first line, the platform, the line changed to, the arrival and the status pill. Each fits its widest entry (a column
 * nobody uses takes no room), and the stop name takes what is left, giving way first when the card is narrow. The pill
 * is capped and wraps to two lines beyond that, so a long status cannot squeeze the route.
 * 一张卡片的列宽，按卡片上实际显示的文字量出来，每一行的每一列才都对得齐：时刻列、先坐的线路、站台、换乘的线路、到达时刻、
 * 状态胶囊。每列取最宽的那一项（没人用的列不占地方），站名列分剩下的宽度，卡片窄时最先省略。胶囊有上限，超过就折成两行，
 * 长状态不会挤掉路线。
 */
private class RouteColumns(val time: Dp, val first: Dp, val platform: Dp, val next: Dp, val arrival: Dp, val badge: Dp)

@Composable
private fun rememberRouteColumns(rows: List<CommuteRow>, now: ZonedDateTime): RouteColumns {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val time = rememberTimeFormatter()
    fun at(millis: Long) = Instant.ofEpochMilli(millis).atZone(now.zone).format(time)
    val typography = MaterialTheme.typography
    fun width(text: String, style: TextStyle): Dp = with(density) { measurer.measure(text, style, softWrap = false).size.width.toDp() }
    val chipStyle = typography.labelMedium.copy(fontWeight = FontWeight.Bold)
    fun chip(leg: TransitLeg): Dp = width(leg.line.ifEmpty { "–" }, chipStyle) + COMPACT_CHIP_PADDING * 2 + if (leg.replacementBus) CHIP_ICON else 0.dp
    fun chips(legs: List<TransitLeg>): Dp = if (legs.isEmpty()) 0.dp else legs.fold(0.dp) { sum, leg -> sum + chip(leg) } + ROUTE_GAP * (legs.size - 1)
    val lines = rows.flatMap { listOfNotNull(it.option, it.folded) }
    val platforms = lines.mapNotNull { routePlatform(it).takeIf { p -> p.isNotEmpty() } }.map { stringResource(R.string.transit_platform, it) }
    val timeStyle = LocalTextStyle.current.copy(fontSize = TIME_SIZE, fontWeight = FontWeight.Bold)
    return RouteColumns(
        time = rows.maxOf {
            maxOf(width(at(it.option.departure), timeStyle), width(at(it.option.legs.first().aimedDeparture), typography.labelSmall))
        }.coerceAtLeast(TIME_MIN_WIDTH),
        first = lines.maxOf { chip(it.legs.first()) },
        platform = platforms.maxOfOrNull { width(it, typography.bodySmall) } ?: 0.dp,
        next = lines.maxOf { chips(it.legs.drop(1)) },
        arrival = lines.maxOf { width(arrivalText(it, at(it.arrival)), typography.bodySmall) },
        badge = rows.mapNotNull { it.status }.maxOfOrNull { width(it.text, typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)) + STATUS_PADDING * 2 }
            ?.coerceAtMost(BADGE_MAX_WIDTH) ?: 0.dp,
    )
}

/**
 * One route on one line, in the card's [RouteColumns]: first line, transfer stop, the platform there, the line changed to,
 * then the arrival, e.g. "L1 Asker Pl. 3 R12 → 08:54". A direct route reads "L1 Direct Pl. 1 → 08:58" with the platform it
 * leaves from. The duration and the gain over the direct option are left out: the arrivals already show them, and at
 * 360 dp there is no room.
 * 一条路线一行，按卡片的 [RouteColumns] 排：先坐的线路、换乘站、在那里的站台、换乘的线路，然后是到达时刻，如
 * 「L1 Asker 3站台 R12 → 08:54」。直达写成「L1 直达 1站台 → 08:58」，站台是出发站台。用时和比直达早多少都不写：到达时刻
 * 已经说明，360 dp 宽也放不下。
 */
@Composable
private fun RouteLine(option: TripOption, columns: RouteColumns, now: ZonedDateTime, struck: Boolean, modifier: Modifier = Modifier) {
    val time = rememberTimeFormatter()
    val color = if (struck) MaterialTheme.colorScheme.outline else LocalContentColor.current
    val decoration = if (struck) TextDecoration.LineThrough else null
    val first = option.legs.first()
    val rest = option.legs.drop(1)
    // Read out as one sentence rather than five fragments. / 读屏时念成一句，而不是五个碎片。
    Row(
        modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROUTE_GAP),
    ) {
        Box(Modifier.width(columns.first)) { LineChip(first.line, first.replacementBus, compact = true) }
        val stop = if (rest.isEmpty()) {
            stringResource(R.string.transit_direct)
        } else {
            // Several changes: the last stop is where the final train is boarded, the one worth naming.
            // 多次换乘时写最后一个换乘站：在那里上最后一班车，最值得写出来。
            TransitPolicy.shortStopName(option.legs[option.legs.size - 2].toName)
        }
        RouteCell(stop, color, decoration, Modifier.weight(1f))
        if (columns.platform > 0.dp) {
            val platform = routePlatform(option)
            if (platform.isEmpty()) {
                Spacer(Modifier.width(columns.platform))
            } else {
                RouteCell(stringResource(R.string.transit_platform, platform), color, decoration, Modifier.width(columns.platform))
            }
        }
        if (columns.next > 0.dp) {
            Row(Modifier.width(columns.next), horizontalArrangement = Arrangement.spacedBy(ROUTE_GAP)) {
                rest.forEach { LineChip(it.line, it.replacementBus, compact = true) }
            }
        }
        val arrival = Instant.ofEpochMilli(option.arrival).atZone(now.zone).format(time)
        RouteCell(arrivalText(option, arrival), color, decoration, Modifier.width(columns.arrival))
    }
}

/** The platform of a direct route's train, or of the first train changed to. / 直达是出发站台，换乘是第一次换乘那班车的站台。 */
private fun routePlatform(option: TripOption): String = (option.legs.getOrNull(1) ?: option.legs.first()).platform

private fun arrivalText(option: TripOption, arrival: String): String = "→ $arrival"

@Composable
private fun RouteCell(text: String, color: Color, decoration: TextDecoration?, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textDecoration = decoration,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private val ROUTE_GAP = 3.dp
private val COMPACT_CHIP_PADDING = 4.dp
private val CHIP_ICON = 12.dp
private val STATUS_PADDING = 8.dp

/** Wide enough for "L1 / 5 min late" stacked; anything wider wraps. / 够放叠成两行的「L1 / 晚 5 分」；更宽的就折行。 */
private val BADGE_MAX_WIDTH = 72.dp
private val TIME_SIZE = 17.sp
private val TIME_MIN_WIDTH = 52.dp

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
            if (!cancelled && expected != aimed) {
                Text(at(aimed), style = MaterialTheme.typography.labelSmall, color = faint, textDecoration = TextDecoration.LineThrough, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), content = content)
        Spacer(Modifier.width(6.dp))
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
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 1.dp),
    ) {
        if (replacementBus) {
            Icon(
                painterResource(R.drawable.ic_bus),
                contentDescription = stringResource(R.string.transit_replacement_bus),
                tint = content,
                modifier = Modifier.size(12.dp).padding(end = 2.dp),
            )
        }
        Text(
            text = line.ifEmpty { "–" },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
        )
    }
}

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
        usable.forEach { board ->
            val result = results[board.id] ?: return@forEach
            Text(
                text = board.stop.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
            val visible = TransitPolicy.visibleDepartures(result.departures, now)
            if (visible.isEmpty()) Hint(stringResource(R.string.transit_no_departures))
            visible.forEachIndexed { i, departure -> DepartureRow(departure, now, divider = i > 0) }
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
private fun DepartureRow(departure: BoardDeparture, now: ZonedDateTime, divider: Boolean) {
    val status = TransitPolicy.legStatus(departure)
    val cancelled = status.state == LegState.CANCELLED
    TransitRow(
        expected = departure.expected,
        aimed = departure.aimed,
        cancelled = cancelled,
        now = now,
        divider = divider,
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
 * data is grey, never green. A [prefix] line goes above the status: side by side the pill would take the room the route
 * columns need.
 * 四种状态各有颜色；文字本身就能区分，不加前置符号。没有实时数据是灰色，绝不是绿色。[prefix] 线路写在状态上面：并排的话
 * 胶囊会占掉路线各列需要的宽度。
 */
@Composable
private fun statusLabel(status: LegStatus, prefix: String? = null): StatusLabel {
    val (text, color) = when (status.state) {
        LegState.ON_TIME -> stringResource(R.string.transit_on_time) to MaterialTheme.statusColors.green
        LegState.DELAYED -> pluralStringResource(R.plurals.transit_late, status.delayMinutes, status.delayMinutes) to MaterialTheme.statusColors.amber
        LegState.NO_REALTIME -> stringResource(R.string.transit_no_realtime) to MaterialTheme.statusColors.gray
        LegState.CANCELLED -> stringResource(R.string.transit_cancelled) to MaterialTheme.statusColors.red
    }
    return StatusLabel(if (prefix.isNullOrEmpty()) text else stringResource(R.string.transit_line_status_stacked, prefix, text), color)
}

@Composable
private fun StatusBadge(status: LegStatus) = statusLabel(status).let { StatusChip(it.text, it.color) }

@Composable
private fun Hint(text: String, maxLines: Int = 1) = Hint(AnnotatedString(text), maxLines)

@Composable
private fun Hint(text: AnnotatedString, maxLines: Int = 1) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
