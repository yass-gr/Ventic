package com.yass.vintageplayer

import android.content.Context
import com.yass.vintageplayer.data.FavoritesStore
import com.yass.vintageplayer.data.LibraryRepository
import com.yass.vintageplayer.data.SettingsStore
import com.yass.vintageplayer.playback.PlayerConnection

/**
 * Manual DI (no Hilt/Koin — keeps cold start fast). Implementations are wired
 * by the lead after the agents deliver; until then these are lateinit.
 */
object AppGraph {
    lateinit var app: Context
        private set
    lateinit var library: LibraryRepository
    lateinit var favorites: FavoritesStore
    lateinit var settings: SettingsStore
    lateinit var player: PlayerConnection

    fun init(context: Context) {
        app = context.applicationContext
    }
}
