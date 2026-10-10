package io.github.lonemoonspace.dayloom.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lonemoonspace.dayloom.app.ActiveModule
import io.github.lonemoonspace.dayloom.app.ModuleHost
import io.github.lonemoonspace.dayloom.core.module.CardPlacement
import io.github.lonemoonspace.dayloom.core.module.HomeCard
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCoordinator
import io.github.lonemoonspace.dayloom.core.refresh.Trigger
import io.github.lonemoonspace.dayloom.core.routine.RoutinePolicy
import io.github.lonemoonspace.dayloom.core.storage.AppSettings
import io.github.lonemoonspace.dayloom.core.storage.ValueStore
import io.github.lonemoonspace.dayloom.core.time.AppClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A card with its stored key `<moduleId>.<key>`. / 卡片及其存储键 `<模块id>.<键>`。 */
class HomeCardEntry(val key: String, val card: HomeCard)

data class HomeState(
    /** False until the enabled modules are known. / 已开启模块确定之前为 false。 */
    val loaded: Boolean = false,
    /** In display order (pinned cards first). / 按显示顺序（置顶的在前）。 */
    val cards: List<HomeCardEntry> = emptyList(),
    /** In the user's order, for edit mode. / 按用户顺序，供编辑模式使用。 */
    val userOrder: List<HomeCardEntry> = emptyList(),
    val refreshing: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val active: StateFlow<List<ActiveModule>?>,
    private val settings: ValueStore<AppSettings>,
    private val coordinator: RefreshCoordinator,
    private val clock: AppClock,
) : ViewModel() {

    private var polling: Job? = null

    private val placedCards: Flow<List<Pair<HomeCardEntry, CardPlacement>>?> = active.flatMapLatest { modules ->
        when {
            modules == null -> flowOf(null)
            else -> {
                val entries = modules.flatMap { a -> a.instance.homeCards.map { HomeCardEntry("${a.module.id}.${it.key}", it) } }
                if (entries.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(entries.map { e -> e.card.placement.map { e to it } }) { it.toList() }
                }
            }
        }
    }

    private val refreshing: Flow<Boolean> = combine(active, coordinator.status) { modules, status ->
        val ids = modules?.let(ModuleHost::sourceIds).orEmpty()
        ids.any { status[it]?.refreshing == true }
    }

    val state: StateFlow<HomeState> = combine(placedCards, settings.flow, refreshing) { placed, s, busy ->
        if (placed == null) return@combine HomeState(refreshing = busy)
        val byKey = placed.associate { (entry, _) -> entry.key to entry }
        val cards = placed.map { (entry, placement) ->
            CardOrderPolicy.Card(entry.key, entry.card.defaultOrder, pinnedToTop = placement == CardPlacement.TOP)
        }
        HomeState(
            loaded = true,
            cards = CardOrderPolicy.displayOrder(cards, s.cardOrder).map(byKey::getValue),
            userOrder = CardOrderPolicy.userOrder(cards, s.cardOrder).map(byKey::getValue),
            refreshing = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun refresh(trigger: Trigger = Trigger.USER) {
        viewModelScope.launch {
            val ids = active.value?.let(ModuleHost::sourceIds).orEmpty()
            try {
                coordinator.refresh(ids, trigger)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Per-source errors are in coordinator.status; this only guards an unexpected failure. / 各来源的错误在 coordinator.status 里；这里只兜意外失败。
            }
        }
    }

    /**
     * While the home screen is visible, refreshes whatever is due by each source's cadence: at once (visibly, so the user
     * sees it happen on return), then silently every minute. In a commute window the departures stay current without the
     * user pulling; outside, most minutes nothing is due and no request goes out.
     * 首页可见期间，按各来源的节奏刷新到期的来源：先立即刷新一次（可见，用户回到 App 时看得到），之后每分钟静默检查一次。
     * 通勤时间窗内发车信息不用手动刷新也保持最新；时间窗外大多数分钟里没有来源到期，不会发请求。
     */
    fun startPolling() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            var trigger = Trigger.AUTO
            while (true) {
                refreshDue(trigger)
                trigger = Trigger.LIVE_POLL
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        polling?.cancel()
        polling = null
    }

    private suspend fun refreshDue(trigger: Trigger) {
        // On a cold start the enabled modules may not be known yet; wait rather than refresh nothing.
        // 冷启动时可能还不知道开启了哪些模块；等一等，而不是什么都不刷。
        val ids = ModuleHost.sourceIds(active.filterNotNull().first())
        try {
            coordinator.refreshDue(ids, trigger, RoutinePolicy.isDaytime(clock.now()), throttleFailures = true)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // As in refresh(): per-source errors are in coordinator.status. / 与 refresh() 相同：各来源的错误在 coordinator.status 里。
        }
    }

    fun saveOrder(keys: List<String>) {
        viewModelScope.launch { settings.update { it.copy(cardOrder = keys) } }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 60_000L
    }
}
