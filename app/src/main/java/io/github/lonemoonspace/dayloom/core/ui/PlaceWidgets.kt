package io.github.lonemoonspace.dayloom.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.location.Place

/** "Home", "Work" or the custom place's own label. / 「家」「公司」或自定义地点自己的标签。 */
@Composable
fun placeTitle(place: Place): String = when (place.id) {
    Place.HOME -> stringResource(R.string.place_home)
    Place.WORK -> stringResource(R.string.place_work)
    else -> place.label.ifBlank { place.name }
}

/** [placeTitle] for text resolved later (notifications). / 供稍后解析的文字（通知）使用的 [placeTitle]。 */
fun placeTitleText(place: Place): UiText = when (place.id) {
    Place.HOME -> UiText.Res(R.string.place_home)
    Place.WORK -> UiText.Res(R.string.place_work)
    else -> UiText.Raw(place.label.ifBlank { place.name })
}

/**
 * Picks one of the saved places for a module setting ("weather for: Home"). Places themselves are edited in the Places card.
 * 为模块设置选择一个已保存的地点（「天气地点：家」）。地点本身在「地点」卡片里编辑。
 */
@Composable
fun PlacePicker(places: List<Place>, selectedId: String, onSelect: (String) -> Unit) {
    if (places.isEmpty()) {
        Text(
            text = stringResource(R.string.places_none_saved),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(Modifier.selectableGroup()) {
        places.forEach { place ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = place.id == selectedId, role = Role.RadioButton, onClick = { onSelect(place.id) })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = place.id == selectedId, onClick = null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(placeTitle(place), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
