package io.github.lonemoonspace.dayloom.core.module

/**
 * The single definition of what a module id (and each `<moduleId>.<name>` segment) may look like, shared by the registry check
 * and [io.github.lonemoonspace.dayloom.core.refresh.SourceId] so the two can never drift apart.
 * 模块 id（以及 `<模块id>.<名称>` 的每一段）的唯一规则，注册表校验与 [io.github.lonemoonspace.dayloom.core.refresh.SourceId]
 * 共用，两边不会各改各的。
 */
object ModuleIds {
    /** Lowercase letter first, then lowercase letters, digits or `_`. / 小写字母开头，后面是小写字母、数字或 `_`。 */
    const val SEGMENT = "[a-z][a-z0-9_]*"

    val MODULE_ID = Regex(SEGMENT)

    /** `<moduleId>.<name>`. */
    val SCOPED_NAME = Regex("$SEGMENT\\.$SEGMENT")
}
