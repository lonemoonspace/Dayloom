package io.github.lonemoonspace.dayloom.app

import io.github.lonemoonspace.dayloom.core.module.FeatureModule

/**
 * Release builds add no extra modules; the debug source set adds the demo module here.
 * Release 版不额外加模块；Debug 源集在这里加入演示模块。
 */
internal val variantModules: List<FeatureModule> = emptyList()
