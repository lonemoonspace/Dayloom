package io.github.lonemoonspace.dayloom.app

import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.feature.demo.DemoModule

/**
 * Debug builds add the demo module, which exercises the whole module system (source, card, tab, settings) without network.
 * Debug 版额外加入演示模块，它不联网，但走通整个模块系统（来源、卡片、标签页、设置）。
 */
internal val variantModules: List<FeatureModule> = listOf(DemoModule)
