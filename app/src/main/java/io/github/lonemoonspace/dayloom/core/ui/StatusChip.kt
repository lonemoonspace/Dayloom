package io.github.lonemoonspace.dayloom.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A status pill: 14% status colour behind status-coloured text with a subtle border.
 * The wording itself states the status, so no leading symbol ("!", check mark) is added in front of it.
 * 状态胶囊：状态色 14% 底 + 微光边框 + 状态色文字。文字本身已说明状态，前面不加「!」、对勾之类的符号。
 */
@Composable
fun StatusChip(text: String, color: Color, maxLines: Int = 1, shape: Shape = CircleShape) {
    Surface(
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.35f)),
        shape = shape,
    ) {
        Text(
            text = text,
            color = color,
            style = statusChipStyle(),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = STATUS_CHIP_PADDING, vertical = 2.dp),
        )
    }
}

/** The text style of [StatusChip], for callers that size a column to fit one. / [StatusChip] 的文字样式，供按它定列宽的调用方使用。 */
@Composable
fun statusChipStyle(): TextStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)

/** Horizontal padding on each side of [StatusChip]'s text. / [StatusChip] 文字左右各自的内边距。 */
val STATUS_CHIP_PADDING = 8.dp
