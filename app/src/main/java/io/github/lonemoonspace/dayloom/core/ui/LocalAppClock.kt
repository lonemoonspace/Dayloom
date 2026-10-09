package io.github.lonemoonspace.dayloom.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.lonemoonspace.dayloom.core.time.AppClock

/**
 * How composables read "now"; provided by the app root from `AppGraph.clock`.
 * Composable 读取「现在」的方式；由 App 根节点用 `AppGraph.clock` 提供。
 */
val LocalAppClock = staticCompositionLocalOf<AppClock> { error("LocalAppClock not provided") }
