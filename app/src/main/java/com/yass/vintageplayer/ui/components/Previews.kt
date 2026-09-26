package com.yass.vintageplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yass.vintageplayer.data.Track
import com.yass.vintageplayer.playback.AudioLevels
import com.yass.vintageplayer.ui.theme.VintageTheme

private fun sampleTrack(id: Long) = Track(
    id = id,
    uri = "content://media/external/audio/media/$id",
    title = "Paper Satellites",
    artist = "Juniper Lane",
    album = "Copper Skies",
    albumId = id,
    durationMs = 225_000L,
    relativePath = "Music/Juniper Lane/",
    dateAddedSec = 0L,
)

private val PreviewAccents = listOf(Color(0xFF27B4E6), Color(0xFFF0A330), Color(0xFFE5544B), Color(0xFF7CC957))

@Composable
private fun KitPreview(dark: Boolean, accent: Color) {
    VintageTheme(dark = dark, accent = accent) {
        val silent = remember { AudioLevels.SILENT }
        Column(
            modifier = Modifier.background(if (dark) Color(0xFF2C2E31) else Color(0xFFDEDED1)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LcdPanel(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LcdPill("03 SAVED")
                        SectionLabel("NOW PLAYING")
                    }
                    SpectrumBars(levels = { silent }, playing = { false })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BevelButton(onClick = {}, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    SectionLabel("PLAY ALL")
                }
                Led(on = true)
                Led(on = false)
            }
            CoverArt(sampleTrack(3), 60.dp, 6.dp)
            SeekGroove(progress = { 0.28f }, onSeek = {})
            VolumeKnob(volume = 0.72f, onVolume = {})
            VuMeter(level = { 0f }, volume = { 0.72f }, playing = { false })
            MiniPlayer(
                track = sampleTrack(1),
                artistLine = "Juniper Lane · 1:03",
                progress = 0.28f,
                playing = true,
                onOpen = {},
                onToggle = {},
                onNext = {},
            )
            BottomNav(selected = NavTab.PLAYING, onSelect = {})
            Module(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                SectionLabel("LEVEL", modifier = Modifier.padding(10.dp))
            }
        }
    }
}

@Preview(name = "kit-dark", widthDp = 390, heightDp = 1400)
@Composable
private fun KitPreviewDark() = KitPreview(dark = true, accent = PreviewAccents[0])

@Preview(name = "kit-light", widthDp = 390, heightDp = 1400)
@Composable
private fun KitPreviewLight() = KitPreview(dark = false, accent = PreviewAccents[0])

@Preview(name = "kit-dark-amber", widthDp = 390, heightDp = 1400)
@Composable
private fun KitPreviewDarkAmber() = KitPreview(dark = true, accent = PreviewAccents[1])
