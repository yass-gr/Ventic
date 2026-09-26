package com.yass.vintageplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yass.vintageplayer.ui.theme.LocalVintage
import com.yass.vintageplayer.ui.theme.ShareTechMono
import com.yass.vintageplayer.ui.theme.needleAngle
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive

private data class VuTick(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

/** The 8 tick segments from the design's SVG, in viewBox (200×126) units. */
private val VuTicks = listOf(
    VuTick(44.8f, 71.7f, 35.7f, 64f),
    VuTick(64f, 55.6f, 58f, 45.3f),
    VuTick(81.4f, 48.4f, 78.3f, 36.9f),
    VuTick(100f, 46f, 100f, 34f),
    VuTick(118.6f, 48.4f, 121.7f, 36.9f),
    VuTick(130.4f, 52.8f, 135.5f, 41.9f),
    VuTick(141.3f, 59f, 148.2f, 49.2f),
    VuTick(155.2f, 71.7f, 164.3f, 64f),
)

private data class VuLabel(val text: String, val x: Float, val y: Float, val red: Boolean = false)

private data class PlacedLabel(val layout: TextLayoutResult, val x: Float, val y: Float)

/** Tick labels from the SVG. */
private val VuLabels = listOf(
    VuLabel("20", 27f, 58f),
    VuLabel("10", 52f, 37f),
    VuLabel("5", 75f, 28f),
    VuLabel("0", 100f, 24f),
    VuLabel("+3", 140f, 33f, red = true),
)

/**
 * VU meter, exact port of the design's SVG (`viewBox 200×126`):
 * arc, red zone, 8 ticks, labels 20/10/5/0/+3/VU, needle, cap, top gloss.
 *
 * [level] and [volume] are sampled once per frame in a `withFrameNanos` loop
 * that runs only while [playing]; the needle eases toward
 * `-40 + 88*vol*level` (clamped to 48) and rests at `-50°` when idle.
 * No `animateFloatAsState` is used.
 */
@Composable
fun VuMeter(
    level: () -> Float,
    volume: () -> Float,
    playing: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalVintage.current
    val measurer = rememberTextMeasurer()
    var needle by remember { mutableFloatStateOf(-50f) }
    val latestLevel by rememberUpdatedState(level)
    val latestVolume by rememberUpdatedState(volume)
    val latestPlaying by rememberUpdatedState(playing)
    val isPlaying = playing()

    // Labels are static: measure once per theme instead of every frame.
    val labels = remember(measurer, tokens) {
        val small = { red: Boolean ->
            androidx.compose.ui.text.TextStyle(
                fontFamily = ShareTechMono,
                fontSize = 11.sp,
                color = if (red) tokens.vuRed else tokens.vuInk,
            )
        }
        VuLabels.map { l -> PlacedLabel(measurer.measure(l.text, style = small(l.red)), l.x, l.y) } +
            PlacedLabel(
                measurer.measure(
                    "VU",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = ShareTechMono,
                        fontSize = 13.sp,
                        letterSpacing = 2.sp,
                        color = tokens.vuInk,
                    ),
                ),
                100f,
                92f,
            )
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            needle = -50f
            return@LaunchedEffect
        }
        var last = 0L
        while (isActive && latestPlaying()) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = ((now - last) / 1_000_000f).coerceIn(0f, 100f)
                last = now
                val target = needleAngle(true, latestVolume(), latestLevel())
                val k = (dt / 120f).coerceIn(0f, 1f)
                needle += (target - needle) * k
            }
        }
        needle = -50f
    }

    val gloss = remember {
        Brush.verticalGradient(0f to Color.White.copy(alpha = 0.28f), 1f to Color.Transparent)
    }
    // Face brush depends on layout size: cache it on size/theme changes so the
    // per-frame draw path allocates nothing.
    var facePx by remember { mutableStateOf(IntSize.Zero) }
    val faceBrush = remember(tokens, facePx) {
        tokens.vuFaceBrush(
            center = Offset(facePx.width / 2f, facePx.height * (118f / 126f)),
            radius = facePx.width.coerceAtMost((facePx.height * (200f / 126f)).toInt()) * 0.64f,
        )
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tokens.vuFaceInner)
            .onSizeChanged { facePx = it }
            .drawBehind { drawRect(faceBrush) },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(200f / 126f)) {
            val sx = size.width / 200f
            val sy = size.height / 126f
            fun px(x: Float, y: Float) = Offset(x * sx, y * sy)

            val cx = 100f * sx
            val cy = 118f * sy
            val radius = 80f * sx
            drawArc(
                color = tokens.vuInk,
                startAngle = 220f,
                sweepAngle = 75f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                style = Stroke(2f * sx),
            )
            drawArc(
                color = tokens.vuRed,
                startAngle = 295f,
                sweepAngle = 25f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                style = Stroke(5f * sx),
            )
            for (t in VuTicks) {
                drawLine(tokens.vuInk, px(t.x1, t.y1), px(t.x2, t.y2), strokeWidth = 2f * sx)
            }
            for (l in labels) {
                drawText(
                    l.layout,
                    topLeft = Offset(l.x * sx - l.layout.size.width / 2f, l.y * sy - l.layout.size.height / 2f),
                )
            }
            rotate(needle, pivot = Offset(cx, cy)) {
                drawLine(tokens.vuNeedle, Offset(cx, cy), Offset(cx, 38f * sy), strokeWidth = 2f * sx)
            }
            drawCircle(tokens.vuCap, 9f * sx, Offset(cx, cy))
            drawRect(gloss, size = androidx.compose.ui.geometry.Size(size.width, size.height / 2f))
        }
    }
}
