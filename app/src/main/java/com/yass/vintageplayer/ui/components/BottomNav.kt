package com.yass.vintageplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yass.vintageplayer.ui.theme.Barlow
import com.yass.vintageplayer.ui.theme.LocalVintage

enum class NavTab { LIBRARY, FAVORITES, PLAYING, SETTINGS }

private data class NavEntry(val tab: NavTab, val label: String, val icon: ImageVector)

/**
 * Bottom navigation, port of the design's `nav`.
 * 4 items (LED + icon + label); dark brushed metal, light walnut.
 */
@Composable
fun BottomNav(selected: NavTab, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalVintage.current
    val bg = remember(tokens) { Brush.verticalGradient(listOf(tokens.navTop, tokens.navBottom)) }
    val entries = remember {
        listOf(
            NavEntry(NavTab.LIBRARY, "LIBRARY", VintageIcons.MusicNote),
            NavEntry(NavTab.FAVORITES, "FAVORITES", VintageIcons.Heart),
            NavEntry(NavTab.PLAYING, "PLAYING", VintageIcons.Disc),
            NavEntry(NavTab.SETTINGS, "SETTINGS", VintageIcons.Sliders),
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(bg)
            .drawBehind {
                if (tokens.navWood) {
                    val dark1 = Color(0x1F3C1E0A)
                    val light = Color(0x0FFFDCA4)
                    var x = 0f
                    while (x < size.width) {
                        drawRect(dark1, Offset(x, 0f), androidx.compose.ui.geometry.Size(density, size.height))
                        drawRect(light, Offset(x + 6f * density, 0f), androidx.compose.ui.geometry.Size(density, size.height))
                        x += 13f * density
                    }
                }
            }
            .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (entry in entries) {
            val on = entry.tab == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .clickable(role = Role.Tab, onClick = { onSelect(entry.tab) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Led(on, size = 6.dp)
                Image(
                    entry.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp).padding(top = 4.dp),
                    colorFilter = ColorFilter.tint(if (on) tokens.navOn else tokens.navText),
                )
                BasicText(
                    entry.label,
                    style = TextStyle(
                        color = if (on) tokens.navOn else tokens.navText,
                        fontFamily = Barlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.5.sp,
                        shadow = tokens.navLabelShadow,
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
