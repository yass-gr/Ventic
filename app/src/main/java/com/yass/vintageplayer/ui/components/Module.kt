package com.yass.vintageplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.yass.vintageplayer.ui.theme.LocalVintage

/**
 * Module card, port of `st.module`
 * (module gradient + 1 dp border + module shadow, 14 dp radius).
 */
@Composable
fun Module(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tokens = LocalVintage.current
    val brush = remember(tokens) { tokens.moduleBrush }
    Box(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(14.dp), clip = false)
            .background(brush, RoundedCornerShape(14.dp))
            .border(1.dp, tokens.moduleBorder, RoundedCornerShape(14.dp)),
        content = { content() },
    )
}
