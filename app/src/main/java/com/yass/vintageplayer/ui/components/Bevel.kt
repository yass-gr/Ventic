package com.yass.vintageplayer.ui.components

import android.content.res.Resources
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yass.vintageplayer.ui.theme.LocalVintage

/**
 * Skeuomorphic bevel, port of the design's `bevel(on)`.
 *
 * Off: gradient face + 1 dp border + outer drop shadow + top highlight.
 * On: darker gradient + blurred inset shadow clipped to [shape].
 */
@Composable
fun Modifier.bevel(on: Boolean, shape: Shape, shadowElevation: Dp = 4.dp): Modifier {
    val tokens = LocalVintage.current
    val brush = remember(tokens, on) { tokens.btnBrush(on) }
    val borderColor = tokens.btnBorder
    val highlight = remember(tokens) {
        if (tokens.dark) Color.White.copy(alpha = 0.15f) else Color.White
    }
    val density = Resources.getSystem().displayMetrics.density
    val insetPaint = remember {
        Paint().apply {
            color = android.graphics.Color.BLACK
            alpha = 140
            maskFilter = BlurMaskFilter(6f * density, BlurMaskFilter.Blur.NORMAL)
        }
    }
    val base = if (on) this else this.shadow(shadowElevation, shape, clip = false)
    return base
        .background(brush, shape)
        .border(1.dp, borderColor, shape)
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            if (on) {
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.clipPath(outlineToAndroidPath(outline))
                val strokePx = 5 * density
                canvas.drawRect(-strokePx, -strokePx, size.width + strokePx, strokePx * 2f, insetPaint)
                canvas.restore()
            } else {
                translate(top = 0.75f * density) {
                    drawOutline(outline, color = highlight, style = Stroke(width = 1f * density))
                }
            }
        }
}

private fun outlineToAndroidPath(outline: Outline): android.graphics.Path {
    val path = android.graphics.Path()
    when (outline) {
        is Outline.Rounded -> {
            val r = outline.roundRect
            path.addRoundRect(
                android.graphics.RectF(r.left, r.top, r.right, r.bottom),
                floatArrayOf(
                    r.topLeftCornerRadius.x, r.topLeftCornerRadius.y,
                    r.topRightCornerRadius.x, r.topRightCornerRadius.y,
                    r.bottomRightCornerRadius.x, r.bottomRightCornerRadius.y,
                    r.bottomLeftCornerRadius.x, r.bottomLeftCornerRadius.y,
                ),
                android.graphics.Path.Direction.CW,
            )
        }
        is Outline.Rectangle -> {
            val r = outline.rect
            path.addRect(r.left, r.top, r.right, r.bottom, android.graphics.Path.Direction.CW)
        }
        // Rounded shapes cover every bevel use; fall back to a full-bounds clip.
        is Outline.Generic -> path.addRect(0f, 0f, 10_000f, 10_000f, android.graphics.Path.Direction.CW)
    }
    return path
}

/**
 * Tactile bevel button: shows the "on" style while pressed.
 * Port of the design's push-buttons (`st.btn`, transport row, PLAY ALL …).
 */
@Composable
fun BevelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clip(shape)
            .bevel(on || pressed, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
