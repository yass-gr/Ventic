package com.yass.vintageplayer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yass.vintageplayer.AppGraph
import com.yass.vintageplayer.data.Track
import com.yass.vintageplayer.ui.MainViewModel
import com.yass.vintageplayer.ui.Screen
import com.yass.vintageplayer.ui.UiState
import com.yass.vintageplayer.ui.components.BevelButton
import com.yass.vintageplayer.ui.components.CoverArt
import com.yass.vintageplayer.ui.components.LcdPill
import com.yass.vintageplayer.ui.components.SectionLabel
import com.yass.vintageplayer.ui.components.VintageIcons
import com.yass.vintageplayer.ui.formatMs
import com.yass.vintageplayer.ui.theme.Barlow
import com.yass.vintageplayer.ui.theme.LocalVintage
import com.yass.vintageplayer.ui.theme.ShareTechMono

@Composable
fun LibraryScreen(ui: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    TrackList(
        ui = ui,
        vm = vm,
        isFavorites = false,
        modifier = modifier,
    )
}

@Composable
fun FavoritesScreen(ui: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    TrackList(
        ui = ui,
        vm = vm,
        isFavorites = true,
        modifier = modifier,
    )
}

@Composable
private fun TrackList(ui: UiState, vm: MainViewModel, isFavorites: Boolean, modifier: Modifier = Modifier) {
    val tokens = LocalVintage.current
    val title = if (isFavorites) "FAVORITES" else "LIBRARY"
    val count = if (isFavorites) {
        "${ui.favorites.size} SAVED"
    } else if (ui.query.trim().isEmpty()) {
        "${ui.tracks.size} TRACKS"
    } else {
        "${ui.visible.size} / ${ui.tracks.size}"
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                title,
                style = TextStyle(
                    color = tokens.label,
                    fontFamily = Barlow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = 4.sp,
                    shadow = tokens.labelShadow,
                ),
            )
            LcdPill(count)
        }

        if (!isFavorites) {
            SearchField(query = ui.query, onQuery = vm::setQuery)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BevelButton(
                onClick = vm::playAll,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(46.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        VintageIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(tokens.btnText),
                    )
                    BasicText(
                        "PLAY ALL",
                        style = TextStyle(
                            color = tokens.btnText,
                            fontFamily = Barlow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 2.sp,
                        ),
                    )
                }
            }
            BevelButton(
                onClick = vm::shuffleAll,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(46.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        VintageIcons.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(tokens.btnText),
                    )
                    BasicText(
                        "SHUFFLE",
                        style = TextStyle(
                            color = tokens.btnText,
                            fontFamily = Barlow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 2.sp,
                        ),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(14.dp), clip = false)
                .background(tokens.inset, RoundedCornerShape(14.dp))
                .padding(start = 10.dp, top = 2.dp, end = 8.dp, bottom = 2.dp),
        ) {
            if (ui.visible.isEmpty()) {
                if (isFavorites) EmptyFavorites() else EmptySearch(query = ui.query)
            } else {
                val currentId = ui.playback.current?.id
                val playing = ui.playback.isPlaying
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        ui.visible,
                        key = { _, track -> track.id },
                        contentType = { _, _ -> "track" },
                    ) { index, track ->
                        TrackRow(
                            track = track,
                            isCurrent = track.id == currentId,
                            playing = playing,
                            isFav = track.id in ui.favorites,
                            showDivider = index < ui.visible.size - 1,
                            onPlay = { vm.playTrack(index) },
                            onFav = { vm.toggleFav(track.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalVintage.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp), clip = false)
            .background(tokens.lcdBrush, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(
                color = tokens.lcdText,
                fontFamily = ShareTechMono,
                fontSize = 16.sp,
                letterSpacing = 1.sp,
            ),
            cursorBrush = SolidColor(tokens.lcdText),
            decorationBox = { inner ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Image(
                        VintageIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(tokens.lcdText),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            BasicText(
                                "SEARCH TITLE OR ARTIST",
                                style = TextStyle(
                                    color = tokens.lcdText.copy(alpha = 0.45f),
                                    fontFamily = ShareTechMono,
                                    fontSize = 16.sp,
                                    letterSpacing = 1.sp,
                                ),
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                }
            },
        )
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    playing: Boolean,
    isFav: Boolean,
    showDivider: Boolean,
    onPlay: () -> Unit,
    onFav: () -> Unit,
) {
    val tokens = LocalVintage.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(62.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button, onClick = onPlay)
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    CoverArt(track, 44.dp, 5.dp)
                    if (isCurrent) {
                        CurrentTrackOverlay(playing = playing)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(
                        track.title,
                        style = TextStyle(
                            color = if (isCurrent) tokens.accentText else tokens.text,
                            fontFamily = Barlow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            letterSpacing = 0.3.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(
                        "${track.artist} · ${formatMs(track.durationMs)}",
                        style = TextStyle(
                            color = tokens.sub,
                            fontFamily = Barlow,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onFav),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    if (isFav) VintageIcons.HeartFilled else VintageIcons.Heart,
                    contentDescription = if (isFav) "Remove ${track.title} from favorites"
                    else "Add ${track.title} to favorites",
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(if (isFav) tokens.accentText else tokens.sub),
                )
            }
        }
        if (showDivider) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.divider))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.dividerHi))
            }
        }
    }
}

/**
 * Dark scrim + 3 accent mini-bars over the current track's cover.
 * Levels are collected as snapshot state and read inside [Canvas], so only
 * the overlay redraws — the row never recomposes per frame.
 */
@Composable
private fun CurrentTrackOverlay(playing: Boolean) {
    val tokens = LocalVintage.current
    val levels by AppGraph.player.levels.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(modifier = Modifier.size(44.dp).padding(bottom = 12.dp)) {
            val bands = levels.bands
            val picks = if (playing) {
                floatArrayOf(
                    bands.getOrElse(3) { 0.2f },
                    bands.getOrElse(9) { 0.35f },
                    bands.getOrElse(15) { 0.27f },
                )
            } else {
                floatArrayOf(10f / 48f, 18f / 48f, 13f / 48f)
            }
            val barW = 4.dp.toPx()
            val gap = 3.dp.toPx()
            val totalW = barW * 3 + gap * 2
            var x = (size.width - totalW) / 2f
            for (v in picks) {
                val h = (18.dp.toPx() * v.coerceIn(0f, 1f)).coerceAtLeast(3.dp.toPx())
                drawRect(
                    color = tokens.accent,
                    topLeft = androidx.compose.ui.geometry.Offset(x, size.height - h),
                    size = androidx.compose.ui.geometry.Size(barW, h),
                )
                x += barW + gap
            }
        }
    }
}

@Composable
private fun EmptySearch(query: String) {
    val tokens = LocalVintage.current
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SectionLabel("NO MATCH", fontSize = 16.sp, letterSpacing = 2.sp)
        BasicText(
            "Nothing in your library matches \u201C${query.trim()}\u201D.",
            style = TextStyle(color = tokens.sub, fontFamily = Barlow, fontSize = 15.sp),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun EmptyFavorites() {
    val tokens = LocalVintage.current
    Column(
        modifier = Modifier.fillMaxSize().padding(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            VintageIcons.Heart,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            colorFilter = ColorFilter.tint(tokens.sub),
        )
        SectionLabel("NO FAVORITES YET", fontSize = 16.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 10.dp))
        BasicText(
            "Tap the heart on any track to keep it here.",
            style = TextStyle(color = tokens.sub, fontFamily = Barlow, fontSize = 15.sp),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
