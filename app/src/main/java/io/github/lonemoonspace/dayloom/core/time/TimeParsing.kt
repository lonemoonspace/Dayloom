package io.github.lonemoonspace.dayloom.core.time

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Parsing helpers for timestamps from external APIs. Display formatting lives in the UI layer, because it depends on the language.
 * 外部接口时间戳的解析工具。展示用的格式化在界面层，因为它取决于语言。
 */
object TimeParsing {

    /**
     * ISO-8601 with offset (Entur, MET) or RFC 1123, converted to [zone]; unparseable input returns null.
     * 带偏移的 ISO-8601（Entur、MET）或 RFC 1123，换算到 [zone]；无法解析时返回 null。
     */
    fun parseOffset(value: String?, zone: ZoneId): ZonedDateTime? {
        if (value.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(value).atZoneSameInstant(zone)
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).atZoneSameInstant(zone)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    /**
     * UTC instants such as football-data.org's, tolerating a missing trailing `Z`.
     * football-data.org 这类 UTC 时刻，容忍末尾缺少 `Z`。
     */
    fun parseInstant(value: String?, zone: ZoneId): ZonedDateTime? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val instant = runCatching { Instant.parse(trimmed) }.getOrNull()
            ?: runCatching { Instant.parse(trimmed + "Z") }.getOrNull()
            ?: return parseOffset(trimmed, zone)
        return instant.atZone(zone)
    }

    /**
     * Delay in whole minutes; arriving early counts as 0.
     * 晚点的整分钟数；提前到达按 0 计。
     */
    fun delayMinutes(aimed: ZonedDateTime?, expected: ZonedDateTime?): Int? {
        if (aimed == null || expected == null) return null
        return Duration.between(aimed, expected).toMinutes().toInt().coerceAtLeast(0)
    }

    /**
     * ISO offset format that [OffsetDateTime.parse] can read back ([ZonedDateTime.toString] adds a zone suffix it cannot).
     * 能被 [OffsetDateTime.parse] 读回的 ISO 偏移格式（[ZonedDateTime.toString] 带时区后缀，读不回）。
     */
    fun isoOffset(time: ZonedDateTime): String = time.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
