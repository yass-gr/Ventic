package com.yass.vintageplayer

import android.content.Context
import com.yass.vintageplayer.data.DataStoreFavorites
import com.yass.vintageplayer.data.DataStoreSettings
import com.yass.vintageplayer.data.FavoritesStore
import com.yass.vintageplayer.data.LibraryRepository
import com.yass.vintageplayer.data.MediaStoreLibrary
import com.yass.vintageplayer.data.SettingsStore
import com.yass.vintageplayer.playback.MediaControllerConnection
import com.yass.vintageplayer.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual DI (no Hilt/Koin — keeps cold start fast). Everything is created lazily on first use. */
object AppGraph {
    lateinit var app: Context
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsStore by lazy { DataStoreSettings(app, scope) }
    val favorites: FavoritesStore by lazy { DataStoreFavorites(app, scope) }
    val library: LibraryRepository by lazy { MediaStoreLibrary(app, settings, scope) }
    val connection: MediaControllerConnection by lazy {
        MediaControllerConnection(app, scope, settings).also { conn ->
            conn.trackResolver = { id -> library.tracks.value.firstOrNull { it.id == id } }
        }
    }
    val player: PlayerConnection get() = connection

    fun init(context: Context) {
        app = context.applicationContext
    }
}
