package com.yass.vintageplayer.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.vintageDataStore by preferencesDataStore(name = "vintage")

private object PrefKeys {
    val Dark = booleanPreferencesKey("dark")
    val Accent = longPreferencesKey("accent")
    val Volume = floatPreferencesKey("volume")
    val Shuffle = booleanPreferencesKey("shuffle")
    val RepeatOne = booleanPreferencesKey("repeat_one")
    val Folder = stringPreferencesKey("folder")
    val LastTrackId = longPreferencesKey("last_track_id")
    val LastPositionMs = longPreferencesKey("last_position_ms")
    val Favorites = stringSetPreferencesKey("favorites")
}

private fun defaultSettings() = AppSettings()

private fun Preferences.toAppSettings(): AppSettings {
    val defaults = defaultSettings()
    return AppSettings(
        dark = this[PrefKeys.Dark] ?: defaults.dark,
        accent = this[PrefKeys.Accent] ?: defaults.accent,
        volume = this[PrefKeys.Volume] ?: defaults.volume,
        shuffle = this[PrefKeys.Shuffle] ?: defaults.shuffle,
        repeatOne = this[PrefKeys.RepeatOne] ?: defaults.repeatOne,
        folder = this[PrefKeys.Folder],
        lastTrackId = this[PrefKeys.LastTrackId],
        lastPositionMs = this[PrefKeys.LastPositionMs] ?: defaults.lastPositionMs,
    )
}

private fun Preferences.toFavorites(): Set<Long> {
    return this[PrefKeys.Favorites]?.mapNotNullTo(LinkedHashSet()) { it.toLongOrNull() }
        ?: emptySet()
}

class DataStoreSettings(
    ctx: Context,
    private val scope: CoroutineScope,
) : SettingsStore {
    private val appCtx = ctx.applicationContext

    private val _settings = MutableStateFlow(
        runBlocking { appCtx.vintageDataStore.data.map { it.toAppSettings() }.first() },
    )
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        scope.launch {
            appCtx.vintageDataStore.data.map { it.toAppSettings() }
                .distinctUntilChanged()
                .collect { if (it != _settings.value) _settings.value = it }
        }
    }

    override fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        scope.launch(Dispatchers.IO) {
            appCtx.vintageDataStore.edit { prefs ->
                prefs[PrefKeys.Dark] = next.dark
                prefs[PrefKeys.Accent] = next.accent
                prefs[PrefKeys.Volume] = next.volume
                prefs[PrefKeys.Shuffle] = next.shuffle
                prefs[PrefKeys.RepeatOne] = next.repeatOne
                if (next.folder == null) {
                    prefs.remove(PrefKeys.Folder)
                } else {
                    prefs[PrefKeys.Folder] = next.folder
                }
                if (next.lastTrackId == null) {
                    prefs.remove(PrefKeys.LastTrackId)
                } else {
                    prefs[PrefKeys.LastTrackId] = next.lastTrackId
                }
                prefs[PrefKeys.LastPositionMs] = next.lastPositionMs
            }
        }
    }
}

class DataStoreFavorites(
    ctx: Context,
    private val scope: CoroutineScope,
) : FavoritesStore {
    private val appCtx = ctx.applicationContext

    private val _favorites: MutableStateFlow<Set<Long>> = MutableStateFlow(
        runBlocking { appCtx.vintageDataStore.data.map { it.toFavorites() }.first() },
    )
    override val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    init {
        scope.launch {
            appCtx.vintageDataStore.data.map { it.toFavorites() }
                .distinctUntilChanged()
                .collect { if (it != _favorites.value) _favorites.value = it }
        }
    }

    override fun toggle(trackId: Long) {
        val current = _favorites.value
        val next = if (current.contains(trackId)) current - trackId else current + trackId
        if (next == current) return
        _favorites.value = next
        scope.launch(Dispatchers.IO) {
            appCtx.vintageDataStore.edit { prefs ->
                prefs[PrefKeys.Favorites] = next.mapTo(LinkedHashSet(next.size)) { it.toString() }
            }
        }
    }
}
