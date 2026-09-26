package com.yass.vintageplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yass.vintageplayer.ui.theme.LocalVintage

/**
 * Status LED, port of the design's `led(on)`.
 * On: accent with glow (`0 0 7px accent + 0 0 2px accent`). Off: dark inset dot.
 */
@Composable
fun Led(on: Boolean, modifier: Modifier = Modifier, size: Dp = 6.dp) {
    val tokens = LocalVintage.current
    Canvas(modifier = modifier.size(size)) {
        val r = size.toPx() / 2f
        val c = center
        if (on) {
            drawCircle(Color.Black.copy(alpha = 0.35f), r * 1.9f, c)
            drawCircle(tokens.accent.copy(alpha = 0.35f), r * 1.7f, c)
            drawCircle(tokens.accent, r, c)
            drawCircle(
                Color.White.copy(alpha = 0.5f),
                r * 0.35f,
                c + Offset(-r * 0.25f, -r * 0.25f),
            )
        } else {
            drawCircle(Color.Black.copy(alpha = 0.5f), r, c + Offset(0f, r * 0.3f))
            drawCircle(tokens.ledOff, r, c)
        }
    }
}
