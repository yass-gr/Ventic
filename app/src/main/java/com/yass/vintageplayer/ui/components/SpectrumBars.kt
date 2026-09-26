package com.yass.vintageplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yass.vintageplayer.playback.AudioLevels
import com.yass.vintageplayer.ui.theme.LocalVintage
import com.yass.vintageplayer.ui.theme.SPECTRUM_FLOOR

/**
 * 28-band spectrum, port of the design's `bars`.
 *
 * 7 dp wide bars with a 3 dp gap, 48 dp tall, segmented (3 px lit / 2 px
 * gap) with a dim ghost underneath. Pure [Canvas]: [levels] and [playing]
 * are read inside [androidx.compose.ui.graphics.drawscope.DrawScope] so no
 * recomposition happens per frame. When paused, draws the 0.06 floor.
 */
@Composable
fun SpectrumBars(
    levels: () -> AudioLevels,
    playing: () -> Boolean,
    modifier: Modifier = Modifier,
    barWidth: Dp = 7.dp,
    barGap: Dp = 3.dp,
    height: Dp = 48.dp,
) {
    val tokens = LocalVintage.current
    val width = barWidth * AudioLevels.BAND_COUNT + barGap * (AudioLevels.BAND_COUNT - 1)
    Canvas(modifier = modifier.width(width).height(height)) {
        val bw = barWidth.toPx()
        val step = bw + barGap.toPx()
        val h = size.height
        val litColor = tokens.lcdText
        val ghostColor = tokens.lcdDim
        val isPlaying = playing()
        val bands = levels().bands
        var i = 0
        while (i < AudioLevels.BAND_COUNT) {
            val v = if (isPlaying) bands[i].coerceIn(0f, 1f) else SPECTRUM_FLOOR
            val litH = (h * v).coerceAtLeast(3f)
            val x = i * step
            var y = h
            while (y > 0f) {
                val segTop = (y - 3f).coerceAtLeast(0f)
                val segH = y - segTop
                val lit = (h - segTop) <= litH
                drawRect(
                    if (lit) litColor else ghostColor,
                    Offset(x, segTop),
                    Size(bw, segH),
                )
                y -= 5f
            }
            i++
        }
    }
}
