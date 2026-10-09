package io.github.lonemoonspace.dayloom.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.BuildConfig
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.LanguageChoice
import io.github.lonemoonspace.dayloom.core.network.SharedHttpClient
import io.github.lonemoonspace.dayloom.core.notify.CoreNotifications
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.NotifySwitch
import io.github.lonemoonspace.dayloom.core.ui.SwitchRow
import io.github.lonemoonspace.dayloom.core.ui.plusBars

@Composable
fun SettingsScreen(
    state: SettingsState,
    onLanguage: (LanguageChoice) -> Unit,
    onTimeZone: (String) -> Boolean,
    onModuleEnabled: (String, Boolean) -> Unit,
    onMorningBrief: (Boolean) -> Unit,
    /** The shared places and daily-routine cards. / 共用的地点与日常作息卡片。 */
    sharedData: @Composable () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp).plusBars(top = true),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "language") { LanguageCard(state.language, onLanguage) }
        item(key = "timezone") { TimeZoneCard(state.timeZoneOverride, state.effectiveZone, onTimeZone) }
        item(key = "shared") { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { sharedData() } }
        item(key = "notifications") { NotificationsCard(state.morningBrief, onMorningBrief) }
        item(key = "modules") { ModulesCard(state.modules, onModuleEnabled) }
        items(state.sections, key = { "section:${it.module.id}" }) { active ->
            InfoCard(title = stringResource(active.module.title)) {
                active.instance.settings?.content?.invoke()
            }
        }
        item(key = "about") { AboutCard() }
    }
}

/** Also used by the onboarding. / 首次启动引导也用它。 */
@Composable
internal fun LanguageCard(selected: LanguageChoice, onSelect: (LanguageChoice) -> Unit) {
    InfoCard(title = stringResource(R.string.settings_language)) {
        Column(Modifier.selectableGroup()) {
            LanguageChoice.entries.forEach { choice ->
                val label = when (choice) {
                    LanguageChoice.SYSTEM -> stringResource(R.string.settings_language_system)
                    LanguageChoice.ENGLISH -> stringResource(R.string.language_name_english)
                    LanguageChoice.CHINESE -> stringResource(R.string.language_name_chinese)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = choice == selected, role = Role.RadioButton, onClick = { onSelect(choice) })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = choice == selected, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun TimeZoneCard(override: String, effective: String, onSave: (String) -> Boolean) {
    var followDevice by rememberSaveable(override) { mutableStateOf(override.isBlank()) }
    var input by rememberSaveable(override) { mutableStateOf(override) }
    var invalid by rememberSaveable(override) { mutableStateOf(false) }
    InfoCard(title = stringResource(R.string.settings_time_zone)) {
        SwitchRow(
            label = stringResource(R.string.settings_time_zone_follow),
            checked = followDevice,
            onCheckedChange = { follow ->
                followDevice = follow
                if (follow) {
                    invalid = false
                    onSave("")
                }
            },
        )
        if (!followDevice) {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    invalid = false
                },
                singleLine = true,
                label = { Text(stringResource(R.string.settings_time_zone_hint)) },
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(stringResource(R.string.settings_time_zone_invalid)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { invalid = !onSave(input) }) { Text(stringResource(R.string.common_save)) }
        }
        Text(
            text = stringResource(R.string.settings_time_zone_current, effective),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotificationsCard(morningBrief: Boolean, onMorningBrief: (Boolean) -> Unit) {
    InfoCard(title = stringResource(R.string.settings_notifications)) {
        NotifySwitch(
            label = stringResource(R.string.brief_title),
            summary = stringResource(R.string.brief_switch_summary),
            checked = morningBrief,
            channelId = CoreNotifications.BRIEF_CHANNEL_ID,
            onCheckedChange = onMorningBrief,
        )
        Text(
            text = stringResource(R.string.settings_notifications_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The module switches; also used by the onboarding.
 * 模块开关；首次启动引导也用它。
 */
@Composable
internal fun ModulesCard(modules: List<ModuleToggle>, onToggle: (String, Boolean) -> Unit) {
    InfoCard(title = stringResource(R.string.settings_modules)) {
        if (modules.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_modules_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        modules.forEach { toggle ->
            SwitchRow(
                label = stringResource(toggle.module.title),
                summary = stringResource(toggle.module.summary),
                checked = toggle.enabled,
                onCheckedChange = { onToggle(toggle.module.id, it) },
            )
        }
    }
}

@Composable
private fun AboutCard() {
    val uriHandler = LocalUriHandler.current
    InfoCard(title = stringResource(R.string.settings_about)) {
        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { uriHandler.openUri(SharedHttpClient.REPO_URL) }) {
            Text(stringResource(R.string.settings_source_code))
        }
    }
}
