package io.github.lonemoonspace.dayloom.feature.todo

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.module.CardPlacement
import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.core.module.HomeCard
import io.github.lonemoonspace.dayloom.core.module.ModuleContext
import io.github.lonemoonspace.dayloom.core.module.ModuleInstance
import io.github.lonemoonspace.dayloom.core.module.SettingsSection
import io.github.lonemoonspace.dayloom.core.time.minuteTicks
import io.github.lonemoonspace.dayloom.feature.todo.domain.TodoItem
import io.github.lonemoonspace.dayloom.feature.todo.domain.TodoPolicy
import io.github.lonemoonspace.dayloom.feature.todo.ui.TodoCard
import io.github.lonemoonspace.dayloom.feature.todo.ui.TodoSettingsSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * A short to-do list: added, ticked off and undone right on the home card, with an optional due day. Purely local; no data
 * source and no notifications: an overdue or due-today item moves the card to the top instead.
 * 简单的待办清单：在首页卡片上直接添加、勾选和撤销，截止日期可选。纯本地数据，没有数据来源也不发通知：有逾期或今天到期的条目时，
 * 卡片会置顶，以此代替提醒。
 */
object TodoModule : FeatureModule {
    override val id = "todo"
    override val title = R.string.todo_title
    override val summary = R.string.todo_summary
    override val icon = R.drawable.ic_todo
    override val defaultEnabled = true

    override fun create(ctx: ModuleContext): ModuleInstance = TodoInstance(ctx)
}

@Serializable
data class TodoSettings(
    /** Reserved for migrations after v1.0.0. / 预留给 v1.0.0 之后的迁移。 */
    val version: Int = 1,
    val items: List<TodoItem> = emptyList(),
)

private class TodoInstance(private val ctx: ModuleContext) : ModuleInstance {
    private val store = ctx.settings(TodoSettings.serializer(), TodoSettings())

    /** Applies [transform] to the item list in the app scope, so leaving the screen never loses an edit. / 在应用级作用域里修改条目列表，离开页面也不会丢掉改动。 */
    private val update: ((List<TodoItem>) -> List<TodoItem>) -> Unit = { transform ->
        ctx.appScope.launch {
            val now = ctx.clock.instant()
            store.update { s -> s.copy(items = TodoPolicy.purge(transform(s.items), now)) }
        }
    }

    init {
        // Old done items are dropped at start too, not only on the next edit. / 启动时也清理过期的已完成条目，而不只在下次修改时。
        update { it }
    }

    override val settingsStatus: Flow<UiText?> = store.flow.map { s ->
        TodoPolicy.openCount(s.items).takeIf { it > 0 }?.let { UiText.Plural(R.plurals.todo_open_count, it) }
    }

    override val homeCards = listOf(
        HomeCard(
            key = "list",
            title = R.string.todo_title,
            defaultOrder = 290,
            // Re-checked every minute: an item becomes due at midnight while the app is open. / 每分钟重新判断：App 开着时，零点一过条目就到期了。
            placement = combine(store.flow, ctx.clock.minuteTicks()) { s, now ->
                if (TodoPolicy.needsAttention(s.items, now.toLocalDate())) CardPlacement.TOP else CardPlacement.NORMAL
            }.distinctUntilChanged(),
        ) { TodoCard(store.flow, update) },
    )

    override val settings = SettingsSection { TodoSettingsSection(store.flow, update) }
}
