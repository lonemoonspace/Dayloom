package io.github.lonemoonspace.dayloom.feature.traffic.domain

import io.github.lonemoonspace.dayloom.core.routine.Routine
import io.github.lonemoonspace.dayloom.core.routine.RoutinePolicy
import io.github.lonemoonspace.dayloom.core.routine.WindowKind
import java.time.ZonedDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Which way the commute goes. / 通勤方向。 */
enum class Direction { TO_WORK, BACK_HOME }

@Serializable(with = TrafficLevelSerializer::class)
enum class TrafficLevel { CLEAR, SLIGHT, MODERATE, SEVERE, UNKNOWN }

/**
 * The snapshot of `traffic.route`: numbers only; place names come from the saved places when shown.
 * `traffic.route` 的快照：只存数字；地点名称在显示时从已保存地点取。
 */
@Serializable
data class TrafficStatus(
    val durationSec: Long = 0,
    val staticDurationSec: Long = 0,
    val delaySec: Long = 0,
    val distanceMeters: Long = 0,
    val level: TrafficLevel = TrafficLevel.UNKNOWN,
    val direction: Direction = Direction.TO_WORK,
)

/**
 * Traffic decisions, ported from the original `TrafficSource`; one source now follows the daily windows instead of two
 * fixed sources (design §5).
 * 路况判定，移植自原项目 `TrafficSource`；现在一个来源跟随日常时间窗切换方向，而不是两个固定来源（设计文档 §5）。
 */
object TrafficPolicy {

    /**
     * Back home during the back-home window; otherwise the direction of whichever window comes next, so in the afternoon the
     * card already shows the way home. With the routine off it always shows the way to work.
     * 回家时段内是返程；其余时间看哪个时间窗先到，所以下午卡片已经显示回家的路。日常作息关闭时始终显示去程。
     */
    fun direction(routine: Routine, now: ZonedDateTime): Direction {
        val toWork = RoutinePolicy.next(routine, WindowKind.TO_WORK, now)
        val backHome = RoutinePolicy.next(routine, WindowKind.BACK_HOME, now)
        return when {
            backHome == null -> Direction.TO_WORK
            toWork == null -> Direction.BACK_HOME
            // Under way counts as "now", so an ongoing window wins. / 进行中的时间窗视为「现在」，因此优先。
            effectiveStart(backHome.start, now).isBefore(effectiveStart(toWork.start, now)) -> Direction.BACK_HOME
            else -> Direction.TO_WORK
        }
    }

    private fun effectiveStart(start: ZonedDateTime, now: ZonedDateTime) = if (start.isBefore(now)) now else start

    /**
     * Google's `duration` / `staticDuration` (protobuf Duration as JSON). Fractional seconds are allowed (`"10.5s"`); null when
     * missing or unreadable, so the caller decides between failing and degrading.
     * Google 的 `duration` / `staticDuration`（protobuf Duration 的 JSON 形式）。允许小数秒（`"10.5s"`）；缺失或读不出时为 null，
     * 由调用方决定报错还是降级。
     */
    fun parseSeconds(value: String): Long? =
        value.takeIf { it.endsWith("s") }?.removeSuffix("s")?.toDoubleOrNull()?.takeIf { it >= 0 }?.toLong()

    /**
     * Delay over the free-flow time, and the level it maps to. Without a free-flow time the level is UNKNOWN, never CLEAR.
     * 相对畅通时长的延误及对应等级。没有畅通时长时等级为 UNKNOWN，绝不说成畅通。
     */
    fun level(durationSec: Long, staticDurationSec: Long?): Pair<Long, TrafficLevel> {
        if (staticDurationSec == null) return 0L to TrafficLevel.UNKNOWN
        val delay = (durationSec - staticDurationSec).coerceAtLeast(0)
        val level = when {
            delay <= CLEAR_MAX_SEC -> TrafficLevel.CLEAR
            delay <= SLIGHT_MAX_SEC -> TrafficLevel.SLIGHT
            delay <= MODERATE_MAX_SEC -> TrafficLevel.MODERATE
            else -> TrafficLevel.SEVERE
        }
        return delay to level
    }

    /** Delays up to this are within normal variation and not shown as "slower". / 不超过此值的延误属正常波动，不显示「变慢」。 */
    const val CLEAR_MAX_SEC = 120L
    private const val SLIGHT_MAX_SEC = 480L
    private const val MODERATE_MAX_SEC = 1080L
}

/** Unknown names (an older or newer version) fall back to UNKNOWN instead of breaking the whole snapshot. / 未知名称回退为 UNKNOWN，而不是让整份快照解码失败。 */
object TrafficLevelSerializer : KSerializer<TrafficLevel> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TrafficLevel", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): TrafficLevel {
        val name = decoder.decodeString()
        return TrafficLevel.entries.firstOrNull { it.name == name } ?: TrafficLevel.UNKNOWN
    }

    override fun serialize(encoder: Encoder, value: TrafficLevel) = encoder.encodeString(value.name)
}
