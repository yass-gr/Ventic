package com.yass.vintageplayer.data

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Temporary stub owned by Agent C. The lead drops this file at merge and
 * replaces it with Agent A's thumbnail loader + LruCache.
 */
object AlbumArt {
    suspend fun load(context: Context, track: Track, sizePx: Int): ImageBitmap? = null
}
