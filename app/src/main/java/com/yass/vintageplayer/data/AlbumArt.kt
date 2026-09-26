package com.yass.vintageplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object AlbumArt {
    private val missSentinel: ImageBitmap =
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()

    private val cache: LruCache<String, ImageBitmap> =
        object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
            override fun sizeOf(key: String, value: ImageBitmap): Int {
                return if (value === missSentinel) 1 else value.width * value.height * 4
            }
        }

    suspend fun load(ctx: Context, track: Track, sizePx: Int): ImageBitmap? {
        if (sizePx <= 0) return null
        val key = "${track.id}:$sizePx"
        cache.get(key)?.let { hit ->
            return if (hit === missSentinel) null else hit
        }
        return withContext(Dispatchers.IO) {
            val hit = cache.get(key)
            if (hit != null) {
                if (hit === missSentinel) null else hit
            } else {
                try {
                    val bitmap = ctx.contentResolver.loadThumbnail(
                        Uri.parse(track.uri),
                        Size(sizePx, sizePx),
                        null,
                    )
                    val image = bitmap.asImageBitmap()
                    cache.put(key, image)
                    image
                } catch (e: IOException) {
                    cache.put(key, missSentinel)
                    null
                } catch (e: SecurityException) {
                    cache.put(key, missSentinel)
                    null
                } catch (e: IllegalArgumentException) {
                    cache.put(key, missSentinel)
                    null
                }
            }
        }
    }
}
