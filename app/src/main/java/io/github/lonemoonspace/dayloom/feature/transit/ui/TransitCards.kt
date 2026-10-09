package io.github.lonemoonspace.dayloom.feature.transit.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/**
 * One commute card (train or bus): inside a daily window the next options in that direction, otherwise the next option each
 * way. Each option shows its times, transfers and the worst real-time status of its legs.
 * 一张通勤卡片（火车或公交）：日常时间窗内显示该方向的接下来几个方案，时间窗外两个方向各显示下一个。每个方案显示时刻、换乘，
 * 以及各段中最差的实时状态。
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
    val kindName = stringResource(kindTitle(kind))
    val title = when (trips?.mode) {
        CommuteMode.OUTBOUND -> stringResource(R.string.transit_card_title, kindName, stringResource(R.string.transit_to_work, origin.name, destination.name))
        CommuteMode.INBOUND -> stringResource(R.string.transit_card_title, kindName, stringResource(R.string.transit_back_home, destination.name, origin.name))
        else -> kindName
    }
    InfoCard(title = title, stale = route.isSet && snapshot?.let { isStale(it, now.toInstant()) } == true) {
        if (!route.isSet) {
            Hint(stringResource(R.string.transit_setup_prompt), maxLines = 3)
            return@InfoCard
        }
        val options = route.options
        when (trips?.mode) {
            null -> if (error == null) SkeletonLines()
            CommuteMode.OUTBOUND -> OptionList(TransitPolicy.visibleOptions(trips.outbound, now, options), now)
            CommuteMode.INBOUND -> OptionList(TransitPolicy.visibleOptions(trips.inbound, now, options), now)
            CommuteMode.BOTH -> {
                Direction(stringResource(R.string.transit_direction, origin.name, destination.name))
                OptionList(TransitPolicy.visibleOptions(trips.outbound, now, 1), now)
                Direction(stringResource(R.string.transit_direction, destination.name, origin.name))
                OptionList(TransitPolicy.visibleOptions(trips.inbound, now, 1), now)
            }
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

@Composable
private fun OptionList(options: List<TripOption>, now: ZonedDateTime) {
    if (options.isEmpty()) {
        Hint(stringResource(R.string.transit_no_options))
        return
    }
    options.forEach { OptionRow(it, now) }
}

@Composable
private fun OptionRow(option: TripOption, now: ZonedDateTime) {
    val time = rememberTimeFormatter()
    fun at(millis: Long) = Instant.ofEpochMilli(millis).atZone(now.zone).format(time)
    val minutes = Duration.ofMillis(option.arrival - option.departure).toMinutes().toInt().coerceAtLeast(0)
    Column(Modifier.padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${at(option.departure)} → ${at(option.arrival)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                pluralStringResource(R.plurals.transit_minutes, minutes, minutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TransitPolicy.worst(option)?.let { (leg, status) ->
                // With several legs, name the line the status belongs to. / 多段时说明状态属于哪条线。
                StatusBadge(status, prefix = leg.line.takeIf { option.legs.size > 1 && status.state != LegState.ON_TIME })
            }
        }
        Hint(legsText(option), maxLines = 2)
    }
}

/** "R13 · direct" or "F6 → R13 · change at Oslo S". / 「R13 · 直达」或「F6 → R13 · 在 Oslo S 换乘」。 */
@Composable
private fun legsText(option: TripOption): String {
    val lines = option.legs.joinToString(" → ") { it.line.ifEmpty { "–" } }
    if (option.transfers == 0) return stringResource(R.string.transit_direct, lines)
    val stops = option.legs.dropLast(1).map { it.toName }.distinct().joinToString(", ")
    return pluralStringResource(R.plurals.transit_transfers, option.transfers, lines, stops)
}

/**
 * The favourite stops: the next departures of each, filtered as configured, with a countdown.
 * 收藏站点：每个站点按设置过滤后的下几班，带倒计时。
 */
@Composable
internal fun BoardsCard(
    snapshot: Snapshot<Boards>?,
    error: AppError?,
    isStale: (Snapshot<Boards>, Instant) -> Boolean,
    favourites: List<FavouriteBoard>,
) {
    val now = rememberMinuteTick()
    InfoCard(title = stringResource(R.string.transit_boards_title), stale = snapshot?.let { isStale(it, now.toInstant()) } == true) {
        val results = snapshot?.value?.boards.orEmpty().associateBy { it.boardId }
        if (snapshot == null && error == null) SkeletonLines()
        favourites.filter { it.stop.isSet }.forEach { board ->
            val result = results[board.id] ?: return@forEach
            Text(
                text = board.stop.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
            val visible = TransitPolicy.visibleDepartures(result.departures, now)
            if (visible.isEmpty()) Hint(stringResource(R.string.transit_no_departures))
            visible.forEach { DepartureRow(it, now) }
        }
        if (error != null) ErrorLine(error)
        Attribution()
    }
}

@Composable
private fun DepartureRow(departure: BoardDeparture, now: ZonedDateTime) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            departure.line.ifEmpty { "–" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 40.dp),
        )
        Text(
            departure.frontText,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (departure.platform.isNotEmpty()) {
            Text(
                stringResource(R.string.transit_platform, departure.platform),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(countdown(departure.expected, now), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        val status = TransitPolicy.legStatus(departure)
        // On time is the normal case; only deviations get a badge, which keeps a busy board readable.
        // 准点是常态；只有异常才加徽标，繁忙的发车板才看得清。
        if (status.state != LegState.ON_TIME) {
            Spacer(Modifier.width(6.dp))
            StatusBadge(status)
        }
    }
}

/** "Now", "7 min" within the hour, the clock time after that. / 「现在」，一小时内显示「7 分钟」，之后显示时刻。 */
@Composable
private fun countdown(millis: Long, now: ZonedDateTime): String {
    val minutes = Duration.between(now.toInstant(), Instant.ofEpochMilli(millis)).toMinutes().toInt()
    return when {
        minutes < 1 -> stringResource(R.string.transit_now)
        minutes < 60 -> pluralStringResource(R.plurals.transit_minutes, minutes, minutes)
        else -> Instant.ofEpochMilli(millis).atZone(now.zone).format(rememberTimeFormatter())
    }
}

/**
 * The four states, each with its own colour and icon so the status never depends on colour alone. No real-time data is grey,
 * never green.
 * 四种状态各有颜色与图标，状态不只靠颜色区分。没有实时数据是灰色，绝不是绿色。
 */
@Composable
private fun StatusBadge(status: LegStatus, prefix: String? = null) {
    val (text, color, icon) = when (status.state) {
        LegState.ON_TIME -> Triple(stringResource(R.string.transit_on_time), MaterialTheme.statusColors.green, R.drawable.ic_status_ok)
        LegState.DELAYED -> Triple(
            pluralStringResource(R.plurals.transit_late, status.delayMinutes, status.delayMinutes),
            MaterialTheme.statusColors.amber,
            R.drawable.ic_status_late,
        )
        LegState.NO_REALTIME -> Triple(stringResource(R.string.transit_no_realtime), MaterialTheme.statusColors.gray, R.drawable.ic_status_unknown)
        LegState.CANCELLED -> Triple(stringResource(R.string.transit_cancelled), MaterialTheme.statusColors.red, R.drawable.ic_status_cancel)
    }
    StatusChip(if (prefix.isNullOrEmpty()) text else stringResource(R.string.transit_line_status, prefix, text), color, icon)
}

@Composable
private fun Direction(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Hint(text: String, maxLines: Int = 1) {
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
