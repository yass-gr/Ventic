package com.yass.vintageplayer.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.yass.vintageplayer.ui.theme.Barlow
import com.yass.vintageplayer.ui.theme.LocalVintage

/**
 * Section label, port of `st.label` (label colour + text shadow).
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 12.sp, letterSpacing: TextUnit = 2.5.sp) {
    val tokens = LocalVintage.current
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = tokens.label,
            fontFamily = Barlow,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            letterSpacing = letterSpacing,
            shadow = tokens.labelShadow,
        ),
    )
}
