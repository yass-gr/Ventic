package com.yass.vintageplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yass.vintageplayer.ui.theme.LocalVintage
import com.yass.vintageplayer.ui.theme.ShareTechMono

/**
 * Monospace LCD pill, port of `st.lcdPill`
 * (mono 13 px, LCD text on LCD bg, pill shadow, LCD glow).
 */
@Composable
fun LcdPill(text: String, modifier: Modifier = Modifier) {
    val tokens = LocalVintage.current
    val lcd = remember(tokens) { tokens.lcdBrush }
    Box(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(6.dp), clip = false)
            .background(lcd, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = tokens.lcdText,
                fontFamily = ShareTechMono,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                shadow = tokens.lcdGlow,
            ),
        )
    }
}
