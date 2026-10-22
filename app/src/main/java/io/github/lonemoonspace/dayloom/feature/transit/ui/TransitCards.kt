package io.github.lonemoonspace.dayloom.feature.transit.ui

import androidx.annotation.StringRes
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    options.forEachIndexed { i, option -> OptionRow(option, TransitPolicy.foldedDirect(option, all), now, divider = i > 0) }
}

/**
 * One option in one row: the expected departure large on the left (the timetable time struck through under it when late),
 * the route in the middle, the status pill on the right. A transfer that replaced a direct option on the same first train
 * shows that direct option dimmed on a second line, so the two arrivals line up for comparison. Cancelled options stay,
 * struck through, so the user knows why they are gone.
 * 一个方案一行：左边大字是预计出发时刻（晚点时下面划掉时刻表时间），中间是路线，右边是状态胶囊。取代了同一班首车直达方案的
 * 换乘方案，在第二行淡色显示那个直达方案，两个到达时刻上下对齐便于比较。被取消的方案保留并划掉，用户才知道它为什么没了。
 */
@Composable
private fun OptionRow(option: TripOption, folded: TripOption?, now: ZonedDateTime, divider: Boolean) {
    val first = option.legs.first()
    val worst = TransitPolicy.worst(option)
    val cancelled = worst?.second?.state == LegState.CANCELLED
    TransitRow(
        expected = option.departure,
        aimed = first.aimedDeparture,
        cancelled = cancelled,
        now = now,
        divider = divider,
        badge = {
            worst?.let { (leg, status) ->
                // With several legs, name the line the status belongs to. / 多段时说明状态属于哪条线。
                StatusBadge(status, prefix = leg.line.takeIf { option.legs.size > 1 && status.state != LegState.ON_TIME })
            }
        },
    ) {
        RouteLine(option, now, struck = cancelled)
        if (folded != null) RouteLine(folded, now, struck = false, modifier = Modifier.padding(top = 2.dp).alpha(0.6f))
    }
}

/**
 * One route in five columns, the same widths on every line of the card so they stay aligned: first line, then the transfer
 * stop, the platform there and the line to change to, then the arrival. A direct route reads "L1 · Direct · Pl. 1" with
 * the platform it leaves from. Only the arrival column flexes, so a wider status pill never shifts the others; the duration
 * and the gain over the direct option are left out, the arrivals already show them and there is no room at 360 dp.
 * 一条路线分五列，整张卡片每一行列宽相同，上下对齐：先坐的线路、换乘站、在那里的站台、换乘的线路，最后是到达时刻。直达写成
 * 「L1 · 直达 · 1站台」，站台是出发站台。只有到达这一列会伸缩，状态胶囊宽一些也不会挤动其他列；用时和比直达早多少都不写，
 * 到达时刻已经说明，360 dp 宽也放不下。
 */
@Composable
private fun RouteLine(option: TripOption, now: ZonedDateTime, struck: Boolean, modifier: Modifier = Modifier) {
    val time = rememberTimeFormatter()
    val color = if (struck) MaterialTheme.colorScheme.outline else LocalContentColor.current
    val decoration = if (struck) TextDecoration.LineThrough else null
    val first = option.legs.first()
    val rest = option.legs.drop(1)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        // Line chips and platforms may grow past their column rather than lose text; stop names give way instead.
        // 线路标签和站台宁可超出列宽也不截字；站名则让位省略。
        Box(Modifier.widthIn(min = FIRST_LINE_WIDTH)) { LineChip(first.line, first.replacementBus, compact = true) }
        val stop = if (rest.isEmpty()) {
            stringResource(R.string.transit_direct)
        } else {
            option.legs.dropLast(1).map { TransitPolicy.shortStopName(it.toName) }.distinct().joinToString(", ")
        }
        RouteCell(stop, color, decoration, Modifier.width(STOP_WIDTH))
        val platform = (rest.firstOrNull() ?: first).platform
        RouteCell(
            if (platform.isEmpty()) "" else stringResource(R.string.transit_platform, platform),
            color,
            decoration,
            Modifier.widthIn(min = PLATFORM_WIDTH),
        )
        Row(Modifier.widthIn(min = NEXT_LINE_WIDTH), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            rest.forEach { LineChip(it.line, it.replacementBus, compact = true) }
        }
        RouteCell(
            "→ " + Instant.ofEpochMilli(option.arrival).atZone(now.zone).format(time),
            color,
            decoration,
            Modifier.weight(1f),
        )
    }
}

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

/** Column widths of [RouteLine], sized for "R13", "Asker", "3站台" / "Pl. 3" and "RE10". / [RouteLine] 的列宽，按「R13」「Asker」「3站台」/「Pl. 3」「RE10」定。 */
private val FIRST_LINE_WIDTH = 32.dp
private val STOP_WIDTH = 34.dp
private val PLATFORM_WIDTH = 32.dp
private val NEXT_LINE_WIDTH = 38.dp

/**
 * The row shared by the train, bus and departure board cards, so the three stay identical: the expected time large on the
 * left (the timetable time struck through under it when late), [content] in the middle, [badge] on the right, and a divider
 * above every row but a group's first.
 * 火车、公交与发车板共用的行，三张卡片才会始终一致：左边大字是预计时刻（晚点时下面划掉时刻表时间），中间是 [content]，
 * 右边是 [badge]，除每组第一行外上方都有分隔线。
 */
@Composable
private fun TransitRow(
    expected: Long,
    aimed: Long,
    cancelled: Boolean,
    now: ZonedDateTime,
    divider: Boolean,
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
        Column(Modifier.widthIn(min = 52.dp)) {
            Text(
                text = at(expected),
                fontSize = 17.sp,
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
        badge()
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

/**
 * The four states, each with its own colour; the wording alone tells them apart, so no leading symbol is added. No real-time
 * data is grey, never green.
 * 四种状态各有颜色；文字本身就能区分，不加前置符号。没有实时数据是灰色，绝不是绿色。
 */
@Composable
private fun StatusBadge(status: LegStatus, prefix: String? = null) {
    val (text, color) = when (status.state) {
        LegState.ON_TIME -> stringResource(R.string.transit_on_time) to MaterialTheme.statusColors.green
        LegState.DELAYED -> pluralStringResource(R.plurals.transit_late, status.delayMinutes, status.delayMinutes) to MaterialTheme.statusColors.amber
        LegState.NO_REALTIME -> stringResource(R.string.transit_no_realtime) to MaterialTheme.statusColors.gray
        LegState.CANCELLED -> stringResource(R.string.transit_cancelled) to MaterialTheme.statusColors.red
    }
    // The line goes above the status: side by side the pill would take the room the route columns need.
    // 线路写在状态上面：并排的话胶囊会占掉路线各列需要的宽度。
    if (prefix.isNullOrEmpty()) StatusChip(text, color) else StatusChip("$prefix\n$text", color, maxLines = 2)
}

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
