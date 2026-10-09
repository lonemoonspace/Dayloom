package io.github.lonemoonspace.dayloom.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Card colours shared by every card. The dark card is darker than `primaryContainer`, on which status colours miss 4.5:1.
 * 所有卡片共用的底色。深色卡片比 `primaryContainer` 更暗，因为后者上的状态色达不到 4.5:1。
 */
@Immutable
class AppSurfaces(
    val card: Color,
    /** A tile inside a card: halfway between card and surface. / 卡片里再分一层的小块：卡片色与 surface 各半。 */
    val tile: Color,
)

val LightAppSurfaces = AppSurfaces(card = Color(0xFFD8E8FF), tile = Color(0xFFE8F0FE))

val DarkAppSurfaces = AppSurfaces(card = Color(0xFF0B3A66), tile = Color(0xFF0E2740))

val LocalAppSurfaces = staticCompositionLocalOf { LightAppSurfaces }

val MaterialTheme.appSurfaces: AppSurfaces
    @Composable
    @ReadOnlyComposable
    get() = LocalAppSurfaces.current
