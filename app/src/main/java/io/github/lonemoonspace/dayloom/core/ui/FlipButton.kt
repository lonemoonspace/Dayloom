package io.github.lonemoonspace.dayloom.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R

/**
 * Turns a commute card round, for an [InfoCard]'s title row. Drawn small so the row keeps its height; Compose still gives
 * a pointer target this small the minimum touch size around it. Tinted while the card looks the other way than the clock says.
 * 把通勤卡片掉头的按钮，放在 [InfoCard] 的标题行里。画得小是为了不撑高标题行；这么小的点按目标 Compose 仍会按最小触控尺寸
 * 向四周扩展。卡片方向与时钟给出的相反时着色。
 */
@Composable
fun FlipButton(flipped: Boolean, onFlip: () -> Unit) {
    Spacer(Modifier.width(6.dp))
    Icon(
        painter = painterResource(R.drawable.ic_swap),
        contentDescription = stringResource(R.string.common_flip_direction),
        tint = if (flipped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onFlip),
    )
}
