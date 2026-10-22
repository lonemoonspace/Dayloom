package io.github.lonemoonspace.dayloom.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A status pill: 12% status colour behind status-coloured text. The wording itself states the status, so no leading
 * symbol ("!", check mark) is added in front of it. [maxLines] above 1 lets a caller stack the text, centred, to keep the
 * pill narrow.
 * 状态胶囊：状态色 12% 底 + 状态色文字。文字本身已说明状态，前面不加「!」、对勾之类的符号。[maxLines] 大于 1 时调用方可以
 * 把文字分行居中叠放，让胶囊窄一些。
 */
@Composable
fun StatusChip(text: String, color: Color, maxLines: Int = 1) {
    Surface(color = color.copy(alpha = 0.12f), shape = if (maxLines > 1) RoundedCornerShape(12.dp) else CircleShape) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = maxLines,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
