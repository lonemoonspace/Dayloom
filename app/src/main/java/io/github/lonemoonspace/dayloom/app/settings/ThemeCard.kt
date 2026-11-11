package io.github.lonemoonspace.dayloom.app.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.SwitchRow
import io.github.lonemoonspace.dayloom.core.ui.theme.ThemePreset
import io.github.lonemoonspace.dayloom.core.ui.theme.appSurfacesFor
import io.github.lonemoonspace.dayloom.core.ui.theme.wallpaperScheme

/**
 * The wallpaper colours plus each [ThemePreset], with three dots (page, card, accent) so a scheme can be judged before
 * switching. In light mode the dark-only presets are greyed out and say so; the selection itself is kept, so they come back
 * when the phone turns dark. The woven-background switch sits underneath.
 * 壁纸取色加各个 [ThemePreset]，每项带三个色点（页面、卡片、主色），切换前就能大致看出效果。浅色模式下只有深色的预设置灰并
 * 注明；已选的值保持不变，手机切到深色时自动生效。下面是背景织纹的开关。
 */
@Composable
internal fun ThemeCard(selected: String, onSelect: (String) -> Unit, weave: Boolean, onWeave: (Boolean) -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val current = ThemePreset.of(selected)
    InfoCard(title = stringResource(R.string.settings_theme)) {
        Column(Modifier.selectableGroup()) {
            ThemeRow(
                label = stringResource(R.string.theme_wallpaper),
                note = stringResource(R.string.theme_default),
                selected = current == null,
                enabled = true,
                scheme = wallpaperScheme(context, dark),
                dark = dark,
                onClick = { onSelect("") },
            )
            ThemePreset.entries.forEach { preset ->
                val usable = dark || !preset.darkOnly
                ThemeRow(
                    label = stringResource(preset.nameRes()),
                    note = if (preset.darkOnly) stringResource(R.string.theme_dark_only) else null,
                    selected = current == preset,
                    enabled = usable,
                    // A greyed-out preset still shows its own dark colours. / 置灰的预设仍显示它自己的深色配色。
                    scheme = if (usable) preset.schemeFor(dark) else preset.dark,
                    dark = dark || !usable,
                    onClick = { onSelect(preset.id) },
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        SwitchRow(
            label = stringResource(R.string.theme_weave),
            checked = weave,
            onCheckedChange = onWeave,
            summary = stringResource(R.string.theme_weave_summary),
        )
    }
}

@Composable
private fun ThemeRow(
    label: String,
    note: String?,
    selected: Boolean,
    enabled: Boolean,
    scheme: ColorScheme,
    dark: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
            maxLines = 1,
        )
        if (note != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        Swatches(scheme, dark, Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA))
    }
}

/** Decorative: the name already identifies the scheme. / 装饰性的：名称已经说明是哪套配色。 */
@Composable
private fun Swatches(scheme: ColorScheme, dark: Boolean, modifier: Modifier) {
    val edge = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (color in listOf(scheme.background, appSurfacesFor(scheme, dark).card, scheme.primary)) {
            Box(
                Modifier
                    .size(16.dp)
                    .background(color, CircleShape)
                    .border(0.5.dp, edge, CircleShape),
            )
        }
    }
}

@StringRes
internal fun ThemePreset.nameRes(): Int = when (this) {
    ThemePreset.NORDIC_DAWN -> R.string.theme_nordic_dawn
    ThemePreset.WARM_LINEN -> R.string.theme_warm_linen
    ThemePreset.SAGE_FOREST -> R.string.theme_sage_forest
    ThemePreset.OLED_OBSIDIAN -> R.string.theme_oled_obsidian
    ThemePreset.AURORA_TWILIGHT -> R.string.theme_aurora_twilight
}

/** Material's alpha for disabled content. / Material 规定的不可用内容透明度。 */
private const val DISABLED_ALPHA = 0.38f
