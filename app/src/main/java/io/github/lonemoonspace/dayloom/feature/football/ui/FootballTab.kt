package io.github.lonemoonspace.dayloom.feature.football.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.LocalBarInsets
import io.github.lonemoonspace.dayloom.core.ui.SkeletonLines
import io.github.lonemoonspace.dayloom.core.ui.plusBars
import io.github.lonemoonspace.dayloom.core.ui.rememberTimeFormatter
import io.github.lonemoonspace.dayloom.core.ui.theme.appSurfaces
import io.github.lonemoonspace.dayloom.core.ui.theme.statusColors
import io.github.lonemoonspace.dayloom.feature.football.domain.FootballPolicy
import io.github.lonemoonspace.dayloom.feature.football.domain.Match
import io.github.lonemoonspace.dayloom.feature.football.domain.Outcome
import io.github.lonemoonspace.dayloom.feature.football.domain.Scorers
import io.github.lonemoonspace.dayloom.feature.football.domain.Standings
import io.github.lonemoonspace.dayloom.feature.football.domain.TeamRef
import java.time.Instant
import java.time.ZonedDateTime

/** What the football tab shows, kept per source so each part loads and fails on its own. / 足球标签页的数据，按来源分开，各部分各自加载、各自出错。 */
internal class FootballTabData(
    val teamId: Int,
    val matches: List<Match>?,
    val matchesError: AppError?,
    val standings: Standings?,
    val standingsFetchedAt: Long?,
    val standingsError: AppError?,
    val scorers: Scorers?,
    val scorersFetchedAt: Long?,
    val scorersError: AppError?,
)

private enum class FootballSection(val label: Int) {
    FIXTURES(R.string.football_section_fixtures),
    TABLE(R.string.football_table),
    SCORERS(R.string.football_section_scorers),
}

/**
 * The football tab, laid out like the original app: a switch between fixtures, the table and the top scorers. The team is
 * not named at the top: the user picked it, and its matches and rows are highlighted.
 * 足球标签页，沿用原 App 的布局：在赛程、积分榜与射手榜之间切换。顶部不写队名：球队是用户自己选的，它的比赛与所在行都会高亮。
 */
@Composable
internal fun FootballTab(data: FootballTabData, now: ZonedDateTime, crests: CrestLoader) {
    var section by rememberSaveable { mutableStateOf(FootballSection.FIXTURES) }
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = LocalBarInsets.current.calculateTopPadding() + 8.dp, bottom = 4.dp),
        ) {
            FootballSection.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = section == entry,
                    onClick = { section = entry },
                    shape = SegmentedButtonDefaults.itemShape(index, FootballSection.entries.size),
                    icon = {},
                ) { Text(stringResource(entry.label), maxLines = 1) }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp).plusBars(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (section) {
                FootballSection.FIXTURES -> fixtures(data, now, crests)
                FootballSection.TABLE -> table(data, now, crests)
                FootballSection.SCORERS -> scorers(data, now, crests)
            }
        }
    }
}

private fun LazyListScope.fixtures(data: FootballTabData, now: ZonedDateTime, crests: CrestLoader) {
    data.matchesError?.let { error -> item(key = "error") { ErrorLine(error) } }
    val list = data.matches
    if (list == null) {
        if (data.matchesError == null) item(key = "loading") { InfoCard(title = null) { SkeletonLines() } }
        return
    }
    val upcoming = FootballPolicy.upcoming(list, now.toInstant())
    val recent = FootballPolicy.recent(list)
    item(key = "upcoming-title") { SectionTitle(stringResource(R.string.football_upcoming_header)) }
    if (upcoming.isEmpty()) {
        item(key = "upcoming-none") { Hint(stringResource(R.string.football_no_matches)) }
    } else {
        // The next match, or the one being played, gets the large card. / 下一场（或正在进行的一场）用大卡片。
        item(key = "hero-${upcoming.first().id}") { NextMatchHero(upcoming.first(), data.teamId, now, crests) }
        upcoming.drop(1).forEach { match -> item(key = "next-${match.id}") { FixtureCard(match, data.teamId, now, crests) } }
    }
    item(key = "recent-title") { SectionTitle(stringResource(R.string.football_recent_header)) }
    if (recent.isEmpty()) {
        item(key = "recent-none") { Hint(stringResource(R.string.football_no_results)) }
    } else {
        recent.forEach { match -> item(key = "last-${match.id}") { ResultCard(match, data.teamId, now, crests) } }
    }
    item(key = "attribution") { Attribution() }
}

private fun LazyListScope.table(data: FootballTabData, now: ZonedDateTime, crests: CrestLoader) {
    data.standingsError?.let { error -> item(key = "error") { ErrorLine(error) } }
    val standings = data.standings
    val group = standings?.groups?.let { groups -> groups.firstOrNull { g -> g.rows.any { it.team.id == data.teamId } } ?: groups.firstOrNull() }
    if (standings == null || group == null || group.rows.isEmpty()) {
        if (data.standingsError == null) item(key = "empty") { Hint(stringResource(R.string.football_no_table)) }
        return
    }
    item(key = "header") {
        LeagueHeader(competitionLabel(standings.competitionCode, standings.competition), standings.matchday, data.standingsFetchedAt, now)
    }
    if (group.group.isNotBlank()) {
        item(key = "group") {
            Hint(stringResource(R.string.football_group, group.group.removePrefix("GROUP_").replace('_', ' ')))
        }
    }
    item(key = "columns") {
        TableLine(highlight = false) {
            HeadCell("#", 24.dp, TextAlign.Start)
            HeadCell(stringResource(R.string.football_table_team), null, TextAlign.Start)
            HeadCell(stringResource(R.string.football_table_played), 26.dp)
            HeadCell(stringResource(R.string.football_table_won), 26.dp)
            HeadCell(stringResource(R.string.football_table_drawn), 26.dp)
            HeadCell(stringResource(R.string.football_table_lost), 26.dp)
            HeadCell(stringResource(R.string.football_table_gd), 38.dp)
            HeadCell(stringResource(R.string.football_table_points), 36.dp)
        }
    }
    itemsIndexed(group.rows, key = { _, row -> "row-${row.position}-${row.team.id}" }) { _, row ->
        val ours = row.team.id == data.teamId
        TableLine(highlight = ours) {
            NumCell(row.position.toString(), 24.dp, ours, TextAlign.Start)
            Crest(row.team, crests, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                row.team.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (ours) FontWeight.Bold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            NumCell(row.played.toString(), 26.dp, ours)
            NumCell(row.won.toString(), 26.dp, ours)
            NumCell(row.draw.toString(), 26.dp, ours)
            NumCell(row.lost.toString(), 26.dp, ours)
            NumCell(if (row.goalDifference > 0) "+${row.goalDifference}" else row.goalDifference.toString(), 38.dp, ours)
            NumCell(row.points.toString(), 36.dp, bold = true)
        }
    }
    item(key = "attribution") { Attribution() }
}

private fun LazyListScope.scorers(data: FootballTabData, now: ZonedDateTime, crests: CrestLoader) {
    data.scorersError?.let { error -> item(key = "error") { ErrorLine(error) } }
    val scorers = data.scorers
    if (scorers == null || scorers.scorers.isEmpty()) {
        if (data.scorersError == null) item(key = "empty") { Hint(stringResource(R.string.football_no_scorers)) }
        return
    }
    item(key = "header") {
        LeagueHeader(competitionLabel(scorers.competitionCode, scorers.competition), scorers.matchday, data.scorersFetchedAt, now)
    }
    item(key = "columns") {
        TableLine(highlight = false) {
            HeadCell("#", 24.dp, TextAlign.Start)
            HeadCell(stringResource(R.string.football_scorer_player), null, TextAlign.Start)
            HeadCell(stringResource(R.string.football_scorer_played), 40.dp)
            HeadCell(stringResource(R.string.football_scorer_penalties), 40.dp)
            HeadCell(stringResource(R.string.football_scorer_goals), 42.dp)
        }
    }
    val ranks = FootballPolicy.scorerRanks(scorers.scorers)
    itemsIndexed(scorers.scorers, key = { i, s -> "scorer-$i-${s.player}" }) { i, scorer ->
        val ours = scorer.team.id == data.teamId
        TableLine(highlight = ours) {
            NumCell(ranks[i].toString(), 24.dp, ours, TextAlign.Start)
            Crest(scorer.team, crests, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    scorer.player,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (ours) FontWeight.Bold else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    scorer.team.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Unknown counts show a dash: "0" would read as "took no penalties". / 未知的数字显示横线：「0」会被读成「一个点球都没罚」。
            NumCell(scorer.playedMatches?.toString() ?: "–", 40.dp, ours)
            NumCell(scorer.penalties?.toString() ?: "–", 40.dp, ours)
            NumCell(scorer.goals.toString(), 42.dp, bold = true)
        }
    }
    item(key = "attribution") { Attribution() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/** "LaLiga · Matchday 8 · Updated 21:19". / 「西甲 · 第 8 轮 · 更新于 21:19」。 */
@Composable
private fun LeagueHeader(competition: String, matchday: Int?, fetchedAt: Long?, now: ZonedDateTime) {
    val parts = listOfNotNull(
        competition.takeIf { it.isNotBlank() },
        matchday?.let { stringResource(R.string.football_matchday, it) },
        fetchedAt?.let { stringResource(R.string.football_updated_at, Instant.ofEpochMilli(it).atZone(now.zone).format(rememberTimeFormatter())) },
    )
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** One table line; the followed team's is filled. / 表格的一行；关注球队那一行加底色。 */
@Composable
private fun TableLine(highlight: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlight) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        content = content,
    )
}

/** A header cell; [width] null takes the remaining room. / 表头单元格；[width] 为空时占满剩余空间。 */
@Composable
private fun RowScope.HeadCell(text: String, width: Dp?, align: TextAlign = TextAlign.End) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 1,
        modifier = if (width == null) Modifier.weight(1f) else Modifier.width(width),
    )
}

/** Fixed-width figures so every line lines up. / 定宽数字列，各行对齐。 */
@Composable
private fun NumCell(text: String, width: Dp, bold: Boolean, align: TextAlign = TextAlign.End) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else null,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.width(width),
    )
}

/**
 * The next match (or the one being played) as a large card: both crests facing each other, the kick-off time or the score
 * between them, and the countdown or the status underneath.
 * 下一场（或正在进行的一场）的大卡片：两队队徽相对，中间是开球时间或比分，下面是倒计时或比赛状态。
 */
@Composable
private fun NextMatchHero(match: Match, teamId: Int, now: ZonedDateTime, crests: CrestLoader) {
    val accent = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.appSurfaces.card, contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                competitionLabel(match.competitionCode, match.competition),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroTeam(match.home, ours = match.home.id == teamId, crests, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                    val started = match.status.isLive || match.status.isFinished
                    if (started && match.homeGoals != null && match.awayGoals != null) {
                        Text("${match.homeGoals} : ${match.awayGoals}", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    } else {
                        Text(kickoffTime(match, now), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            kickoffDay(match, now),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                HeroTeam(match.away, ours = match.away.id == teamId, crests, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            val countdown = if (match.status.isUpcoming) FootballPolicy.countdown(match.kickoff, now.toInstant()) else null
            if (countdown != null) {
                Text(
                    when {
                        countdown.days > 0 -> stringResource(R.string.football_countdown_days, countdown.days, countdown.hours)
                        countdown.hours > 0 -> stringResource(R.string.football_countdown_hours, countdown.hours, countdown.minutes)
                        else -> stringResource(R.string.football_countdown_minutes, countdown.minutes)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MatchStatusChip(match)
            }
        }
    }
}

@Composable
private fun HeroTeam(team: TeamRef, ours: Boolean, crests: CrestLoader, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Crest(team, crests, size = 44.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            team.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (ours) FontWeight.Bold else FontWeight.Medium,
            color = if (ours) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A coming match: competition, the opponent's crest (the followed team's is the same every time), home and away lines with
 * the followed team in bold, and the kick-off on the right.
 * 一场即将进行的比赛：赛事、对手的队徽（关注球队的队徽每场都一样）、主客两行（关注球队加粗），右边是开球时间。
 */
@Composable
private fun FixtureCard(match: Match, teamId: Int, now: ZonedDateTime, crests: CrestLoader) {
    InfoCard(title = null) {
        Text(
            competitionLabel(match.competitionCode, match.competition),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Crest(FootballPolicy.opponent(match, teamId), crests, size = 28.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                TeamLine(match.home, home = true, ours = match.home.id == teamId)
                TeamLine(match.away, home = false, ours = match.away.id == teamId)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${kickoffDay(match, now)} ${kickoffTime(match, now)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (match.status.isUpcoming) {
                    Text(stringResource(R.string.football_not_started), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    MatchStatusChip(match)
                }
            }
        }
    }
}

@Composable
private fun TeamLine(team: TeamRef, home: Boolean, ours: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(if (home) R.string.football_home_abbr else R.string.football_away_abbr),
            style = MaterialTheme.typography.labelMedium,
            color = if (home) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            team.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (ours) FontWeight.Bold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A finished match on one line, home crest and name, the score, away name and crest; a coloured edge and pill for win, draw
 * or loss from the followed team's side.
 * 一场已结束的比赛排成一行：主队队徽与队名、比分、客队队名与队徽；左边的色条和胶囊从关注球队角度标出胜、平、负。
 */
@Composable
private fun ResultCard(match: Match, teamId: Int, now: ZonedDateTime, crests: CrestLoader) {
    val outcome = FootballPolicy.outcome(match, teamId)
    val edge = when (outcome) {
        Outcome.WIN -> MaterialTheme.statusColors.green
        Outcome.DRAW -> MaterialTheme.statusColors.gray
        Outcome.LOSS -> MaterialTheme.statusColors.red
        null -> Color.Transparent
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.appSurfaces.card, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(edge),
            )
            Column(Modifier.padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        competitionLabel(match.competitionCode, match.competition),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${kickoffDay(match, now)} ${kickoffTime(match, now)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (outcome != null) OutcomePill(outcome) else MatchStatusChip(match)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Crest(match.home, crests, size = 24.dp)
                    Spacer(Modifier.width(6.dp))
                    ResultTeam(match.home, ours = match.home.id == teamId, end = false, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 10.dp)) {
                        Text(
                            "${match.homeGoals ?: "–"} : ${match.awayGoals ?: "–"}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        if (match.homePens != null && match.awayPens != null) {
                            Text(
                                stringResource(R.string.football_penalties_short, match.homePens, match.awayPens),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ResultTeam(match.away, ours = match.away.id == teamId, end = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    Crest(match.away, crests, size = 24.dp)
                }
            }
        }
    }
}

@Composable
private fun ResultTeam(team: TeamRef, ours: Boolean, end: Boolean, modifier: Modifier) {
    Text(
        team.label,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (ours) FontWeight.Bold else null,
        color = if (ours) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
