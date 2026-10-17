package io.github.lonemoonspace.dayloom.feature.football.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.i18n.asString
import io.github.lonemoonspace.dayloom.core.ui.StatusChip
import io.github.lonemoonspace.dayloom.core.ui.rememberPatternFormatter
import io.github.lonemoonspace.dayloom.core.ui.rememberTimeFormatter
import io.github.lonemoonspace.dayloom.core.ui.theme.statusColors
import io.github.lonemoonspace.dayloom.core.ui.userMessage
import io.github.lonemoonspace.dayloom.feature.football.domain.FootballPolicy
import io.github.lonemoonspace.dayloom.feature.football.domain.Match
import io.github.lonemoonspace.dayloom.feature.football.domain.MatchStatus
import io.github.lonemoonspace.dayloom.feature.football.domain.Outcome
import io.github.lonemoonspace.dayloom.feature.football.domain.TeamRef
import java.time.Instant
import java.time.ZonedDateTime

/**
 * One match as a scoreboard: home crest and name, the score or kick-off time in the middle, away name and crest; the status
 * under the score.
 * 一场比赛的记分牌：主队队徽与队名、中间是比分或开球时间、客队队名与队徽；比分下面是比赛状态。
 */
@Composable
internal fun Scoreboard(match: Match, now: ZonedDateTime, crests: CrestLoader) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        TeamSide(match.home, crests, Modifier.weight(1f), alignEnd = false)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 92.dp)) {
            val started = match.status.isLive || match.status.isFinished
            if (started && match.homeGoals != null && match.awayGoals != null) {
                Text("${match.homeGoals} – ${match.awayGoals}", fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (match.homePens != null && match.awayPens != null) {
                    Text(
                        stringResource(R.string.football_penalties_short, match.homePens, match.awayPens),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(kickoffTime(match, now), fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(kickoffDay(match, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.padding(top = 2.dp))
            MatchStatusChip(match, now)
        }
        TeamSide(match.away, crests, Modifier.weight(1f), alignEnd = true)
    }
}

@Composable
private fun TeamSide(team: TeamRef, crests: CrestLoader, modifier: Modifier, alignEnd: Boolean) {
    Column(modifier = modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Crest(team, crests, size = 36.dp)
        Text(
            team.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Live in red with the minute, finished, postponed and the like in grey; nothing for a match that is simply scheduled,
 * whose time already says it all. A live status long past the match's end is an old snapshot, so it shows no LIVE.
 * 进行中用红色并带分钟数，完场、延期等用灰色；只是排了期的比赛不加标签，时间已经说明一切。比赛早该结束却仍是进行中，说明快照过时，
 * 不显示「进行中」。
 */
@Composable
internal fun MatchStatusChip(match: Match, now: ZonedDateTime) {
    val colors = MaterialTheme.statusColors
    if (match.status.isLive && !FootballPolicy.isLiveNow(match, now.toInstant())) return
    when (match.status) {
        MatchStatus.IN_PLAY -> StatusChip(
            when {
                match.minute != null && match.injuryTime != null -> stringResource(R.string.football_live_minute_added, match.minute, match.injuryTime)
                match.minute != null -> stringResource(R.string.football_live_minute, match.minute)
                else -> stringResource(R.string.football_live)
            },
            colors.red,
        )
        MatchStatus.PAUSED -> StatusChip(stringResource(R.string.football_half_time), colors.red)
        MatchStatus.FINISHED, MatchStatus.AWARDED -> StatusChip(stringResource(R.string.football_full_time), colors.gray)
        MatchStatus.POSTPONED -> StatusChip(stringResource(R.string.football_postponed), colors.amber)
        MatchStatus.SUSPENDED -> StatusChip(stringResource(R.string.football_suspended), colors.amber)
        MatchStatus.CANCELLED -> StatusChip(stringResource(R.string.football_cancelled), colors.amber)
        MatchStatus.SCHEDULED, MatchStatus.TIMED, MatchStatus.UNKNOWN -> Unit
    }
}

@Composable
internal fun kickoffTime(match: Match, now: ZonedDateTime): String =
    Instant.ofEpochMilli(match.kickoff).atZone(now.zone).format(rememberTimeFormatter())

/** "Today", "Tomorrow" or a short date with weekday. / 「今天」「明天」或带星期的短日期。 */
@Composable
internal fun kickoffDay(match: Match, now: ZonedDateTime): String {
    val day = Instant.ofEpochMilli(match.kickoff).atZone(now.zone).toLocalDate()
    return when (day) {
        now.toLocalDate() -> stringResource(R.string.common_today)
        now.toLocalDate().plusDays(1) -> stringResource(R.string.common_tomorrow)
        else -> day.format(rememberPatternFormatter("MMMEd"))
    }
}

@Composable
internal fun OutcomePill(outcome: Outcome) {
    val colors = MaterialTheme.statusColors
    when (outcome) {
        Outcome.WIN -> StatusChip(stringResource(R.string.football_outcome_win), colors.green)
        Outcome.DRAW -> StatusChip(stringResource(R.string.football_outcome_draw), colors.gray)
        Outcome.LOSS -> StatusChip(stringResource(R.string.football_outcome_loss), colors.red)
    }
}

@Composable
internal fun ErrorLine(error: AppError) {
    Text(error.userMessage().asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.statusColors.red)
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun Attribution() {
    Text(
        stringResource(R.string.football_attribution),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@StringRes
internal fun competitionName(code: String): Int? = when (code) {
    "PL" -> R.string.football_comp_pl
    "PD" -> R.string.football_comp_pd
    "BL1" -> R.string.football_comp_bl1
    "SA" -> R.string.football_comp_sa
    "FL1" -> R.string.football_comp_fl1
    "CL" -> R.string.football_comp_cl
    "DED" -> R.string.football_comp_ded
    "PPL" -> R.string.football_comp_ppl
    "ELC" -> R.string.football_comp_elc
    "BSA" -> R.string.football_comp_bsa
    "EC" -> R.string.football_comp_ec
    "WC" -> R.string.football_comp_wc
    else -> null
}

/**
 * The competition as text: a localized name for the free ones, the API's own name for any other (a cup the team plays in).
 * 赛事名称：免费赛事用本地化名称，其他赛事（球队参加的杯赛）用接口给的名称。
 */
@Composable
internal fun competitionLabel(code: String, apiName: String): String = competitionName(code)?.let { stringResource(it) } ?: apiName
