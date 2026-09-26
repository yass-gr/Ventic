package com.yass.vintageplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yass.vintageplayer.data.AlbumArt
import com.yass.vintageplayer.data.Track
import com.yass.vintageplayer.ui.theme.coverPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Album cover: generated art from the `coverFor(id)` palette
 * (135° linear gradient, stripe overlay, circle at 68 % / 34 %);
 * a real thumbnail from [AlbumArt] is drawn over it when available.
 */
@Composable
fun CoverArt(track: Track, size: Dp, corner: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val (c0, c1, c2) = remember(track.id) { coverPalette(track.id) }
    val sizePx = with(density) { size.toPx().toInt().coerceAtLeast(1) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, track.id, sizePx) {
        value = withContext(Dispatchers.IO) { AlbumArt.load(context, track, sizePx) }
    }
    var canvasPx by remember { mutableStateOf(IntSize.Zero) }
    val gradient = remember(c0, c1, canvasPx) {
        Brush.linearGradient(
            listOf(c0, c1),
            start = Offset.Zero,
            end = Offset(canvasPx.width.toFloat(), canvasPx.height.toFloat()),
        )
    }
    val gloss = remember {
        Brush.verticalGradient(0f to Color.White.copy(alpha = 0.3f), 0.25f to Color.Transparent)
    }
    val stripeColor = remember { Color.Black.copy(alpha = 0.08f) }
    val borderColor = remember { Color.Black.copy(alpha = 0.5f) }
    val shape = remember(corner) { RoundedCornerShape(corner) }
    Canvas(
        modifier = modifier
            .size(size)
            .shadow(4.dp, shape, clip = false)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .onSizeChanged { canvasPx = it },
    ) {
        val canvasSize = this.size
        drawRect(gradient)
        clipRect {
            var y = 0f
            while (y < canvasSize.height) {
                drawRect(stripeColor, Offset(0f, y), Size(canvasSize.width, 2f))
                y += 7f
            }
            drawCircle(c2, canvasSize.minDimension * 0.17f, Offset(canvasSize.width * 0.68f, canvasSize.height * 0.34f))
            drawCircle(
                Color.White.copy(alpha = 0.3f),
                canvasSize.minDimension * 0.17f,
                Offset(canvasSize.width * 0.68f, canvasSize.height * 0.34f),
                style = Stroke(1f),
            )
        }
        bitmap?.let { bmp ->
            drawImage(bmp, dstSize = IntSize(canvasSize.width.toInt(), canvasSize.height.toInt()))
        }
        drawRect(gloss)
    }
}
