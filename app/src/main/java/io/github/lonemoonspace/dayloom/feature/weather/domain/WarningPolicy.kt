package io.github.lonemoonspace.dayloom.feature.weather.domain

import java.time.Instant
import java.time.ZonedDateTime

/**
 * When a warning applies, relative to now: [start] is null once it has begun, so the card says "until 18:00" instead of a
 * start that is already past.
 * 预警的生效时间（相对于现在）：已经开始时 [start] 为 null，卡片说「至 18:00」，而不是一个已经过去的开始时间。
 */
data class WarningWindow(val start: ZonedDateTime?, val end: ZonedDateTime)

/**
 * Which warnings to show and to notify about. Pure: [now] is passed in.
 * 显示哪些预警、通知哪些预警。纯函数：[now] 由调用方传入。
 */
object WarningPolicy {

    /**
     * Warnings not yet over, most severe first, then the soonest. Unknown levels are left out: the colour is the message.
     * 还没结束的预警，最严重的在前，其次按开始时间。级别不明的不显示：颜色本身就是信息。
     */
    fun active(warnings: List<WeatherWarning>, now: ZonedDateTime): List<WeatherWarning> {
        val at = now.toInstant().toEpochMilli()
        return warnings
            .filter { it.end > at && it.severity != null }
            .sortedWith(compareByDescending<WeatherWarning> { it.level }.thenBy { it.start })
    }

    fun window(warning: WeatherWarning, now: ZonedDateTime): WarningWindow {
        val start = Instant.ofEpochMilli(warning.start).atZone(now.zone)
        val end = Instant.ofEpochMilli(warning.end).atZone(now.zone)
        return WarningWindow(start.takeIf { it.isAfter(now) }, end)
    }

    /**
     * What a warning is remembered by for notifications: MET gives an updated warning a new id, so the id alone would
     * notify again for every small text change. Event, level and start stay the same through such updates, while a
     * raised level is news.
     * 通知时用来认出一条预警的键：MET 更新预警会换 id，只看 id 的话每次改个措辞都会再通知一次。事件、级别与开始时间在这类更新里
     * 不变，而级别升高才是新消息。
     */
    fun key(warning: WeatherWarning): String = "${warning.event}|${warning.level}|${warning.start}"

    /**
     * The warnings to notify about now (not told before), and the keys to remember: only those of warnings still active,
     * so the remembered set does not grow forever.
     * 现在要通知的预警（之前没说过的），以及要记住的键：只记仍然有效的预警，记住的集合不会无限增长。
     */
    fun toNotify(warnings: List<WeatherWarning>, told: Set<String>, now: ZonedDateTime): Pair<List<WeatherWarning>, Set<String>> {
        val active = active(warnings, now)
        val keys = active.map(::key).toSet()
        return active.filter { key(it) !in told } to keys
    }
}
