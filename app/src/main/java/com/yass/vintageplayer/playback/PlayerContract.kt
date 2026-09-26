package com.yass.vintageplayer.playback

import androidx.compose.runtime.Immutable
import com.yass.vintageplayer.data.Track
import kotlinx.coroutines.flow.StateFlow

/** CONTRACT (Phase 0). Implemented by Agent B. */
@Immutable
data class PlaybackState(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatOne: Boolean = false,
    val queueIndex: Int = 0,   // 0-based index of current item in the (unshuffled) queue
    val queueSize: Int = 0,
    val volume: Float = 0.72f,
)

/**
 * Live audio analysis produced by an ExoPlayer AudioProcessor (no RECORD_AUDIO permission needed).
 * `bands` has exactly BAND_COUNT entries in 0f..1f; `level` is overall loudness 0f..1f (drives the VU needle).
 * The same FloatArray instance may be reused between emissions — read it, don't store it.
 */
class AudioLevels(val bands: FloatArray, val level: Float) {
    companion object {
        const val BAND_COUNT = 28
        val SILENT = AudioLevels(FloatArray(BAND_COUNT), 0f)
    }
}

/** CONTRACT (Phase 0). Implemented by Agent B. UI talks to playback ONLY through this. */
interface PlayerConnection {
    val state: StateFlow<PlaybackState>
    /** ~30 fps while playing; SILENT while paused. Consume in the draw phase only. */
    val levels: StateFlow<AudioLevels>
    /** Current position. Cheap; call from a frame loop / draw phase, not from composition. */
    fun positionMs(): Long

    fun connect()          // bind MediaController to PlaybackService (idempotent)
    fun release()

    fun playQueue(tracks: List<Track>, startIndex: Int, shuffle: Boolean)
    fun togglePlay()
    fun next()
    fun previous()         // restart if position > 3s, else previous track (design behaviour)
    fun seekTo(positionMs: Long)
    fun setShuffle(enabled: Boolean)
    fun setRepeatOne(enabled: Boolean)
    fun setVolume(volume: Float)
}
