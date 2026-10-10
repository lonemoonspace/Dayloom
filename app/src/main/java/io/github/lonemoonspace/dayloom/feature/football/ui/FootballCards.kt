package io.github.lonemoonspace.dayloom.feature.football.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import io.github.lonemoonspace.dayloom.feature.football.domain.Standings
import io.github.lonemoonspace.dayloom.feature.football.domain.TeamRef
import java.time.Duration
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
            MatchStatusChip(match)
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
 * whose time already says it all.
 * 进行中用红色并带分钟数，完场、延期等用灰色；只是排了期的比赛不加标签，时间已经说明一切。
 */
@Composable
internal fun MatchStatusChip(match: Match) {
    val colors = MaterialTheme.statusColors
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

/**
 * A fixture or result from the followed team's side: date, home/away, opponent, competition; the score with a W/D/L pill
 * for results, the kick-off time for fixtures.
 * 从关注球队角度看的一场赛程或赛果：日期、主客、对手、赛事；赛果显示比分与胜平负胶囊，赛程显示开球时间。
 */
@Composable
internal fun MatchRow(match: Match, teamId: Int, now: ZonedDateTime, crests: CrestLoader, divider: Boolean) {
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    val opponent = FootballPolicy.opponent(match, teamId)
    val home = FootballPolicy.isHome(match, teamId)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        Column(Modifier.widthIn(min = 64.dp)) {
            Text(kickoffDay(match, now), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                if (match.status.isFinished) competitionLabel(match.competitionCode, match.competition) else kickoffTime(match, now),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(if (home) R.string.football_home_abbr else R.string.football_away_abbr),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(5.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
        Spacer(Modifier.width(6.dp))
        Crest(opponent, crests, size = 22.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            opponent.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (match.status.isFinished || match.status.isLive) {
            // From the followed team's side, its goals first. / 从关注球队的角度，先写它的进球数。
            val ours = if (home) match.homeGoals else match.awayGoals
            val theirs = if (home) match.awayGoals else match.homeGoals
            Text("${ours ?: "–"}–${theirs ?: "–"}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
        }
        FootballPolicy.outcome(match, teamId)?.let { OutcomePill(it) } ?: MatchStatusChip(match)
    }
}

@Composable
private fun OutcomePill(outcome: Outcome) {
    val colors = MaterialTheme.statusColors
    when (outcome) {
        Outcome.WIN -> StatusChip(stringResource(R.string.football_outcome_win), colors.green)
        Outcome.DRAW -> StatusChip(stringResource(R.string.football_outcome_draw), colors.gray)
        Outcome.LOSS -> StatusChip(stringResource(R.string.football_outcome_loss), colors.red)
    }
}

/**
 * The table, compact: position, crest, team, played, goal difference, points; the followed team highlighted. Group stages
 * show only the followed team's group, which is what the user wants to know.
 * 紧凑的积分表：名次、队徽、球队、场次、净胜球、积分；关注球队高亮。小组赛只显示关注球队所在的组，那才是用户想看的。
 */
@Composable
internal fun TableRows(standings: Standings, teamId: Int, crests: CrestLoader) {
    val group = standings.groups.firstOrNull { g -> g.rows.any { it.team.id == teamId } } ?: standings.groups.firstOrNull() ?: return
    if (group.group.isNotBlank()) {
        Text(
            stringResource(R.string.football_group, group.group.removePrefix("GROUP_").replace('_', ' ')),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(Modifier.padding(vertical = 2.dp)) {
        HeaderCell("#", Modifier.width(24.dp))
        Spacer(Modifier.weight(1f))
        HeaderCell(stringResource(R.string.football_table_played), Modifier.width(30.dp))
        HeaderCell(stringResource(R.string.football_table_gd), Modifier.width(36.dp))
        HeaderCell(stringResource(R.string.football_table_points), Modifier.width(34.dp))
    }
    group.rows.forEach { row ->
        val ours = row.team.id == teamId
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (ours) Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)) else Modifier)
                .padding(vertical = 2.dp),
        ) {
            val weight = if (ours) FontWeight.Bold else null
            Text(row.position.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = weight, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
            Crest(row.team, crests, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(row.team.label, style = MaterialTheme.typography.bodySmall, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            NumberCell(row.played.toString(), weight, Modifier.width(30.dp))
            NumberCell(if (row.goalDifference > 0) "+${row.goalDifference}" else row.goalDifference.toString(), weight, Modifier.width(36.dp))
            NumberCell(row.points.toString(), FontWeight.Bold, Modifier.width(34.dp))
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = modifier)
}

@Composable
private fun NumberCell(text: String, weight: FontWeight?, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = weight, textAlign = TextAlign.Center, modifier = modifier)
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

/** Arranges cards with the same spacing as the home screen. / 与首页相同的卡片间距。 */
internal val CardSpacing = Arrangement.spacedBy(8.dp)
