package io.github.lonemoonspace.dayloom.feature.traffic.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.i18n.asString
import io.github.lonemoonspace.dayloom.core.location.Place
import io.github.lonemoonspace.dayloom.core.location.PlacesPolicy
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.LocalAppClock
import io.github.lonemoonspace.dayloom.core.ui.SkeletonLines
import io.github.lonemoonspace.dayloom.core.ui.StatusChip
import io.github.lonemoonspace.dayloom.core.ui.placeTitle
import io.github.lonemoonspace.dayloom.core.ui.theme.statusColors
import io.github.lonemoonspace.dayloom.core.ui.userMessage
import io.github.lonemoonspace.dayloom.feature.traffic.domain.Direction
import io.github.lonemoonspace.dayloom.feature.traffic.domain.TrafficLevel
import io.github.lonemoonspace.dayloom.feature.traffic.domain.TrafficPolicy
import io.github.lonemoonspace.dayloom.feature.traffic.domain.TrafficStatus
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Driving time with a congestion chip, distance and the delay over free flow; tapping opens the route in a maps app.
 * 驾车时间、拥堵徽标、距离与相对畅通时的延误；点按在地图 App 里打开路线。
 */
@Composable
internal fun TrafficCard(
    snapshot: Snapshot<TrafficStatus>?,
    error: AppError?,
    isStale: (Snapshot<TrafficStatus>, Instant) -> Boolean,
    from: Place?,
    to: Place?,
) {
    val status = snapshot?.value
    val context = LocalContext.current
    // The direction comes from the data actually shown, which may be from the other window. / 方向以实际显示的数据为准，它可能来自另一个时间窗。
    val (origin, destination) = if (status?.direction == Direction.BACK_HOME) Pair(to, from) else Pair(from, to)
    val title = if (origin != null && destination != null) {
        stringResource(R.string.traffic_card_title, placeTitle(origin), placeTitle(destination))
    } else {
        stringResource(R.string.traffic_title)
    }
    val openLabel = stringResource(R.string.traffic_open_maps)
    InfoCard(
        title = title,
        stale = snapshot?.let { isStale(it, LocalAppClock.current.instant()) } == true,
        modifier = if (status != null && origin != null && destination != null) {
            Modifier.clickable(onClickLabel = openLabel) { openMaps(context, origin, destination) }
        } else {
            Modifier
        },
    ) {
        if (status != null) {
            val (color, icon) = when (status.level) {
                TrafficLevel.CLEAR -> MaterialTheme.statusColors.green to R.drawable.ic_status_ok
                TrafficLevel.SLIGHT -> MaterialTheme.statusColors.amber to R.drawable.ic_status_warn
                TrafficLevel.MODERATE -> MaterialTheme.statusColors.orange to R.drawable.ic_status_warn
                TrafficLevel.SEVERE -> MaterialTheme.statusColors.red to R.drawable.ic_status_warn
                TrafficLevel.UNKNOWN -> MaterialTheme.statusColors.gray to null
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(minutes(status.durationSec), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                StatusChip(stringResource(levelText(status.level)), color, icon)
                Spacer(Modifier.weight(1f))
                Text(
                    text = String.format(Locale.ROOT, "%.1f km", status.distanceMeters / 1000.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status.level != TrafficLevel.UNKNOWN) {
                Row {
                    val slower = status.delaySec > TrafficPolicy.CLEAR_MAX_SEC
                    Text(
                        text = stringResource(R.string.traffic_free_flow, minutes(status.staticDurationSec)) + if (slower) " · " else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (slower) {
                        Text(
                            text = stringResource(R.string.traffic_slower, minutes(status.delaySec)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.statusColors.amber,
                        )
                    }
                }
            }
        } else if (error == null) {
            SkeletonLines()
        }
        if (error != null) {
            Text(error.userMessage().asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.statusColors.red)
        }
    }
}

/** Whole minutes, at least 1, so a short trip never reads "0 min". / 整分钟，至少 1，短途不会显示「0 分钟」。 */
@Composable
private fun minutes(seconds: Long): String {
    val value = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    return pluralStringResource(R.plurals.traffic_minutes, value, value)
}

private fun levelText(level: TrafficLevel): Int = when (level) {
    TrafficLevel.CLEAR -> R.string.traffic_clear
    TrafficLevel.SLIGHT -> R.string.traffic_slight
    TrafficLevel.MODERATE -> R.string.traffic_moderate
    TrafficLevel.SEVERE -> R.string.traffic_severe
    TrafficLevel.UNKNOWN -> R.string.traffic_unknown
}

/** Google Maps directions URL; any maps app or a browser can open it. / Google 地图路线链接；任何地图 App 或浏览器都能打开。 */
private fun openMaps(context: Context, origin: Place, destination: Place) {
    fun point(p: Place) = PlacesPolicy.formatCoordinate(p.lat) + "," + PlacesPolicy.formatCoordinate(p.lon)
    val uri = "https://www.google.com/maps/dir/?api=1&origin=${point(origin)}&destination=${point(destination)}&travelmode=driving".toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        // No app can open links; nothing sensible to do. / 没有能打开链接的 App；无事可做。
    }
}
