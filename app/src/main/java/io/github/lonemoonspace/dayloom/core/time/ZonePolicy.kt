package io.github.lonemoonspace.dayloom.core.time

import java.time.DateTimeException
import java.time.ZoneId

/**
 * Decides which time zone the app uses.
 * 决定应用使用哪个时区。
 */
object ZonePolicy {

    /**
     * The override from settings when it is a valid zone id, otherwise the device zone.
     * An invalid stored value must not crash the app or silently switch to UTC.
     * 设置里的覆盖值是合法时区 id 时用它，否则用设备时区。存储里的非法值不能让 App 崩溃，也不能悄悄变成 UTC。
     */
    fun resolve(override: String, device: ZoneId): ZoneId = parse(override) ?: device

    /**
     * Parses a user-entered zone id; blank or unknown ids return null.
     * 解析用户输入的时区 id；空白或未知 id 返回 null。
     */
    fun parse(id: String): ZoneId? {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) return null
        return try {
            ZoneId.of(trimmed)
        } catch (_: DateTimeException) {
            null
        }
    }
}
