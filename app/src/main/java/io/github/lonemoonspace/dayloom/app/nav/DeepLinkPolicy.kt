package io.github.lonemoonspace.dayloom.app.nav

/**
 * Where a `dayloom://` link leads. Links are resolved by hand instead of Navigation's deep links, so `dayloom://<moduleId>` can fall back
 * to home when the module has no tab or is disabled.
 * `dayloom://` 链接去往哪里。手动解析而不用 Navigation 的深链，这样 `dayloom://<模块id>` 在模块没有标签页或已关闭时可以回退到首页。
 */
object DeepLinkPolicy {

    sealed interface Destination {
        data object Home : Destination
        data object Settings : Destination
        data class ModuleTab(val moduleId: String) : Destination
    }

    const val SCHEME = "dayloom"

    /** Null when the link is not ours; the caller then leaves navigation alone. / 不是本 App 的链接时返回 null，调用方不做导航。 */
    fun resolve(scheme: String?, host: String?, tabModuleIds: Set<String>): Destination? {
        if (scheme != SCHEME) return null
        return when (host) {
            "settings" -> Destination.Settings
            in tabModuleIds -> Destination.ModuleTab(host!!)
            else -> Destination.Home
        }
    }
}
