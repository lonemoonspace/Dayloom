package io.github.lonemoonspace.dayloom.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R

/**
 * Placeholder bars shaped like the content while the first load is running; screen readers hear a single "Loading".
 * 首次加载时用与内容形状相近的占位条；读屏只读一句「加载中」。
 */
@Composable
fun SkeletonLines(modifier: Modifier = Modifier, lines: Int = 2) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.10f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = pulse)
    val loading = stringResource(R.string.common_loading)
    Column(
        modifier = modifier.semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(lines) { i ->
            Box(
                Modifier
                    .fillMaxWidth(if (i == 0) 0.6f else 0.9f - 0.15f * (i - 1))
                    .height(if (i == 0) 14.dp else 10.dp)
                    .background(color, RoundedCornerShape(4.dp)),
            )
        }
    }
}
