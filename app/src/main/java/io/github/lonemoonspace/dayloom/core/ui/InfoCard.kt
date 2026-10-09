package io.github.lonemoonspace.dayloom.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.ui.theme.appSurfaces

/**
 * The compact card used everywhere: card colour, 12/10dp padding and one title row — an optional module [icon], the
 * [title], a muted [subtitle] (route, place) that gives way first on a narrow screen, then a "Cached" chip when [stale] and
 * a faint [meta] text ("1 min ago") at the end.
 * 全 App 共用的紧凑卡片：卡片底色、12/10dp 内边距与一行标题——可选的模块 [icon]、[title]、窄屏时先让位的浅色 [subtitle]
 * （路线、地点），[stale] 时的「缓存」小标签，末尾是淡色的 [meta] 文字（「1 分钟前」）。
 */
@Composable
fun InfoCard(
    title: String?,
    modifier: Modifier = Modifier,
    stale: Boolean = false,
    @DrawableRes icon: Int? = null,
    subtitle: String? = null,
    meta: String? = null,
    /** Trailing slot in the title row (a drag handle in edit mode). / 标题行末尾的插槽（编辑模式下的拖动把手）。 */
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.appSurfaces.card,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        // Smooth size changes when data arrives instead of a jump. / 数据到达时尺寸平滑过渡，而不是跳一下。
        Column(
            Modifier
                .animateContentSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (title != null || titleAction != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        // The title says what the card is; the icon is not read out. / 标题已说明卡片内容，图标不朗读。
                        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = title.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (stale) {
                        Spacer(Modifier.width(6.dp))
                        CacheChip()
                    }
                    Spacer(Modifier.weight(1f))
                    if (meta != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                    }
                    titleAction?.invoke()
                }
                Spacer(Modifier.size(4.dp))
            }
            content()
        }
    }
}

@Composable
private fun CacheChip() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = CircleShape,
    ) {
        Text(
            text = stringResource(R.string.common_cached),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}
