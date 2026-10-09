package io.github.lonemoonspace.dayloom.core.ui

// A 44dp glass top bar and a 52dp floating glass bottom bar instead of the Material 3 components, whose 64dp + 80dp
// minimum heights take about a sixth of a phone screen. Content scrolls under both bars.
// 44dp 玻璃顶栏与 52dp 悬浮玻璃底栏，代替 Material 3 自带组件：后者 64dp + 80dp 的最小高度会占掉手机屏幕约六分之一。内容从两条栏下面滚过。

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun CompactTopBar(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val edge = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glass()
                // A hairline so the bar stays distinct when light cards scroll beneath it. / 一道细线，浅色卡片滚过时栏与内容仍有分界。
                .drawWithContent {
                    drawContent()
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(edge, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx())
                }
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(44.dp)
                .padding(start = 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
            Spacer(Modifier.width(4.dp))
        }
    }
}

class NavTab(
    val selected: Boolean,
    /** Accessible name; the tabs show icons only. / 无障碍名称；标签只显示图标。 */
    val label: String,
    val onClick: () -> Unit,
    val icon: @Composable (tint: Color) -> Unit,
)

private val PillShape = RoundedCornerShape(50)

/**
 * A floating glass pill 12dp above the gesture area; width is capped so it does not stretch across a tablet.
 * 悬浮的玻璃胶囊，距手势区 12dp；宽度封顶，平板上不会拉成一整条。
 */
@Composable
fun CompactNavBar(tabs: List<NavTab>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(PillShape)
                .glass()
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), PillShape)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            tabs.forEach { tab -> NavTabItem(tab, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun NavTabItem(tab: NavTab, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val tint = if (tab.selected) primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = tab.selected,
                onClick = tab.onClick,
                role = Role.Tab,
                interactionSource = null,
                indication = ripple(bounded = false, radius = 24.dp),
            )
            .semantics { contentDescription = tab.label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(if (tab.selected) primary.copy(alpha = 0.18f) else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            tab.icon(tint)
        }
    }
}
