package com.yass.vintageplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yass.vintageplayer.AppGraph
import com.yass.vintageplayer.ui.components.BottomNav
import com.yass.vintageplayer.ui.components.MiniPlayer
import com.yass.vintageplayer.ui.components.NavTab
import com.yass.vintageplayer.ui.screens.FavoritesScreen
import com.yass.vintageplayer.ui.screens.LibraryScreen
import com.yass.vintageplayer.ui.screens.PlayerScreen
import com.yass.vintageplayer.ui.screens.SettingsScreen
import com.yass.vintageplayer.ui.theme.LocalVintage
import com.yass.vintageplayer.ui.theme.VintageTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private fun Screen.toTab(): NavTab = when (this) {
    Screen.LIBRARY -> NavTab.LIBRARY
    Screen.FAVORITES -> NavTab.FAVORITES
    Screen.PLAYER -> NavTab.PLAYING
    Screen.SETTINGS -> NavTab.SETTINGS
}

private fun NavTab.toScreen(): Screen = when (this) {
    NavTab.LIBRARY -> Screen.LIBRARY
    NavTab.FAVORITES -> Screen.FAVORITES
    NavTab.PLAYING -> Screen.PLAYER
    NavTab.SETTINGS -> Screen.SETTINGS
}

/**
 * App shell: theme wrapper, chassis background, instant screen switch,
 * mini player on non-player screens, bottom nav, system-back handling.
 *
 * Window insets (status bar top, navigation bar bottom) are applied on this
 * root so content never slides under the system bars.
 */
@Composable
fun VintageAppUi(vm: MainViewModel = viewModel(), modifier: Modifier = Modifier) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    VintageTheme(dark = ui.settings.dark, accent = Color(ui.settings.accent)) {
        val tokens = LocalVintage.current
        // System back: non-library screens go to LIBRARY first, then exit.
        BackHandler(enabled = ui.screen != Screen.LIBRARY) {
            vm.setScreen(Screen.LIBRARY)
        }
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(tokens.chassisBrush)
                .drawBehind { drawRect(tokens.chassisGrain) }
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (ui.screen) {
                    Screen.LIBRARY -> LibraryScreen(ui, vm)
                    Screen.FAVORITES -> FavoritesScreen(ui, vm)
                    Screen.PLAYER -> PlayerScreen(ui, vm)
                    Screen.SETTINGS -> SettingsScreen(ui, vm)
                }
            }
            val current = ui.playback.current
            if (ui.screen != Screen.PLAYER && current != null) {
                MiniPlayerStrip(ui = ui, vm = vm, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp))
            }
            BottomNav(selected = ui.screen.toTab(), onSelect = { vm.setScreen(it.toScreen()) })
        }
    }
}

/**
 * Mini player with its own 250 ms position ticker (text + thin progress
 * line). Ticks only while this strip is composed and playing.
 */
@Composable
private fun MiniPlayerStrip(ui: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    val track = ui.playback.current ?: return
    val player = remember { AppGraph.player }
    val durationMs = ui.playback.durationMs.takeIf { it > 0L } ?: track.durationMs

    var posMs by remember(track.id) { mutableLongStateOf(player.positionMs().coerceAtLeast(0L)) }
    LaunchedEffect(track.id, ui.playback.isPlaying) {
        if (!ui.playback.isPlaying) {
            posMs = player.positionMs().coerceAtLeast(0L)
        } else {
            while (isActive) {
                delay(250L)
                posMs = player.positionMs().coerceAtLeast(0L)
            }
        }
    }

    val progress = if (durationMs > 0L) (posMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    MiniPlayer(
        track = track,
        artistLine = "${track.artist} · ${formatMs(posMs)}",
        progress = progress,
        playing = ui.playback.isPlaying,
        onOpen = { vm.setScreen(Screen.PLAYER) },
        onToggle = vm::togglePlay,
        onNext = vm::next,
        modifier = modifier,
    )
}
