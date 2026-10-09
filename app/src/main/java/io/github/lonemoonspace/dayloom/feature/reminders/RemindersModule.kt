package io.github.lonemoonspace.dayloom.feature.reminders

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.module.CardPlacement
import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.core.module.HomeCard
import io.github.lonemoonspace.dayloom.core.module.ModuleContext
import io.github.lonemoonspace.dayloom.core.module.ModuleInstance
import io.github.lonemoonspace.dayloom.core.module.SettingsSection
import io.github.lonemoonspace.dayloom.core.notify.ChannelSpec
import io.github.lonemoonspace.dayloom.core.time.minuteTicks
import io.github.lonemoonspace.dayloom.feature.reminders.domain.ReminderItem
import io.github.lonemoonspace.dayloom.feature.reminders.domain.ReminderPolicy
import io.github.lonemoonspace.dayloom.feature.reminders.domain.ReminderRule
import io.github.lonemoonspace.dayloom.feature.reminders.ui.RemindersCard
import io.github.lonemoonspace.dayloom.feature.reminders.ui.RemindersSettingsSection
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Things that expire (passes, permits, documents): a card that moves to the top when one is about to expire, and opt-in
 * reminders. Purely local; no data source.
 * 会到期的东西（月票、停车证、证件）：快到期时置顶的卡片，以及可选的到期提醒。纯本地数据，没有数据来源。
 */
object RemindersModule : FeatureModule {
    override val id = "reminders"
    override val title = R.string.reminders_title
    override val summary = R.string.reminders_summary
    override val icon = R.drawable.ic_reminders
    override val defaultEnabled = true
    override val notificationIds = 4_000..4_999

    override fun create(ctx: ModuleContext): ModuleInstance = RemindersInstance(ctx)
}

@Serializable
data class RemindersSettings(
    /** Reserved for migrations after v1.0.0. / 预留给 v1.0.0 之后的迁移。 */
    val version: Int = 1,
    val items: List<ReminderItem> = emptyList(),
    /** Opt-in, off by default like every notification (design §7.4). / 选择加入，与所有通知一样默认关闭（设计文档 §7.4）。 */
    val notify: Boolean = false,
)

private class RemindersInstance(private val ctx: ModuleContext) : ModuleInstance {
    private val store = ctx.settings(RemindersSettings.serializer(), RemindersSettings())

    private val channel = ChannelSpec("expiry", R.string.reminders_channel, R.string.reminders_channel_description)

    override val homeCards = listOf(
        HomeCard(
            key = "items",
            title = R.string.reminders_title,
            defaultOrder = 300,
            // Re-checked every minute: an item can start expiring while the app is open. / 每分钟重新判断：App 开着时条目也可能进入快到期。
            placement = combine(store.flow, ctx.clock.minuteTicks()) { s, now ->
                if (ReminderPolicy.needsAttention(s.items, now.toLocalDateTime())) CardPlacement.TOP else CardPlacement.NORMAL
            }.distinctUntilChanged(),
        ) { RemindersCard(store.flow) },
    )

    override val settings = SettingsSection {
        RemindersSettingsSection(store.flow) { transform -> ctx.appScope.launch { store.update(transform) } }
    }

    override val notificationChannels = listOf(channel)

    override val notificationRules = listOf(
        ReminderRule(
            enabled = { store.get().notify },
            items = { store.get().items },
            channelId = ctx.channelId(channel.name),
            deepLink = ctx.deepLink,
            // Offsets keep each item's notification separate; ids past the range wrap rather than fail. / 偏移让每个条目的通知互不覆盖；超出号段时回绕而不是报错。
            notificationId = { item -> ctx.notificationId(ReminderPolicy.idNumber(item.id) % NOTIFICATION_SLOTS) },
        ),
    )

    private companion object {
        const val NOTIFICATION_SLOTS = 1_000
    }
}
