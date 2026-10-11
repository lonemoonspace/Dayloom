package io.github.lonemoonspace.dayloom.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.SkeletonLines
import io.github.lonemoonspace.dayloom.core.ui.plusBars
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The home screen: every enabled module's cards in order. In [editing] mode cards collapse to their titles and can be
 * dragged by the handle; each card also offers "move up / move down" accessibility actions so TalkBack users can reorder too.
 * 首页：按顺序显示所有已开启模块的卡片。[editing] 模式下卡片收起为标题，可拖动把手排序；每张卡片同时提供「上移/下移」
 * 无障碍操作，TalkBack 用户也能排序。
 */
@Composable
fun HomeScreen(
    state: HomeState,
    editing: Boolean,
    onSaveOrder: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
) {
    when {
        !state.loaded -> HomeSkeleton()
        state.cards.isEmpty() -> EmptyHome(onOpenSettings)
        editing -> EditableCardList(state.userOrder, onSaveOrder)
        else -> CardList(state.cards)
    }
}

private val ListPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)

@Composable
private fun CardList(cards: List<HomeCardEntry>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListPadding.plusBars(top = true),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(cards, key = { it.key }) { entry ->
            Box(Modifier.animateItem()) { entry.card.content() }
        }
    }
}

@Composable
private fun EditableCardList(cards: List<HomeCardEntry>, onSaveOrder: (List<String>) -> Unit) {
    // Local order while dragging; saved after every move so leaving edit mode never loses a change.
    // 拖动期间的本地顺序；每次移动后都保存，退出编辑模式时不会丢失修改。
    var order by remember { mutableStateOf(cards.map { it.key }) }
    LaunchedEffect(cards.map { it.key }.toSet()) {
        order = CardOrderPolicy.userOrder(cards.map { CardOrderPolicy.Card(it.key, 0) }, order)
    }
    val byKey = cards.associateBy { it.key }
    fun move(from: Int, to: Int) {
        val moved = CardOrderPolicy.move(order, from, to)
        if (moved != order) {
            order = moved
            onSaveOrder(moved)
        }
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to -> move(from.index, to.index) }
    val moveUp = stringResource(R.string.home_move_up)
    val moveDown = stringResource(R.string.home_move_down)
    val dragHandle = stringResource(R.string.home_drag_handle)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListPadding.plusBars(top = true),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(order, key = { it }) { key ->
            val entry = byKey[key] ?: return@items
            ReorderableItem(reorderState, key = key) { isDragging ->
                val index = order.indexOf(key)
                InfoCard(
                    title = stringResource(entry.card.title),
                    modifier = Modifier
                        .graphicsLayer { if (isDragging) shadowElevation = 8.dp.toPx() }
                        .semantics {
                            customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction(moveUp) { move(index, index - 1); true })
                                if (index < order.lastIndex) {
                                    add(CustomAccessibilityAction(moveDown) { move(index, index + 1); true })
                                }
                            }
                        },
                    titleAction = {
                        IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                            Icon(Icons.Default.Menu, contentDescription = dragHandle)
                        }
                    },
                ) {}
            }
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(ListPadding.plusBars(top = true)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(3) { InfoCard(title = null) { SkeletonLines(lines = 3) } }
    }
}

@Composable
private fun EmptyHome(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ListPadding.plusBars(top = true))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.home_open_settings)) }
    }
}
