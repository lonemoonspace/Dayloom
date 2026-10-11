package io.github.lonemoonspace.dayloom.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A status pill: 12% status colour behind status-coloured text. With [icon] a small icon of the same colour leads the text,
 * so the status does not rely on colour alone (colour blindness, bright sunlight).
 * 状态胶囊：状态色 12% 底 + 状态色文字。给出 [icon] 时文字前加一个同色小图标，让状态不只靠颜色区分（色弱、强光下也能辨认）。
 */
@Composable
fun StatusChip(text: String, color: Color, @DrawableRes icon: Int? = null) {
    Surface(color = color.copy(alpha = 0.12f), shape = CircleShape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = if (icon != null) 6.dp else 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        ) {
            if (icon != null) {
                // The text already states the status; the icon is not read out again. / 文字已说明状态，图标不重复朗读。
                Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(text = text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
