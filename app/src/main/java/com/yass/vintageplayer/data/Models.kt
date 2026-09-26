package com.yass.vintageplayer.data

import androidx.compose.runtime.Immutable

/**
 * CONTRACT (Phase 0) — shared by every module. Do not change signatures without the lead.
 *
 * A local audio file discovered through MediaStore.
 */
@Immutable
data class Track(
    val id: Long,             // MediaStore.Audio.Media._ID
    val uri: String,          // content://media/external/audio/media/<id>
    val title: String,
    val artist: String,       // "Unknown artist" when MediaStore reports <unknown>
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val relativePath: String, // e.g. "Music/Juniper Lane/" (MediaStore RELATIVE_PATH)
    val dateAddedSec: Long,
)

/** Persisted user preferences. Defaults mirror the design. */
@Immutable
data class AppSettings(
    val dark: Boolean = true,
    val accent: Long = 0xFF27B4E6,      // ARGB; one of Accents.ALL
    val volume: Float = 0.72f,          // 0f..1f, applied as player volume (not system volume)
    val shuffle: Boolean = false,
    val repeatOne: Boolean = false,
    val folder: String? = null,         // RELATIVE_PATH prefix filter, null = all audio
    val lastTrackId: Long? = null,
    val lastPositionMs: Long = 0L,
)

object Accents {
    const val BLUE = 0xFF27B4E6
    const val AMBER = 0xFFF0A330
    const val RED = 0xFFE5544B
    const val GREEN = 0xFF7CC957
    val ALL = listOf(BLUE to "BLUE", AMBER to "AMBER", RED to "RED", GREEN to "GREEN")
}
