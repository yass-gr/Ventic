package com.yass.vintageplayer.data

import kotlinx.coroutines.flow.StateFlow

/** CONTRACT (Phase 0). Implemented by Agent A. */
interface LibraryRepository {
    /** All tracks (after folder filter), sorted by title (case-insensitive). Emits cached data first. */
    val tracks: StateFlow<List<Track>>
    val isScanning: StateFlow<Boolean>
    /** Distinct top-level RELATIVE_PATH folders that contain audio, e.g. ["Download/", "Music/"]. */
    val folders: StateFlow<List<String>>
    /** Load the on-disk cache (instant), then refresh from MediaStore in the background. Idempotent. */
    suspend fun start()
    /** Force a full MediaStore re-query. */
    suspend fun rescan()
}

/** CONTRACT (Phase 0). Implemented by Agent A. */
interface FavoritesStore {
    val favorites: StateFlow<Set<Long>>
    fun toggle(trackId: Long)
}

/** CONTRACT (Phase 0). Implemented by Agent A. */
interface SettingsStore {
    val settings: StateFlow<AppSettings>
    fun update(transform: (AppSettings) -> AppSettings)
}
