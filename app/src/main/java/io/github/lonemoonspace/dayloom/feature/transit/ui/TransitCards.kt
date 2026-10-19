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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransferGain
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransferGainKind
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitPolicy
import io.github.lonemoonspace.dayloom.feature.transit.domain.TripOption
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/**
 * One commute card (train or bus): the next options towards the destination before noon, back from noon on. One row per
 * option: departure time, lines, arrival and duration, transfers and platform, and the worst real-time status of its legs.
 * 一张通勤卡片（火车或公交）：中午前显示去程、中午起显示回程的接下来几个方案。每个方案一行：出发时刻、线路、到达与用时、
 * 换乘与站台，以及各段中最差的实时状态。
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
            CommuteMode.OUTBOUND -> OptionList(trips.outbound, route.options, now)
            CommuteMode.INBOUND -> OptionList(trips.inbound, route.options, now)
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

/** [all] is the snapshot's list: a transfer's gain is measured against direct options it may have replaced. / [all] 是快照里的列表：换乘的收益要和可能被它取代的直达方案比。 */
@Composable
private fun OptionList(all: List<TripOption>, limit: Int, now: ZonedDateTime) {
    val options = TransitPolicy.visibleOptions(all, now, limit)
    if (options.isEmpty()) {
        Hint(stringResource(R.string.transit_no_options))
        return
    }
    options.forEachIndexed { i, option -> OptionRow(option, TransitPolicy.transferGain(option, all, now), now, divider = i > 0) }
}

/**
 * One option in one row: the expected departure large on the left (the timetable time struck through under it when late),
 * lines, arrival and duration in the middle with transfers and platform under them, the status pill on the right.
 * Cancelled options stay, struck through, so the user knows why they are gone.
 * 一个方案一行：左边大字是预计出发时刻（晚点时下面划掉时刻表时间），中间是线路、到达与用时，下面是换乘与站台，右边是状态胶囊。
 * 被取消的方案保留并划掉，用户才知道它为什么没了。
 */
@Composable
private fun OptionRow(option: TripOption, gain: TransferGain?, now: ZonedDateTime, divider: Boolean) {
    val time = rememberTimeFormatter()
    fun at(millis: Long) = Instant.ofEpochMilli(millis).atZone(now.zone).format(time)
    val first = option.legs.first()
    val worst = TransitPolicy.worst(option)
    val cancelled = worst?.second?.state == LegState.CANCELLED
    val minutes = Duration.ofMillis(option.arrival - option.departure).toMinutes().toInt().coerceAtLeast(0)
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            option.legs.forEach { LineChip(it.line, it.replacementBus) }
            Text(
                text = "→ ${at(option.arrival)} · " + pluralStringResource(R.plurals.transit_minutes, minutes, minutes),
                style = MaterialTheme.typography.bodySmall,
                color = if (cancelled) MaterialTheme.colorScheme.outline else LocalContentColor.current,
                textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Hint(detailText(option, gain))
    }
}

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
 * "Direct · Pl. 4" or "Change at Asker · 8 min earlier · Pl. 4"; the gain is green, the only colour in the line.
 * 「直达 · 4 站台」或「在 Asker 换乘 · 早到 8 分钟 · 4 站台」；收益用绿色，是这一行里唯一的颜色。
 */
@Composable
private fun detailText(option: TripOption, gain: TransferGain?): AnnotatedString {
    val route = if (option.transfers == 0) {
        stringResource(R.string.transit_direct)
    } else {
        pluralStringResource(R.plurals.transit_transfers, option.transfers, option.legs.dropLast(1).map { it.toName }.distinct().joinToString(", "))
    }
    val gainText = when (gain?.kind) {
        null -> null
        TransferGainKind.ARRIVE_EARLIER -> pluralStringResource(R.plurals.transit_arrive_earlier, gain.minutes, gain.minutes)
        TransferGainKind.LEAVE_LATER -> pluralStringResource(R.plurals.transit_leave_later, gain.minutes, gain.minutes)
    }
    val green = MaterialTheme.statusColors.green
    val platform = option.legs.first().platform
    val parts = listOfNotNull(
        // Said in words too: the bus leaves from a street stop, not the platform the user would head for.
        // 也用文字说明：巴士从街边站点出发，而不是用户平时去的站台。
        stringResource(R.string.transit_replacement_bus).takeIf { option.legs.any { it.replacementBus } }?.let { AnnotatedString(it) },
        AnnotatedString(route),
        gainText?.let { text -> buildAnnotatedString { withStyle(SpanStyle(color = green)) { append(text) } } },
        platform.takeIf { it.isNotEmpty() }?.let { AnnotatedString(stringResource(R.string.transit_platform, it)) },
    )
    return buildAnnotatedString {
        parts.forEachIndexed { i, part ->
            if (i > 0) append(" · ")
            append(part)
        }
    }
}

/**
 * A line code as a small tinted tag, as on station signs. A replacement bus keeps the train's code, so it gets a bus icon
 * and a different tint.
 * 线路号做成小底色标签，像车站的指示牌。替代巴士沿用火车的线路号，所以加巴士图标并换一种底色。
 */
@Composable
private fun LineChip(line: String, replacementBus: Boolean = false) {
    val container = if (replacementBus) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (replacementBus) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(container, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
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
    StatusChip(if (prefix.isNullOrEmpty()) text else stringResource(R.string.transit_line_status, prefix, text), color)
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
