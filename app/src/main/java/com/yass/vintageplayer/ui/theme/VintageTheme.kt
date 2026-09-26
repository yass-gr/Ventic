package com.yass.vintageplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalVintage = staticCompositionLocalOf<VintageTokens> {
    error("VintageTheme not provided")
}

/** Current design tokens. */
val currentVintage: VintageTokens
    @Composable
    get() = LocalVintage.current

@Composable
fun VintageTheme(
    dark: Boolean,
    accent: Color,
    content: @Composable () -> Unit,
) {
    val tokens = remember(dark, accent) { vintageTokens(dark, accent) }
    CompositionLocalProvider(LocalVintage provides tokens, content = content)
}
