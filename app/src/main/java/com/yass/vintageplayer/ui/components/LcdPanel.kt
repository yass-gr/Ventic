package com.yass.vintageplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.yass.vintageplayer.ui.theme.LocalVintage

/**
 * LCD panel: bezel + LCD background + scanlines
 * (`repeating 1px/3px`) + top gloss overlay. Port of the LCD `div` in the
 * design's player screen.
 */
@Composable
fun LcdPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tokens = LocalVintage.current
    val bezel = remember(tokens) { tokens.bezelBrush }
    val lcd = remember(tokens) { tokens.lcdBrush }
    val gloss = remember {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.09f),
            0.45f to Color.White.copy(alpha = 0.09f),
            1f to Color.Transparent,
        )
    }
    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(16.dp), clip = false)
            .background(bezel, RoundedCornerShape(16.dp))
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(lcd, RoundedCornerShape(10.dp))
                .drawBehind {
                    drawScanlines()
                    drawRect(gloss)
                }
                .padding(16.dp, 14.dp, 16.dp, 12.dp),
        ) {
            content()
        }
    }
}

/** Horizontal 1 px lines every 3 px, `rgba(0,0,0,0.12)`. */
fun DrawScope.drawScanlines() {
    val lineColor = Color.Black.copy(alpha = 0.12f)
    var y = 0f
    while (y < size.height) {
        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += 3f * density
    }
}
