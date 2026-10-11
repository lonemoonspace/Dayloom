package io.github.lonemoonspace.dayloom.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Card colours shared by every card, taken from the wallpaper scheme by [DayloomTheme].
 * 所有卡片共用的底色，由 [DayloomTheme] 从壁纸配色中取。
 */
@Immutable
class AppSurfaces(
    val card: Color,
    /** A tile inside a card: halfway between card and surface. / 卡片里再分一层的小块：卡片色与 surface 各半。 */
    val tile: Color,
)

/**
 * The neutral tones [DayloomTheme] uses (M3 baseline values; a wallpaper only changes their faint hue): light cards are
 * tone 100 on tone 96 tiles, dark cards tone 12 with tone 17 tiles. Used as the preview default and by the contrast test.
 * [DayloomTheme] 用到的中性色明度（M3 基线值；壁纸只改变其中很淡的色相）：浅色卡片为明度 100、小块 96，深色卡片 12、小块 17。
 * 作为预览默认值，也供对比度测试使用。
 */
val LightAppSurfaces = AppSurfaces(card = Color(0xFFFFFFFF), tile = Color(0xFFF7F2FA))

val DarkAppSurfaces = AppSurfaces(card = Color(0xFF211F26), tile = Color(0xFF2B2930))

val LocalAppSurfaces = staticCompositionLocalOf { LightAppSurfaces }

val MaterialTheme.appSurfaces: AppSurfaces
    @Composable
    @ReadOnlyComposable
    get() = LocalAppSurfaces.current
