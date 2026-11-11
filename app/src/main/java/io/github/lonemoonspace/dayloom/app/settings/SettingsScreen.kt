package io.github.lonemoonspace.dayloom.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.lonemoonspace.dayloom.core.i18n.asString
import io.github.lonemoonspace.dayloom.core.location.Place
import io.github.lonemoonspace.dayloom.core.ui.placeTitle
import io.github.lonemoonspace.dayloom.core.ui.theme.ThemePreset
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
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
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

/**
 * The pages under the settings list, besides one page per module. [segment] is the last part of the page's route.
 * 设置列表下面的各个页面（另外每个模块一页）。[segment] 是页面路由的最后一段。
 */
enum class SettingsPage(val segment: String, @param:StringRes val title: Int, @param:DrawableRes val icon: Int) {
    APPEARANCE("appearance", R.string.settings_theme, R.drawable.ic_settings_appearance),
    LANGUAGE("language", R.string.settings_language_time, R.drawable.ic_settings_language),
    PLACES("places", R.string.settings_places, R.drawable.ic_settings_places),
    NOTIFICATIONS("notifications", R.string.settings_notifications, R.drawable.ic_settings_notifications),
    ABOUT("about", R.string.settings_about, R.drawable.ic_settings_about),
    ;

    companion object {
        fun of(segment: String): SettingsPage? = entries.firstOrNull { it.segment == segment }
    }
}

private val SettingsPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)

/**
 * The settings list: one row per page and per module, each with a line saying how it is set now, so the right page can be
 * found without scrolling through every setting. A module's switch stays in its row; tapping the rest opens its page.
 * 设置列表：每个页面、每个模块各一行，下面一行小字写着现在的设置，不用翻过所有设置就能找到要改的那一页。模块的开关留在
 * 这一行里；点这一行的其他地方打开它的设置页。
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    places: List<Place>,
    onOpenPage: (SettingsPage) -> Unit,
    onOpenModule: (String) -> Unit,
    onModuleEnabled: (String, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = SettingsPadding.plusBars(top = true),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "general") {
            DirectoryGroup(stringResource(R.string.settings_group_general)) {
                listOf(SettingsPage.APPEARANCE, SettingsPage.LANGUAGE, SettingsPage.PLACES, SettingsPage.NOTIFICATIONS)
                    .forEachIndexed { index, page ->
                        if (index > 0) RowDivider()
                        PageRow(page, pageStatus(page, state, places), onOpenPage)
                    }
            }
        }
        if (state.modules.isNotEmpty()) {
            item(key = "modules") {
                DirectoryGroup(stringResource(R.string.settings_modules)) {
                    state.modules.forEachIndexed { index, toggle ->
                        if (index > 0) RowDivider()
                        ModuleRow(toggle, onOpenModule, onModuleEnabled)
                    }
                }
            }
        }
        item(key = "other") {
            DirectoryGroup(stringResource(R.string.settings_group_other)) {
                PageRow(SettingsPage.ABOUT, pageStatus(SettingsPage.ABOUT, state, places), onOpenPage)
            }
        }
    }
}

/**
 * One page of general settings. [sharedData] draws the places card, which lives in its own view model.
 * 一页通用设置。[sharedData] 画地点卡片，它有自己的 ViewModel。
 */
@Composable
fun SettingsPageScreen(
    page: SettingsPage,
    state: SettingsState,
    onLanguage: (LanguageChoice) -> Unit,
    onTimeZone: (String) -> Boolean,
    onMorningBrief: (Boolean) -> Unit,
    onMorningBriefTime: (Int) -> Unit,
    onThemePreset: (String) -> Unit,
    onWeaveBackground: (Boolean) -> Unit,
    sharedData: @Composable () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = SettingsPadding.plusBars(top = true),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (page) {
            SettingsPage.APPEARANCE -> item(key = "theme") { ThemeCard(state.themePreset, onThemePreset, state.weaveBackground, onWeaveBackground) }
            SettingsPage.LANGUAGE -> {
                item(key = "language") { LanguageCard(state.language, onLanguage) }
                item(key = "timezone") { TimeZoneCard(state.timeZoneOverride, state.effectiveZone, onTimeZone) }
            }
            SettingsPage.PLACES -> item(key = "places") { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { sharedData() } }
            SettingsPage.NOTIFICATIONS -> item(key = "notifications") {
                NotificationsCard(state.morningBrief, state.morningBriefMinute, onMorningBrief, onMorningBriefTime)
            }
            SettingsPage.ABOUT -> item(key = "about") { AboutCard() }
        }
    }
}

/**
 * One module's page: its switch with the module's summary, then its settings. While the module is off the settings stay
 * visible but dimmed and ignore touches, so the user sees what turning it on would offer.
 * 一个模块的设置页：先是开关和模块简介，下面是它的设置。模块关闭时设置仍然可见，但变灰且不响应触摸，用户能看到开启后有哪些设置。
 */
@Composable
fun ModuleSettingsScreen(toggle: ModuleToggle, onToggle: (String, Boolean) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = SettingsPadding.plusBars(top = true),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "switch") {
            InfoCard(title = null) {
                SwitchRow(
                    label = stringResource(R.string.settings_module_switch),
                    summary = stringResource(toggle.module.summary),
                    checked = toggle.enabled,
                    onCheckedChange = { onToggle(toggle.module.id, it) },
                )
            }
        }
        if (toggle.sections.isNotEmpty()) {
            item(key = "sections") {
                InfoCard(title = null) {
                    Column(
                        modifier = if (toggle.enabled) Modifier else Modifier.lockedSettings(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        toggle.sections.forEachIndexed { index, section ->
                            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            // A section repeats the module's name only when it adds something. / 只有标题带来新信息时才显示分区标题。
                            section.title?.let { Text(stringResource(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                            section.content()
                        }
                    }
                }
            }
        }
    }
}

/** A small coloured heading over a card of rows. / 一组行上方的彩色小标题。 */
@Composable
private fun DirectoryGroup(title: String, rows: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 6.dp),
        )
        InfoCard(title = null, content = rows)
    }
}

/** Starts where the row text starts, so the icons stand apart. / 从行文字的起点开始画，图标列保持独立。 */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = ICON_COLUMN + ICON_GAP),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun PageRow(page: SettingsPage, status: String, onOpen: (SettingsPage) -> Unit) {
    DirectoryRow(
        icon = page.icon,
        title = stringResource(page.title),
        status = status,
        modifier = Modifier.clickable(role = Role.Button) { onOpen(page) },
    ) {
        // Nudged right so the arrow's visible edge lines up with the switches' right edge. / 往右挪一点，箭头的可见边缘与开关的右边缘对齐。
        Box(Modifier.width(TRAILING_WIDTH), contentAlignment = Alignment.CenterEnd) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = 7.dp),
            )
        }
    }
}

/**
 * The switch has its own touch target behind a thin divider; the rest of the row opens the module's page.
 * 开关有自己的触摸区域，用一道细竖线隔开；这一行的其他地方打开模块的设置页。
 */
@Composable
private fun ModuleRow(toggle: ModuleToggle, onOpen: (String) -> Unit, onToggle: (String, Boolean) -> Unit) {
    val title = stringResource(toggle.module.title)
    val status = moduleStatus(toggle)
    Row(verticalAlignment = Alignment.CenterVertically) {
        DirectoryRow(
            icon = toggle.module.icon,
            title = title,
            status = status.first,
            statusColor = if (status.second) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            dimmed = !toggle.enabled,
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button) { onOpen(toggle.module.id) },
        )
        VerticalDivider(Modifier.height(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(TRAILING_WIDTH), contentAlignment = Alignment.CenterEnd) {
            Switch(
                checked = toggle.enabled,
                onCheckedChange = { onToggle(toggle.module.id, it) },
                modifier = Modifier.semantics { contentDescription = title },
            )
        }
    }
}

@Composable
private fun DirectoryRow(
    @DrawableRes icon: Int,
    title: String,
    status: String,
    modifier: Modifier = Modifier,
    statusColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    dimmed: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val alpha = if (dimmed) DIMMED_ALPHA else 1f
        Box(Modifier.width(ICON_COLUMN), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).alpha(alpha))
        }
        Spacer(Modifier.width(ICON_GAP))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(alpha))
            // Always one line, also while a module's status is still loading, so rows never change height. / 始终占一行（模块状态还在读取时也是），行高不会跳。
            Text(status, style = MaterialTheme.typography.bodySmall, color = statusColor, maxLines = 1, overflow = TextOverflow.Ellipsis, minLines = 1, modifier = Modifier.alpha(alpha))
        }
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
    }
}

/** The line under a module's name, and whether it asks for setup (shown in the accent colour). / 模块名下面那行字，以及它是否在提示去设置（用主色显示）。 */
@Composable
private fun moduleStatus(toggle: ModuleToggle): Pair<String, Boolean> {
    if (!toggle.enabled) return stringResource(R.string.settings_status_off) to false
    return when (val status = toggle.status) {
        ModuleStatus.Loading -> "" to false
        is ModuleStatus.NeedsSetup -> stringResource(R.string.error_not_configured, status.what.asString()) to true
        is ModuleStatus.Line -> (status.text?.asString() ?: stringResource(toggle.module.summary)) to false
    }
}

@Composable
private fun pageStatus(page: SettingsPage, state: SettingsState, places: List<Place>): String = when (page) {
    SettingsPage.APPEARANCE -> {
        val theme = stringResource(ThemePreset.of(state.themePreset)?.nameRes() ?: R.string.theme_wallpaper)
        if (state.weaveBackground) stringResource(R.string.settings_status_pair, theme, stringResource(R.string.theme_weave)) else theme
    }
    SettingsPage.LANGUAGE -> stringResource(R.string.settings_status_pair, languageLabel(state.language), state.effectiveZone)
    SettingsPage.PLACES -> places.takeIf { it.isNotEmpty() }
        ?.map { placeTitle(it) }
        ?.joinToString(stringResource(R.string.settings_status_list_separator))
        ?: stringResource(R.string.places_not_set)
    SettingsPage.NOTIFICATIONS -> if (state.morningBrief) {
        stringResource(R.string.settings_status_brief, LocalTime.MIDNIGHT.plusMinutes(state.morningBriefMinute.toLong()).format(rememberTimeFormatter()))
    } else {
        stringResource(R.string.settings_status_brief_off)
    }
    SettingsPage.ABOUT -> stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)
}

@Composable
private fun languageLabel(choice: LanguageChoice): String = when (choice) {
    LanguageChoice.SYSTEM -> stringResource(R.string.settings_language_system)
    LanguageChoice.ENGLISH -> stringResource(R.string.language_name_english)
    LanguageChoice.CHINESE -> stringResource(R.string.language_name_chinese)
}

/** Icon column and the gap after it, shared by the rows and the dividers between them. / 图标列及其后的间距，行与行间分隔线共用。 */
private val ICON_COLUMN = 24.dp
private val ICON_GAP = 14.dp

/** Wide enough for a Material switch; the arrows use the same width so both end on one line. / 一个 Material 开关的宽度；箭头用同样的宽度，两者右边缘对齐。 */
private val TRAILING_WIDTH = 52.dp

/** Material's alpha for disabled content. / Material 规定的不可用内容透明度。 */
private const val DIMMED_ALPHA = 0.38f

/** Also used by the onboarding. / 首次启动引导也用它。 */
@Composable
internal fun LanguageCard(selected: LanguageChoice, onSelect: (LanguageChoice) -> Unit) {
    InfoCard(title = stringResource(R.string.settings_language)) {
        Column(Modifier.selectableGroup()) {
            LanguageChoice.entries.forEach { choice ->
                val label = languageLabel(choice)
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
            TextButton(
                onClick = {
                    invalid = !onSave(input)
                    // A blank zone means following the device, so the switch says so. / 空白时区就是跟随设备，开关要如实显示。
                    if (!invalid && input.isBlank()) followDevice = true
                },
            ) { Text(stringResource(R.string.common_save)) }
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
 * Dims the settings of a module that is off and swallows presses before any control sees them; screen readers skip the
 * controls and hear that they are unavailable. Only the press: controls need an unconsumed press to react, while the list
 * needs unconsumed moves to scroll and an unconsumed release to fling, so a swipe that starts here still scrolls the page.
 * 让关闭模块的设置变灰，并在任何控件收到之前吞掉按下；读屏软件跳过这些控件，并读出它们不可用。只吞按下：控件要收到未被消费的
 * 按下才会响应，而列表要靠未被消费的移动来滚动、靠未被消费的抬起来惯性滑动，所以从这里开始的滑动照样能滚动页面。
 */
private fun Modifier.lockedSettings(): Modifier = this
    .alpha(0.38f)
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes
                    .filter { it.changedToDownIgnoreConsumed() }
                    .forEach { it.consume() }
            }
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
            TextButton(onClick = {
                // Compose rethrows "no app can open this" as IllegalArgumentException. / 没有 App 能打开链接时，Compose 会抛出 IllegalArgumentException。
                try {
                    uriHandler.openUri(SharedHttpClient.REPO_URL)
                } catch (_: IllegalArgumentException) {
                    // Nothing to open with. / 没有可以打开的 App。
                }
            }) { Text(stringResource(R.string.settings_source_code)) }
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
