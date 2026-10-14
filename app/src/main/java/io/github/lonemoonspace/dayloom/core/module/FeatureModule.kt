package io.github.lonemoonspace.dayloom.core.module

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.notify.BriefContributor
import io.github.lonemoonspace.dayloom.core.notify.ChannelSpec
import io.github.lonemoonspace.dayloom.core.notify.NotificationRule
import io.github.lonemoonspace.dayloom.core.refresh.CachedSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Static description of a feature module. Nothing is created while the module is disabled.
 * Adding a module = implement this + one line in `app/ModuleRegistry.kt`; nothing else in the app changes.
 * 功能模块的静态描述。模块关闭时不会创建任何东西。
 * 加一个模块 = 实现本接口 + 在 `app/ModuleRegistry.kt` 加一行；App 的其他部分都不用改。
 */
interface FeatureModule {
    /** Lowercase id, e.g. `weather`; prefixes everything the module stores. Frozen from v1.0.0. / 小写 id，如 `weather`；模块存储的一切都以它为前缀。从 v1.0.0 起冻结。 */
    val id: String

    @get:StringRes val title: Int

    /** One sentence shown next to the on/off switch. / 开关旁显示的一句说明。 */
    @get:StringRes val summary: Int

    @get:DrawableRes val icon: Int

    val defaultEnabled: Boolean

    /**
     * Notification ids this module may use; ranges of different modules must not overlap (checked by a registry test).
     * Frozen from v1.0.0.
     * 本模块可用的通知 id 号段；不同模块的号段不能重叠（由注册表测试检查）。从 v1.0.0 起冻结。
     */
    val notificationIds: IntRange get() = IntRange.EMPTY

    /**
     * Id of the module this one was split out of, if any. Until the user flips this module's own switch it follows that
     * module's saved on/off choice; its settings and home-card positions can start from that module's too
     * ([ModuleContext.settings], [HomeCard.formerKey]), so a split changes nothing on screen.
     * 本模块是从哪个模块拆出来的（如有）。在用户拨动本模块自己的开关之前，沿用那个模块保存的开关状态；
     * 设置与首页卡片位置也可以从那个模块接过来（[ModuleContext.settings]、[HomeCard.formerKey]），拆分后界面上什么都不变。
     */
    val splitFrom: String? get() = null

    fun create(ctx: ModuleContext): ModuleInstance
}

/**
 * An enabled module: declares everything it contributes. Every item is optional.
 * 已启用的模块：声明它贡献的一切。每一项都是可选的。
 */
interface ModuleInstance {
    /** Registered with the RefreshCoordinator. / 注册到 RefreshCoordinator。 */
    val sources: List<CachedSource<*, *>> get() = emptyList()

    val homeCards: List<HomeCard> get() = emptyList()

    /** Its own bottom-navigation tab (football, news). / 自己的底部导航标签页（足球、新闻）。 */
    val tab: ModuleTab? get() = null

    val settings: SettingsSection? get() = null

    /**
     * Several settings cards when one is not enough (train and bus apart); defaults to [settings] alone.
     * 一张设置卡片不够时可以给多张（火车与公交分开）；默认只有 [settings]。
     */
    val settingsSections: List<SettingsSection> get() = listOfNotNull(settings)

    val notificationChannels: List<ChannelSpec> get() = emptyList()

    val notificationRules: List<NotificationRule<*>> get() = emptyList()

    val brief: BriefContributor? get() = null

    /** Whether the module is set up; when not, its cards show a setup prompt. / 模块是否已设置好；没有时卡片显示设置引导。 */
    val configured: Flow<ConfigState> get() = flowOf(ConfigState.Ready)
}

sealed interface ConfigState {
    data object Ready : ConfigState

    /** [what] names the missing setting. / [what] 是缺少的设置项。 */
    data class NeedsSetup(val what: UiText) : ConfigState
}

/**
 * Where a card wants to be. [TOP] overrides the user's order while it lasts (an item about to expire).
 * 卡片希望出现的位置。[TOP] 在持续期间优先于用户排好的顺序（快到期的条目）。
 */
enum class CardPlacement { NORMAL, TOP }

/**
 * A home-screen card. [key] is local; the stored key is `<moduleId>.<key>`. [defaultOrder] places it before the user reorders.
 * [formerKey] is the card's local key under [FeatureModule.splitFrom], so it keeps the place the user gave it there.
 * 首页卡片。[key] 是本地键，存储时为 `<模块id>.<键>`。[defaultOrder] 决定用户排序之前的位置。
 * [formerKey] 是这张卡片在 [FeatureModule.splitFrom] 那个模块里的本地键，卡片因此留在用户原来给它排的位置。
 */
class HomeCard(
    val key: String,
    @param:StringRes val title: Int,
    val defaultOrder: Int,
    val placement: Flow<CardPlacement> = flowOf(CardPlacement.NORMAL),
    val formerKey: String? = null,
    val content: @Composable () -> Unit,
)

/**
 * A bottom-navigation tab; its route and deep link are `dayloom://<moduleId>`. [iconContent], when given, draws the tab's
 * icon instead of [icon] (the football tab shows the followed team's crest) and receives the tint the bar would use.
 * 底部导航标签页；路由与深链为 `dayloom://<模块id>`。给出 [iconContent] 时由它代替 [icon] 绘制图标（足球标签页显示关注球队的队徽），
 * 参数是导航栏本来要用的着色。
 */
class ModuleTab(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val iconContent: (@Composable (tint: Color) -> Unit)? = null,
    val content: @Composable () -> Unit,
)

/**
 * One card of the module's settings, titled [title] or else the module's title.
 * 模块设置里的一张卡片，标题为 [title]，没有时用模块标题。
 */
class SettingsSection(@param:StringRes val title: Int? = null, val content: @Composable () -> Unit)
