package io.github.lonemoonspace.dayloom.core.ui

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
 * A status pill: 12% status colour behind status-coloured text. The wording itself states the status, so no leading
 * symbol ("!", check mark) is added in front of it. [maxLines] above 1 lets the text stack or wrap, centred, when the
 * caller keeps the pill narrow; such a caller passes a [shape] with smaller corners, so one- and two-line pills match.
 * 状态胶囊：状态色 12% 底 + 状态色文字。文字本身已说明状态，前面不加「!」、对勾之类的符号。[maxLines] 大于 1 时，调用方限制
 * 胶囊宽度时文字可以分行居中；这样的调用方传入圆角更小的 [shape]，一行与两行的胶囊才一致。
 */
@Composable
fun StatusChip(text: String, color: Color, maxLines: Int = 1, shape: Shape = CircleShape) {
    Surface(color = color.copy(alpha = 0.12f), shape = shape) {
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
