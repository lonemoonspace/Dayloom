package io.github.lonemoonspace.dayloom.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Pull-to-refresh around a scrolling page. The indicator drops in below the glass top bar, which the page scrolls under.
 * Turned off with [enabled] while a drag gesture of the page itself needs the pull (reordering cards).
 * 包在可滚动页面外的下拉刷新。指示器出现在玻璃顶栏下方（页面会滚到顶栏下面）。页面自身的拖动手势需要下拉时（拖动排序卡片），
 * 用 [enabled] 关掉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefresh(refreshing: Boolean, onRefresh: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val state = rememberPullToRefreshState()
    Box(Modifier.fillMaxSize().pullToRefresh(refreshing, state, enabled = enabled, onRefresh = onRefresh)) {
        content()
        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = refreshing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = LocalBarInsets.current.calculateTopPadding()),
        )
    }
}
