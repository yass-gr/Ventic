package com.yass.vintageplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yass.vintageplayer.data.Track
import com.yass.vintageplayer.ui.theme.LocalVintage
import com.yass.vintageplayer.ui.theme.Orbitron
import com.yass.vintageplayer.ui.theme.ShareTechMono

/**
 * Mini player strip shown on non-player screens, port of the design's
 * `notPlayer` block: LCD button (cover 38 dp + titles + 2 px progress line)
 * plus 52 dp play and next bevel buttons.
 *
 * Layout only: [progress] is supplied by the caller, which owns position
 * text updates on a low tick rate.
 */
@Composable
fun MiniPlayer(
    track: Track,
    artistLine: String,
    progress: Float,
    playing: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalVintage.current
    val bezel = remember(tokens) { tokens.bezelBrush }
    val lcd = remember(tokens) { tokens.lcdBrush }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(14.dp), clip = false)
            .background(bezel, RoundedCornerShape(14.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(lcd, RoundedCornerShape(9.dp))
                .clickable(role = Role.Button, onClick = onOpen),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp).height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverArt(track, 38.dp, 5.dp)
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    BasicText(
                        track.title.uppercase(),
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(
                            color = tokens.lcdText,
                            fontFamily = Orbitron,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp,
                            shadow = tokens.lcdGlow,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(
                        artistLine.uppercase(),
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(
                            color = tokens.lcdText,
                            fontFamily = ShareTechMono,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(tokens.lcdText),
            )
        }
        BevelButton(onClick = onToggle, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(52.dp)) {
            Image(
                if (playing) VintageIcons.Pause else VintageIcons.Play,
                contentDescription = if (playing) "Pause" else "Play",
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(tokens.btnText),
            )
        }
        BevelButton(onClick = onNext, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(52.dp)) {
            Image(
                VintageIcons.Next,
                contentDescription = "Next track",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(tokens.btnText),
            )
        }
    }
}
