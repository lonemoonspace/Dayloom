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
    // Counted in bytes, not crests, so a few oversized images cannot fill the memory. / 按字节而不是按个数计，几张超大的图也撑不爆内存。
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    suspend fun load(url: String): ImageBitmap? {
        val target = pngOf(url) ?: return null
        cache.get(target)?.let { return it }
        return withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(target).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val source = response.body.source()
                // True means more than the limit arrived; false means the whole body is buffered. / 为 true 表示超过上限；为 false 表示正文已全部读入。
                if (source.request(MAX_DOWNLOAD_BYTES + 1)) return@withContext null
                decode(source.buffer.readByteArray())
            }
        }?.also { cache.put(target, it) }
    }

    companion object {
        /** Room for a few hundred crests at display size. / 够放几百个显示尺寸的队徽。 */
        private const val CACHE_BYTES = 8 * 1024 * 1024

        /** A crest is a few kilobytes; anything far larger is not one. / 队徽只有几 KB；大得多的就不是队徽。 */
        private const val MAX_DOWNLOAD_BYTES = 2L * 1024 * 1024

        /** The largest crest is 44 dp, about 176 px on the densest screens. / 最大的队徽 44 dp，在最高密度屏上约 176 像素。 */
        private const val MAX_SIDE_PX = 192

        /** Decoded at about the size it is shown, never at full resolution. / 按接近显示的尺寸解码，绝不按原图分辨率。 */
        private fun decode(bytes: ByteArray): ImageBitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE_PX) }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        }

        /** The largest power of two that keeps both sides at least [maxSide], as BitmapFactory expects. / 让两边都不小于 [maxSide] 的最大 2 的幂，符合 BitmapFactory 的要求。 */
        internal fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
            var sample = 1
            while (width / (sample * 2) >= maxSide && height / (sample * 2) >= maxSide) sample *= 2
            return sample
        }

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
