package io.github.lonemoonspace.dayloom.feature.football.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.error.toAppError
import io.github.lonemoonspace.dayloom.core.secret.SecretState
import io.github.lonemoonspace.dayloom.core.ui.NotifySwitch
import io.github.lonemoonspace.dayloom.core.ui.SecretInput
import io.github.lonemoonspace.dayloom.feature.football.FootballSettings
import io.github.lonemoonspace.dayloom.feature.football.domain.FootballPolicy
import io.github.lonemoonspace.dayloom.feature.football.domain.TeamRef
import kotlinx.coroutines.CancellationException

/**
 * Key, competition, team and the two notification switches. The free tier has no global team search, so the team is
 * picked from a competition's list (design §11.6). [update] runs in the app scope, set up by the module.
 * Key、赛事、球队与两个通知开关。免费档没有全局球队搜索，所以先选赛事再从它的球队列表里选（设计文档 §11.6）。[update] 在
 * 应用级作用域里执行，由模块提供。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FootballSettingsSection(
    saved: FootballSettings,
    secret: SecretState,
    channelId: String,
    crests: CrestLoader,
    loadTeams: suspend (String) -> List<TeamRef>,
    saveKey: (String) -> Unit,
    update: ((FootballSettings) -> FootballSettings) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Hint(stringResource(R.string.football_key_note))
    SecretInput(stringResource(R.string.football_key), secret, saveKey)

    Text(stringResource(R.string.football_competition), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FootballPolicy.FREE_COMPETITIONS.forEach { code ->
            FilterChip(
                selected = saved.competition == code,
                onClick = { update { it.copy(competition = code) } },
                label = { Text(competitionName(code)?.let { stringResource(it) } ?: code) },
            )
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = saved.competition.isNotBlank()) { picking = true }
            .padding(vertical = 8.dp),
    ) {
        if (saved.team.id > 0) {
            Crest(saved.team, crests, size = 28.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.football_team), style = MaterialTheme.typography.bodyLarge)
            Hint(
                when {
                    saved.team.id > 0 -> saved.team.name
                    saved.competition.isBlank() -> stringResource(R.string.football_pick_competition_first)
                    else -> stringResource(R.string.football_no_team)
                },
            )
        }
        if (saved.competition.isNotBlank()) TextButton(onClick = { picking = true }) { Text(stringResource(R.string.football_choose_team)) }
    }
    NotifySwitch(
        label = stringResource(R.string.football_notify_kickoff_switch),
        summary = stringResource(R.string.football_notify_kickoff_switch_summary),
        checked = saved.notifyKickoff,
        channelId = channelId,
    ) { on -> update { it.copy(notifyKickoff = on) } }
    NotifySwitch(
        label = stringResource(R.string.football_notify_result_switch),
        summary = stringResource(R.string.football_notify_result_switch_summary),
        checked = saved.notifyResult,
        channelId = channelId,
    ) { on -> update { it.copy(notifyResult = on) } }
    Attribution()

    if (picking) {
        TeamPickerDialog(
            competition = saved.competition,
            hasKey = secret.display.isNotBlank(),
            crests = crests,
            loadTeams = loadTeams,
            onDismiss = { picking = false },
        ) { team ->
            picking = false
            update { it.copy(team = team) }
        }
    }
}

/**
 * Lists the competition's teams; the request runs in this dialog's scope, so closing it cancels the request.
 * 列出该赛事的球队；请求在对话框的作用域里运行，关掉对话框就会取消。
 */
@Composable
private fun TeamPickerDialog(
    competition: String,
    hasKey: Boolean,
    crests: CrestLoader,
    loadTeams: suspend (String) -> List<TeamRef>,
    onDismiss: () -> Unit,
    onPick: (TeamRef) -> Unit,
) {
    var teams by remember { mutableStateOf<List<TeamRef>?>(null) }
    var error by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(competition, hasKey) {
        if (!hasKey) return@LaunchedEffect
        try {
            teams = loadTeams(competition)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.toAppError()
        }
    }
    val name = competitionName(competition)?.let { stringResource(it) } ?: competition
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.football_teams_title, name)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                val loaded = teams
                when {
                    !hasKey -> Hint(stringResource(R.string.football_teams_need_key))
                    error != null -> ErrorLine(error!!)
                    loaded == null -> CircularProgressIndicator(Modifier.padding(8.dp))
                    loaded.isEmpty() -> Hint(stringResource(R.string.football_teams_empty))
                    else -> loaded.forEach { team ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(team) }
                                .padding(vertical = 6.dp),
                        ) {
                            Crest(team, crests, size = 24.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(team.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
