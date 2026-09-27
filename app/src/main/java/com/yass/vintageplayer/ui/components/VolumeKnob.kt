package com.yass.vintageplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.yass.vintageplayer.ui.theme.LocalVintage
import com.yass.vintageplayer.ui.theme.knobDegrees
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Volume knob, port of the design's VOL module (104 dp).
 *
 * Ring: accent arc over 270° starting at 225° (`volRing` conic); inner knob
 * uses the conic `knob` sweep; the accent dot rotates `-135 + vol*270`.
 * Both vertical and circular drags adjust the volume; a haptic tick fires
 * every 5 %.
 */
@Composable
fun VolumeKnob(volume: Float, onVolume: (Float) -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalVintage.current
    val haptics = LocalHapticFeedback.current
    var lastTick by remember { mutableIntStateOf((volume * 20).toInt()) }
    val latestVolume by rememberUpdatedState(volume)
    val latestOnVolume by rememberUpdatedState(onVolume)
    val vol = volume.coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .size(104.dp)
            .pointerInput(Unit) {
                var prevAngle: Float? = null
                var dragValue = 0f
                detectDragGestures(
                    onDragStart = {
                        prevAngle = null
                        dragValue = latestVolume.coerceIn(0f, 1f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val cx = w / 2f
                        val cy = h / 2f
                        val px = change.position.x - cx
                        val py = change.position.y - cy
                        val radius = kotlin.math.hypot(px, py)
                        val angle = Math.toDegrees(atan2(px.toDouble(), -py.toDouble())).toFloat()
                        var delta = -dragAmount.y / 400f
                        if (radius > w * 0.15f) {
                            val prev = prevAngle
                            if (prev != null) {
                                var d = angle - prev
                                if (d > 180f) d -= 360f
                                if (d < -180f) d += 360f
                                delta += d / 270f
                            }
                            prevAngle = angle
                        }
                        dragValue = (dragValue + delta).coerceIn(0f, 1f)
                        val next = dragValue
                        val tick = (next * 20).toInt()
                        if (tick != lastTick) {
                            lastTick = tick
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        latestOnVolume(next)
                    },
                )
            },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val ringOuter = size.width / 2f
        val ringWidth = 6f * density
        drawArc(
            color = tokens.ringTrack,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(ringWidth),
        )
        drawArc(
            color = tokens.accent,
            startAngle = 135f,
            sweepAngle = vol * 270f,
            useCenter = false,
            style = Stroke(ringWidth),
        )
        val knobR = ringOuter - ringWidth - 5f * density
        drawCircle(tokens.moduleSolid, knobR, Offset(cx, cy))
        drawCircle(tokens.knobBrush, knobR, Offset(cx, cy))
        drawCircle(tokens.moduleSolid, knobR, Offset(cx, cy), style = Stroke(1f))
        val a = Math.toRadians(knobDegrees(vol).toDouble())
        val dotR = knobR - 9f * density
        val dot = Offset(cx + (sin(a) * dotR).toFloat(), cy - (cos(a) * dotR).toFloat())
        drawCircle(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f), 5.5f * density, dot + Offset(0f, 1f))
        drawCircle(tokens.accent, 4.5f * density, dot)
    }
}
