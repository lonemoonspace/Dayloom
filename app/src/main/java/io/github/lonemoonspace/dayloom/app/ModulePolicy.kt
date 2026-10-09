package io.github.lonemoonspace.dayloom.app

/**
 * Pure rules about the module list: validity of the registry and which modules are on.
 * 关于模块列表的纯规则：注册表是否合法、哪些模块处于开启状态。
 */
object ModulePolicy {

    /** What the policy needs to know about a module. / Policy 需要知道的模块信息。 */
    data class ModuleInfo(val id: String, val defaultEnabled: Boolean, val notificationIds: IntRange)

    private val ID_PATTERN = Regex("[a-z][a-z0-9_]*")

    // `app` and `core` would collide with storage keys and deep links the shell itself uses.
    // `app` 与 `core` 会和外壳自己使用的存储键、深链冲突。
    private val RESERVED_IDS = setOf("app", "core", "home", "settings", "module")

    /**
     * Problems that make the registry invalid; empty when it is fine. Checked at startup and by a unit test.
     * 让注册表不合法的问题列表；没问题时为空。启动时与单元测试中都会检查。
     */
    fun problems(modules: List<ModuleInfo>): List<String> {
        val problems = mutableListOf<String>()
        modules.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { problems += "duplicate module id: $it" }
        modules.forEach { m ->
            if (!ID_PATTERN.matches(m.id)) problems += "invalid module id: ${m.id}"
            if (m.id in RESERVED_IDS) problems += "reserved module id: ${m.id}"
        }
        val ranged = modules.filterNot { it.notificationIds.isEmpty() }
        for (i in ranged.indices) {
            for (j in i + 1 until ranged.size) {
                val a = ranged[i]
                val b = ranged[j]
                if (a.notificationIds.first <= b.notificationIds.last && b.notificationIds.first <= a.notificationIds.last) {
                    problems += "notification ids overlap: ${a.id} ${a.notificationIds} and ${b.id} ${b.notificationIds}"
                }
            }
        }
        return problems
    }

    /**
     * Ids of the enabled modules, in registry order. A module without an explicit choice uses its own default.
     * 已开启模块的 id，按注册表顺序。没有显式选择的模块用它自己的默认值。
     */
    fun enabledIds(modules: List<ModuleInfo>, overrides: Map<String, Boolean>): List<String> =
        modules.filter { overrides[it.id] ?: it.defaultEnabled }.map { it.id }
}
