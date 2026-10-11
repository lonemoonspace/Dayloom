package io.github.lonemoonspace.dayloom.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.remember
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.HorizontalDivider
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
import java.time.LocalTime
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.BuildConfig
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.LanguageChoice
import io.github.lonemoonspace.dayloom.core.network.SharedHttpClient
import io.github.lonemoonspace.dayloom.core.notify.CoreNotifications
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.NotifySwitch
import io.github.lonemoonspace.dayloom.core.ui.SwitchRow
import io.github.lonemoonspace.dayloom.core.ui.rememberTimeFormatter
import io.github.lonemoonspace.dayloom.core.ui.plusBars

@Composable
fun SettingsScreen(
    state: SettingsState,
    onLanguage: (LanguageChoice) -> Unit,
    onTimeZone: (String) -> Boolean,
    onModuleEnabled: (String, Boolean) -> Unit,
    onMorningBrief: (Boolean) -> Unit,
    onMorningBriefTime: (Int) -> Unit,
    /** The shared places card. / 共用的地点卡片。 */
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
        item(key = "notifications") { NotificationsCard(state.morningBrief, state.morningBriefMinute, onMorningBrief, onMorningBriefTime) }
        items(state.modules, key = { "module:${it.module.id}" }) { toggle -> ModuleSettingsCard(toggle, onModuleEnabled) }
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
private fun NotificationsCard(morningBrief: Boolean, briefMinute: Int, onMorningBrief: (Boolean) -> Unit, onBriefTime: (Int) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    InfoCard(title = stringResource(R.string.settings_notifications)) {
        NotifySwitch(
            label = stringResource(R.string.brief_title),
            summary = stringResource(R.string.brief_switch_summary),
            checked = morningBrief,
            channelId = CoreNotifications.BRIEF_CHANNEL_ID,
            onCheckedChange = onMorningBrief,
        )
        if (morningBrief) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.brief_time), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TimeButton(LocalTime.MIDNIGHT.plusMinutes(briefMinute.toLong()).format(rememberTimeFormatter())) { picking = true }
            }
        }
        Text(
            text = stringResource(R.string.settings_notifications_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (picking) {
        TimeDialog(
            initialMinute = briefMinute,
            onDismiss = { picking = false },
            onConfirm = { minute ->
                picking = false
                onBriefTime(minute)
            },
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

/**
 * One card per module: its switch in the title row, then its settings. While the module is off the settings stay visible but
 * dimmed and ignore touches, so the user sees what turning it on would offer.
 * 每个模块一张卡片：标题行里是开关，下面是它的设置。模块关闭时设置仍然可见，但变灰且不响应触摸，用户能看到开启后有哪些设置。
 */
@Composable
private fun ModuleSettingsCard(toggle: ModuleToggle, onToggle: (String, Boolean) -> Unit) {
    val title = stringResource(toggle.module.title)
    InfoCard(
        title = title,
        icon = toggle.module.icon,
        titleAction = {
            Switch(
                checked = toggle.enabled,
                onCheckedChange = { onToggle(toggle.module.id, it) },
                modifier = Modifier.semantics { contentDescription = title },
            )
        },
    ) {
        Text(
            text = stringResource(toggle.module.summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (toggle.sections.isEmpty()) return@InfoCard
        Column(
            modifier = Modifier
                .padding(top = 6.dp)
                .then(if (toggle.enabled) Modifier else Modifier.lockedSettings()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            toggle.sections.forEachIndexed { index, section ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                // A section repeats the module's name only when it adds something (train, bus, favourite stops).
                // 只有标题带来新信息时（火车、公交、收藏站点）才显示分区标题。
                section.title?.let { Text(stringResource(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                section.content()
            }
        }
    }
}

/**
 * Dims the settings of a module that is off and swallows touches before any control sees them; screen readers skip the
 * controls and hear that they are unavailable.
 * 让关闭模块的设置变灰，并在任何控件收到之前吞掉触摸；读屏软件跳过这些控件，并读出它们不可用。
 */
private fun Modifier.lockedSettings(): Modifier = this
    .alpha(0.38f)
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
    .clearAndSetSemantics { disabled() }

/**
 * Version, license and source code, plus three dialogs: where the data comes from, what the app keeps and sends, and the
 * open-source licenses (MIT requires shipping lunar-java's notice with the app).
 * 版本、许可证与源代码，外加三个对话框：数据从哪里来、App 保存与发送什么、开源许可（MIT 要求随 App 附上 lunar-java 的声明）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutCard() {
    val uriHandler = LocalUriHandler.current
    var showing by rememberSaveable { mutableStateOf<AboutPage?>(null) }
    InfoCard(title = stringResource(R.string.settings_about)) {
        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // One line when it fits (Chinese), wrapping only when it does not. / 放得下就排一行（中文），放不下才换行。
        FlowRow(Modifier.fillMaxWidth()) {
            TextButton(onClick = { showing = AboutPage.SOURCES }) { Text(stringResource(R.string.about_data_sources)) }
            TextButton(onClick = { showing = AboutPage.PRIVACY }) { Text(stringResource(R.string.about_privacy)) }
            TextButton(onClick = { showing = AboutPage.LICENSES }) { Text(stringResource(R.string.about_licenses)) }
            TextButton(onClick = { uriHandler.openUri(SharedHttpClient.REPO_URL) }) { Text(stringResource(R.string.settings_source_code)) }
        }
    }
    showing?.let { page -> AboutDialog(page) { showing = null } }
}

private enum class AboutPage { SOURCES, PRIVACY, LICENSES }

@Composable
private fun AboutDialog(page: AboutPage, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val body = when (page) {
        AboutPage.SOURCES -> listOf(
            R.string.about_source_weather,
            R.string.about_source_transit,
            R.string.about_source_places,
            R.string.about_source_google,
            R.string.about_source_football,
            R.string.about_source_news,
            R.string.about_source_lunar,
        ).map { stringResource(it) }.joinToString("\n\n")
        AboutPage.PRIVACY -> stringResource(R.string.about_privacy_body)
        // Kept as a raw file: license texts are legal text and stay in English. / 放在 raw 文件里：许可证是法律文本，保持英文原文。
        AboutPage.LICENSES -> remember(resources) { resources.openRawResource(R.raw.third_party_licenses).bufferedReader().use { it.readText() } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when (page) {
                        AboutPage.SOURCES -> R.string.about_data_sources
                        AboutPage.PRIVACY -> R.string.about_privacy
                        AboutPage.LICENSES -> R.string.about_licenses
                    },
                ),
            )
        },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(body, style = if (page == AboutPage.LICENSES) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_close)) } },
    )
}
