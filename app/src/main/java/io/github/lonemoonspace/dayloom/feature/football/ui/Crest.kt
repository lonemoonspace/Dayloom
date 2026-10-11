package io.github.lonemoonspace.dayloom.feature.football.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import io.github.lonemoonspace.dayloom.feature.football.domain.TeamRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Loads team crests from the URLs football-data.org returns, kept in memory for the session. Android cannot draw SVG, so
 * an `.svg` crest is fetched as the `.png` the same host serves next to it; anything that fails shows the team's code.
 * 从 football-data.org 返回的地址加载队徽，在本次运行期间缓存在内存里。Android 画不了 SVG，所以 `.svg` 队徽改取同一主机上并列的
 * `.png`；任何失败都显示球队代码。
 */
class CrestLoader(private val http: OkHttpClient) {
    private val cache = LruCache<String, ImageBitmap>(CACHE_SIZE)

    suspend fun load(url: String): ImageBitmap? {
        val target = pngOf(url) ?: return null
        cache.get(target)?.let { return it }
        return withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(target).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                BitmapFactory.decodeStream(response.body.byteStream())?.asImageBitmap()
            }
        }?.also { cache.put(target, it) }
    }

    companion object {
        private const val CACHE_SIZE = 64

        /** Only http(s) crests; SVG swapped for PNG. / 只接受 http(s) 地址；SVG 换成 PNG。 */
        internal fun pngOf(url: String): String? {
            val parsed = url.toHttpUrlOrNull() ?: return null
            val path = parsed.encodedPath
            return if (path.endsWith(".svg", ignoreCase = true)) parsed.newBuilder().encodedPath(path.dropLast(4) + ".png").build().toString() else parsed.toString()
        }
    }
}

/** A crest, or the team's three-letter code in a circle while it loads or when it cannot be shown. / 队徽；加载中或显示不了时，用圆圈里的三字母代码代替。 */
@Composable
internal fun Crest(team: TeamRef, loader: CrestLoader, size: Dp, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, team.crest) {
        value = try {
            loader.load(team.crest)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A missing crest is cosmetic; the code stands in. / 缺队徽只影响外观，用代码代替即可。
            null
        }
    }
    val bitmap = image
    if (bitmap != null) {
        // The team name is always next to it. / 旁边总有队名。
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier = modifier
                .size(size)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                team.tla.take(3),
                fontSize = (size.value * 0.3f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
            )
        }
    }
}
