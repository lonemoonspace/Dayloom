package io.github.lonemoonspace.dayloom.core.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.ui.theme.appSurfaces

/**
 * The compact card used everywhere: card colour, 12/10dp padding, titleSmall title, and a "Cached" chip when [stale].
 * 全 App 共用的紧凑卡片：卡片底色、12/10dp 内边距、titleSmall 标题，[stale] 时带一个「缓存」小标签。
 */
@Composable
fun InfoCard(
    title: String?,
    modifier: Modifier = Modifier,
    stale: Boolean = false,
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
                    Text(
                        text = title.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (stale) {
                        Spacer(Modifier.width(6.dp))
                        CacheChip()
                    }
                    if (titleAction != null) {
                        Spacer(Modifier.weight(1f))
                        titleAction()
                    }
                }
                Spacer(Modifier.size(6.dp))
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
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = stringResource(R.string.common_cached),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}
