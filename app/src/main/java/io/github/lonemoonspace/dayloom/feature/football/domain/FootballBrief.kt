package io.github.lonemoonspace.dayloom.feature.football.domain

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The football line of the morning brief: only on a match day, "Real Madrid – Barcelona at 21:00".
 * 早间简报里的足球一行：只在比赛日出现，「皇马 – 巴萨，21:00 开球」。
 */
object FootballBrief {
    fun line(matches: List<Match>, now: ZonedDateTime): UiText? {
        val today = now.toLocalDate()
        val match = matches
            .filter { it.status.isUpcoming || it.status.isLive }
            .filter { Instant.ofEpochMilli(it.kickoff).atZone(now.zone).toLocalDate() == today }
            .minByOrNull { it.kickoff } ?: return null
        val at = Instant.ofEpochMilli(match.kickoff).atZone(now.zone).format(TIME)
        return UiText.Res(R.string.football_brief, listOf(fixture(match), at))
    }

    /** Numeric time reads the same in every language. / 数字时刻在各语言里写法一致。 */
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
}
