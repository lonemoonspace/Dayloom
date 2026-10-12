package io.github.lonemoonspace.dayloom.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A status pill: 12% status colour behind status-coloured text. The wording itself states the status, so no leading
 * symbol ("!", check mark) is added in front of it.
 * 状态胶囊：状态色 12% 底 + 状态色文字。文字本身已说明状态，前面不加「!」、对勾之类的符号。
 */
@Composable
fun StatusChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = CircleShape) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
