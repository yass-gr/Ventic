package com.yass.vintageplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yass.vintageplayer.AppGraph
import com.yass.vintageplayer.data.AppSettings
import com.yass.vintageplayer.data.Track
import com.yass.vintageplayer.playback.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Navigation screens. Default entry point is LIBRARY. */
enum class Screen { LIBRARY, FAVORITES, PLAYER, SETTINGS }

/** Combined UI state. `visible` is the current list (library filtered by query, or favorites). */
data class UiState(
    val screen: Screen = Screen.LIBRARY,
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val visible: List<Track> = emptyList(),
    val favorites: Set<Long> = emptySet(),
    val settings: AppSettings = AppSettings(),
    val playback: PlaybackState = PlaybackState(),
    val isScanning: Boolean = false,
    val folders: List<String> = emptyList(),
)

/** m:ss formatter for durations and positions. */
fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
}

private fun computeVisible(
    tracks: List<Track>,
    favorites: Set<Long>,
    screen: Screen,
    query: String,
): List<Track> {
    val base = if (screen == Screen.FAVORITES) {
        if (favorites.isEmpty()) emptyList() else tracks.filter { it.id in favorites }
    } else {
        tracks
    }
    if (screen == Screen.LIBRARY) {
        val q = query.trim().lowercase()
        if (q.isNotEmpty()) {
            return base.filter { (it.title + " " + it.artist).lowercase().contains(q) }
        }
    }
    return base
}

/**
 * Owns navigation, search, and every action from the design's `renderVals()`
 * handlers. Filtering runs on [Dispatchers.Default] via `flowOn`.
 */
class MainViewModel : ViewModel() {
    private val library get() = AppGraph.library
    private val favoritesStore get() = AppGraph.favorites
    private val settingsStore get() = AppGraph.settings
    private val player get() = AppGraph.player

    private val _screen = MutableStateFlow(Screen.LIBRARY)
    private val _query = MutableStateFlow("")

    val uiState: StateFlow<UiState> = combine(
        combine(_screen, _query) { screen, query -> screen to query },
        combine(library.tracks, favoritesStore.favorites) { tracks, favorites -> tracks to favorites },
        combine(settingsStore.settings, player.state) { settings, playback -> settings to playback },
        combine(library.isScanning, library.folders) { isScanning, folders -> isScanning to folders },
    ) { screenQuery, tracksFavs, settingsPlayback, scanFolders ->
        val (screen, query) = screenQuery
        val (tracks, favorites) = tracksFavs
        val (settings, playback) = settingsPlayback
        val (isScanning, folders) = scanFolders
        UiState(
            screen = screen,
            query = query,
            tracks = tracks,
            visible = computeVisible(tracks, favorites, screen, query),
            favorites = favorites,
            settings = settings,
            playback = playback,
            isScanning = isScanning,
            folders = folders,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, initial())

    private fun initial(): UiState {
        val tracks = library.tracks.value
        val favorites = favoritesStore.favorites.value
        val screen = _screen.value
        val query = _query.value
        return UiState(
            screen = screen,
            query = query,
            tracks = tracks,
            visible = computeVisible(tracks, favorites, screen, query),
            favorites = favorites,
            settings = settingsStore.settings.value,
            playback = player.state.value,
            isScanning = library.isScanning.value,
            folders = library.folders.value,
        )
    }

    fun setScreen(screen: Screen) {
        _screen.value = screen
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    /** Queue = current list, start 0, shuffle off, go PLAYER. */
    fun playAll() {
        val list = uiState.value.visible
        if (list.isEmpty()) return
        player.playQueue(list, 0, false)
        _screen.value = Screen.PLAYER
    }

    /** Queue = current list, random start, shuffle on, go PLAYER. */
    fun shuffleAll() {
        val list = uiState.value.visible
        if (list.isEmpty()) return
        player.playQueue(list, Random.nextInt(list.size), true)
        _screen.value = Screen.PLAYER
    }

    /** Play the current list from [index], keeping the shuffle mode. */
    fun playTrack(index: Int) {
        val list = uiState.value.visible
        if (list.isEmpty()) return
        player.playQueue(list, index.coerceIn(0, list.size - 1), uiState.value.playback.shuffle)
        _screen.value = Screen.PLAYER
    }

    fun toggleFav(trackId: Long) = favoritesStore.toggle(trackId)

    fun toggleCurrentFav() {
        uiState.value.playback.current?.let { favoritesStore.toggle(it.id) }
    }

    fun togglePlay() = player.togglePlay()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun toggleShuffle() = player.setShuffle(!uiState.value.playback.shuffle)
    fun toggleRepeat() = player.setRepeatOne(!uiState.value.playback.repeatOne)
    fun setVolume(volume: Float) = player.setVolume(volume)

    fun setDark() = settingsStore.update { it.copy(dark = true) }
    fun setLight() = settingsStore.update { it.copy(dark = false) }
    fun setAccent(argb: Long) = settingsStore.update { it.copy(accent = argb) }

    fun rescan() {
        viewModelScope.launch { library.rescan() }
    }

    fun pickFolder(folder: String?) {
        settingsStore.update { it.copy(folder = folder) }
    }
}
