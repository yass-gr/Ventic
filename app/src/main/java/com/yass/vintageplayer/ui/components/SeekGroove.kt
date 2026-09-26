package com.yass.vintageplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.yass.vintageplayer.ui.theme.LocalVintage

/**
 * Seek groove, port of the design's seek bar.
 *
 * 10 dp groove with accent fill + glow and a 22 dp knob thumb.
 * Tap or drag seeks; while dragging, the drag value is shown instead of
 * [progress].
 */
@Composable
fun SeekGroove(progress: () -> Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalVintage.current
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val latestOnSeek by rememberUpdatedState(onSeek)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    dragValue = (down.position.x / size.width).coerceIn(0f, 1f)
                    var done = false
                    while (!done) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change == null || !change.pressed) {
                            done = true
                        } else {
                            dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                            change.consume()
                        }
                    }
                    latestOnSeek(dragValue)
                    dragging = false
                }
            },
    ) {
        val grooveH = 10.dp.toPx()
        val grooveTop = (size.height - grooveH) / 2f
        val pct = (if (dragging) dragValue else progress()).coerceIn(0f, 1f)
        drawRoundRect(
            color = tokens.groove,
            topLeft = Offset(0f, grooveTop),
            size = androidx.compose.ui.geometry.Size(size.width, grooveH),
            cornerRadius = CornerRadius(grooveH / 2f),
        )
        if (pct > 0f) {
            val fillW = (size.width * pct).coerceAtLeast(grooveH)
            drawCircle(tokens.accent.copy(alpha = 0.35f), grooveH, Offset(fillW - grooveH / 2f, size.height / 2f))
            drawRoundRect(
                color = tokens.accent,
                topLeft = Offset(0f, grooveTop),
                size = androidx.compose.ui.geometry.Size(fillW, grooveH),
                cornerRadius = CornerRadius(grooveH / 2f),
            )
        }
        val thumbR = 11.dp.toPx()
        val thumbX = (size.width * pct).coerceIn(thumbR, size.width - thumbR)
        drawCircle(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f), thumbR, Offset(thumbX, size.height / 2f + 1f))
        drawCircle(tokens.knobBrush, thumbR, Offset(thumbX, size.height / 2f))
        drawCircle(tokens.btnBorder, thumbR, Offset(thumbX, size.height / 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
    }
}
