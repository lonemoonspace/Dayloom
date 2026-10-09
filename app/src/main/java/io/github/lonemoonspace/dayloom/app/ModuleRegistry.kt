package io.github.lonemoonspace.dayloom.app

import io.github.lonemoonspace.dayloom.core.module.FeatureModule

/**
 * The only place that lists the app's modules; the order here is the default order of cards, tabs and settings sections.
 * Adding a module means adding one line here and nothing else outside the module's own package.
 * 唯一列出所有模块的地方；这里的顺序就是卡片、标签页与设置分区的默认顺序。
 * 加一个模块只需要在这里加一行，模块自己的包之外不用改任何东西。
 */
object ModuleRegistry {
    val modules: List<FeatureModule> = listOf<FeatureModule>(
        // Real modules arrive from M1 on. / 正式模块从 M1 起加入。
    ) + variantModules
}
