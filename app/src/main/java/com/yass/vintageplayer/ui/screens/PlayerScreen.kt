package com.yass.vintageplayer.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yass.vintageplayer.AppGraph
import com.yass.vintageplayer.ui.MainViewModel
import com.yass.vintageplayer.ui.Screen
import com.yass.vintageplayer.ui.UiState
import com.yass.vintageplayer.ui.components.BevelButton
import com.yass.vintageplayer.ui.components.CoverArt
import com.yass.vintageplayer.ui.components.LcdPanel
import com.yass.vintageplayer.ui.components.LcdPill
import com.yass.vintageplayer.ui.components.Led
import com.yass.vintageplayer.ui.components.Module
import com.yass.vintageplayer.ui.components.SectionLabel
import com.yass.vintageplayer.ui.components.SeekGroove
import com.yass.vintageplayer.ui.components.SpectrumBars
import com.yass.vintageplayer.ui.components.VintageIcons
import com.yass.vintageplayer.ui.components.VolumeKnob
import com.yass.vintageplayer.ui.components.VuMeter
import com.yass.vintageplayer.ui.formatMs
import com.yass.vintageplayer.ui.theme.Barlow
import com.yass.vintageplayer.ui.theme.LocalVintage
import com.yass.vintageplayer.ui.theme.Orbitron
import com.yass.vintageplayer.ui.theme.ShareTechMono
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(ui: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    val tokens = LocalVintage.current
    val player = remember { AppGraph.player }
    val playback = ui.playback
    val track = playback.current

    val queueSize = playback.queueSize
    val trkTotal = if (queueSize > 0) queueSize else ui.tracks.size
    val trkNum = if (queueSize > 0) playback.queueIndex + 1 else {
        if (track != null) ui.tracks.indexOfFirst { it.id == track.id }.takeIf { it >= 0 }?.plus(1) ?: 0
        else 0
    }
    val trkNumStr = trkNum.toString().padStart(2, '0')

    val durationMs = when {
        playback.durationMs > 0L -> playback.durationMs
        track != null -> track.durationMs
        else -> 0L
    }

    // Snapshot state: levels are consumed inside Canvas draw scopes only,
    // so the spectrum/VU redraw without recomposing this screen.
    val levels by player.levels.collectAsStateWithLifecycle()

    // Time TEXT ticks at 250 ms, only while this screen is composed.
    var posTextMs by remember(track?.id) { mutableLongStateOf(player.positionMs()) }
    LaunchedEffect(track?.id, playback.isPlaying) {
        if (!playback.isPlaying) {
            posTextMs = player.positionMs().coerceAtLeast(0L)
        } else {
            while (isActive) {
                delay(250L)
                posTextMs = player.positionMs().coerceAtLeast(0L)
            }
        }
    }

    // Draw-phase progress for the seek groove: updated every frame while
    // playing, read only inside SeekGroove's Canvas (no recomposition).
    var progressFrac by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(track?.id, playback.isPlaying, durationMs) {
        if (!playback.isPlaying || durationMs <= 0L) {
            progressFrac = if (durationMs > 0L) {
                (player.positionMs().toFloat() / durationMs).coerceIn(0f, 1f)
            } else 0f
        } else {
            while (isActive) {
                withFrameNanos {
                    progressFrac = (player.positionMs().toFloat() / durationMs).coerceIn(0f, 1f)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header: back + NOW PLAYING + TRACK nn OF nn.
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BevelButton(
                onClick = { vm.setScreen(Screen.LIBRARY) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp),
            ) {
                Image(
                    VintageIcons.ChevronDown,
                    contentDescription = "Back to library",
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(tokens.btnText),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel("NOW PLAYING", fontSize = 14.sp, letterSpacing = 3.sp)
                BasicText(
                    "TRACK $trkNumStr OF $trkTotal",
                    style = TextStyle(
                        color = tokens.sub,
                        fontFamily = ShareTechMono,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                    ),
                )
            }
            Box(modifier = Modifier.size(44.dp))
        }

        // LCD block.
        LcdPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val dim = if (tokens.dark) tokens.accent.copy(alpha = 0.25f)
                    else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f)
                    LcdIndicator("TRK $trkNumStr", tokens.lcdText)
                    LcdIndicator("SHUF", if (playback.shuffle) tokens.lcdText else dim)
                    LcdIndicator("REP", if (playback.repeatOne) tokens.lcdText else dim)
                    val curFav = track != null && track.id in ui.favorites
                    LcdIndicator("FAV", if (curFav) tokens.lcdText else dim)
                    Box(modifier = Modifier.weight(1f))
                    LcdIndicator(if (playback.isPlaying) "PLAY" else "PAUSE", tokens.lcdText)
                }
                if (track != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CoverArt(track, 60.dp, 6.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BasicText(
                                track.title.uppercase(),
                                style = TextStyle(
                                    color = tokens.lcdText,
                                    fontFamily = Orbitron,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    letterSpacing = 1.sp,
                                    shadow = tokens.lcdGlow,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            BasicText(
                                track.artist.uppercase(),
                                style = TextStyle(
                                    color = tokens.lcdText,
                                    fontFamily = ShareTechMono,
                                    fontSize = 15.sp,
                                    letterSpacing = 1.sp,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    BasicText(
                        "NO TRACK LOADED",
                        style = TextStyle(
                            color = tokens.lcdText,
                            fontFamily = Orbitron,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp,
                            shadow = tokens.lcdGlow,
                        ),
                    )
                }
                SpectrumBars(
                    levels = { levels },
                    playing = { playback.isPlaying },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BasicText(
                        formatMs(posTextMs),
                        style = TextStyle(
                            color = tokens.lcdText,
                            fontFamily = Orbitron,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 24.sp,
                            shadow = tokens.lcdGlow,
                        ),
                    )
                    BasicText(
                        "/ ${formatMs(durationMs)}",
                        style = TextStyle(
                            color = tokens.lcdText,
                            fontFamily = ShareTechMono,
                            fontSize = 15.sp,
                        ),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    Box(modifier = Modifier.weight(1f))
                    BasicText(
                        "-${formatMs((durationMs - posTextMs).coerceAtLeast(0L))}",
                        style = TextStyle(
                            color = tokens.lcdText,
                            fontFamily = ShareTechMono,
                            fontSize = 15.sp,
                        ),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        }

        // Seek groove (draw-phase progress).
        SeekGroove(
            progress = { progressFrac },
            onSeek = { frac ->
                if (durationMs > 0L) {
                    progressFrac = frac
                    vm.seekTo((frac * durationMs).toLong())
                }
            },
        )

        // Transport row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BevelButton(
                onClick = vm::toggleShuffle,
                on = playback.shuffle,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(52.dp, 58.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Led(playback.shuffle)
                    Image(
                        VintageIcons.Shuffle,
                        contentDescription = "Shuffle",
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(tokens.btnText),
                    )
                }
            }
            BevelButton(
                onClick = vm::previous,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(62.dp),
            ) {
                Image(
                    VintageIcons.Prev,
                    contentDescription = "Previous track",
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(tokens.btnText),
                )
            }
            PlayRing(
                playing = playback.isPlaying,
                onToggle = vm::togglePlay,
            )
            BevelButton(
                onClick = vm::next,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(62.dp),
            ) {
                Image(
                    VintageIcons.Next,
                    contentDescription = "Next track",
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(tokens.btnText),
                )
            }
            BevelButton(
                onClick = vm::toggleRepeat,
                on = playback.repeatOne,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(52.dp, 58.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Led(playback.repeatOne)
                    Image(
                        VintageIcons.Repeat,
                        contentDescription = "Repeat track",
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(tokens.btnText),
                    )
                }
            }
        }

        // VU + volume modules.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Module(modifier = Modifier.weight(1f).padding(0.dp)) {
                Column(
                    modifier = Modifier.padding(10.dp, 10.dp, 10.dp, 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VuMeter(
                        level = { levels.level },
                        volume = { playback.volume },
                        playing = { playback.isPlaying },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SectionLabel("LEVEL", fontSize = 12.sp, letterSpacing = 2.5.sp)
                }
            }
            Module(modifier = Modifier.width(138.dp)) {
                Column(
                    modifier = Modifier.padding(10.dp, 10.dp, 8.dp, 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    VolumeKnob(
                        volume = playback.volume,
                        onVolume = vm::setVolume,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionLabel("VOL", fontSize = 12.sp, letterSpacing = 2.sp)
                        LcdPill("${(playback.volume * 100).roundToInt()}%")
                    }
                }
            }
        }

        // LOVE THIS.
        val curFav = track != null && track.id in ui.favorites
        BevelButton(
            onClick = vm::toggleCurrentFav,
            on = curFav,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Led(curFav, size = 7.dp)
                Image(
                    if (curFav) VintageIcons.HeartFilled else VintageIcons.Heart,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(if (curFav) tokens.accentText else tokens.btnText),
                )
                BasicText(
                    if (curFav) "LOVED" else "LOVE THIS",
                    style = TextStyle(
                        color = tokens.btnText,
                        fontFamily = Barlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 2.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun LcdIndicator(text: String, color: androidx.compose.ui.graphics.Color) {
    BasicText(
        text,
        style = TextStyle(
            color = color,
            fontFamily = ShareTechMono,
            fontSize = 12.sp,
            letterSpacing = 1.5.sp,
        ),
    )
}

/** 104 dp play ring with glowing groove when playing, 92 dp knob button inside. */
@Composable
private fun PlayRing(playing: Boolean, onToggle: () -> Unit) {
    val tokens = LocalVintage.current
    Box(
        modifier = Modifier
            .size(104.dp)
            .shadow(
                6.dp,
                CircleShape,
                clip = false,
                ambientColor = if (playing) tokens.accent else androidx.compose.ui.graphics.Color.Black,
                spotColor = if (playing) tokens.accent else androidx.compose.ui.graphics.Color.Black,
            )
            .background(tokens.groove, CircleShape)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BevelButton(
            onClick = onToggle,
            shape = CircleShape,
            modifier = Modifier.size(92.dp),
        ) {
            Image(
                if (playing) VintageIcons.Pause else VintageIcons.Play,
                contentDescription = if (playing) "Pause" else "Play",
                modifier = Modifier.size(if (playing) 34.dp else 36.dp),
                colorFilter = ColorFilter.tint(tokens.playIcon),
            )
        }
    }
}
