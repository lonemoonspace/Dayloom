package io.github.lonemoonspace.dayloom.app

import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.core.module.ModuleContext
import io.github.lonemoonspace.dayloom.core.module.ModuleInstance
import io.github.lonemoonspace.dayloom.core.notify.ChannelSpec
import io.github.lonemoonspace.dayloom.core.notify.ScopedRule
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCoordinator
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.storage.AppSettings
import io.github.lonemoonspace.dayloom.core.storage.ValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * An enabled module and its instance. / 已开启的模块及其实例。
 */
class ActiveModule(val module: FeatureModule, val instance: ModuleInstance)

/**
 * Turns the registry plus the user's on/off choices into running modules. A module is created the first time it is enabled
 * (its sources are registered with the coordinator then) and kept afterwards; disabling only removes it from [active], so
 * nothing outside asks it for cards, tabs, refreshes or notifications.
 * 把注册表与用户的开关选择变成运行中的模块。模块第一次被开启时创建（此时把它的来源注册到协调器），之后一直保留；
 * 关闭只是把它从 [active] 里移除，外部就不会再向它要卡片、标签页、刷新或通知。
 */
class ModuleHost(
    val modules: List<FeatureModule>,
    settings: ValueStore<AppSettings>,
    private val contextFor: (FeatureModule) -> ModuleContext,
    private val coordinator: RefreshCoordinator,
    scope: CoroutineScope,
) {
    private val infos = modules.map { ModulePolicy.ModuleInfo(it.id, it.defaultEnabled, it.notificationIds) }

    init {
        val problems = ModulePolicy.problems(infos)
        require(problems.isEmpty()) { "invalid module registry: $problems" }
    }

    private val lock = Any()
    private val instances = mutableMapOf<String, ModuleInstance>()

    /** Enabled modules in registry order; null until the settings have been read once. / 按注册表顺序的已开启模块；设置读出来之前为 null。 */
    val active: StateFlow<List<ActiveModule>?> = settings.flow
        .map { ModulePolicy.enabledIds(infos, it.moduleEnabled) }
        .distinctUntilChanged()
        .map { ids -> modules.filter { it.id in ids }.map { ActiveModule(it, instanceOf(it)) } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun instanceOf(module: FeatureModule): ModuleInstance = synchronized(lock) {
        instances.getOrPut(module.id) {
            module.create(contextFor(module)).also { coordinator.register(it.sources) }
        }
    }

    companion object {
        fun sourceIds(active: List<ActiveModule>): Set<SourceId> =
            active.flatMapTo(mutableSetOf()) { a -> a.instance.sources.map { it.id } }

        /** Rules with their namespaced state keys `<moduleId>.<rule>`. / 规则及其带命名空间的状态键 `<模块id>.<规则>`。 */
        fun rules(active: List<ActiveModule>): List<ScopedRule> =
            active.flatMap { a -> a.instance.notificationRules.map { ScopedRule("${a.module.id}.${it.name}", it) } }

        /** Channels keyed by their full id `<moduleId>.<name>`. / 以完整 id `<模块id>.<名称>` 为键的渠道。 */
        fun channels(active: List<ActiveModule>): Map<String, ChannelSpec> =
            active.flatMap { a -> a.instance.notificationChannels.map { "${a.module.id}.${it.name}" to it } }.toMap()
    }
}
