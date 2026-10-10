package io.github.lonemoonspace.dayloom.feature.traffic.domain

import io.github.lonemoonspace.dayloom.core.routine.CommuteDirection
import io.github.lonemoonspace.dayloom.core.routine.RoutinePolicy
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
 * Traffic decisions, ported from the original `TrafficSource`; one source now switches direction at noon instead of two
 * fixed sources (design §5).
 * 路况判定，移植自原项目 `TrafficSource`；现在一个来源在中午切换方向，而不是两个固定来源（设计文档 §5）。
 */
object TrafficPolicy {

    /** Mornings to work, from noon homewards ([RoutinePolicy.direction]). / 上午去上班，中午起回家（[RoutinePolicy.direction]）。 */
    fun direction(now: ZonedDateTime): Direction = when (RoutinePolicy.direction(now)) {
        CommuteDirection.TO_WORK -> Direction.TO_WORK
        CommuteDirection.BACK_HOME -> Direction.BACK_HOME
    }

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
