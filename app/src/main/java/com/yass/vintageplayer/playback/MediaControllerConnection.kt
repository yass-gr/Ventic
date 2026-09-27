package com.yass.vintageplayer.playback

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.yass.vintageplayer.data.AppSettings
import com.yass.vintageplayer.data.SettingsStore
import com.yass.vintageplayer.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * [PlayerConnection] backed by a Media3 [MediaController] bound to [PlaybackService].
 *
 * Commands issued before the controller connects are queued and replayed in order.
 * Volume, shuffle, repeat, and the last track/position are persisted through
 * [SettingsStore] so they survive restarts.
 */
class MediaControllerConnection(
    context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsStore,
) : PlayerConnection {

    private val appContext = context.applicationContext

    private val persisted = settings.settings.value
    private val _state = MutableStateFlow(
        PlaybackState(
            volume = currentMediaVolume(context),
            shuffle = persisted.shuffle,
            repeatOne = persisted.repeatOne,
        ),
    )
    override val state: StateFlow<PlaybackState> = _state

    override val levels: StateFlow<AudioLevels> =
        combine(LevelMeterProcessor.levels, _state) { meter, playback ->
            if (playback.isPlaying) meter else AudioLevels.SILENT
        }.stateIn(scope, SharingStarted.Eagerly, AudioLevels.SILENT)

    private val lock = Any()
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = ArrayDeque<(MediaController) -> Unit>()
    private var mediaById: Map<String, Track> = emptyMap()
    private var persistJob: Job? = null

    /** Resolves a track by id when the queue was built by an earlier UI process (service outlived it). */
    var trackResolver: (Long) -> Track? = { null }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val ctrl = synchronized(lock) { controller } ?: return
            if (ctrl !== player) {
                return
            }
            refresh(ctrl)
        }
    }

    override fun positionMs(): Long {
        return synchronized(lock) { controller }?.currentPosition ?: 0L
    }

    override fun connect() {
        synchronized(lock) {
            if (controller != null || connecting) {
                return
            }
            connecting = true
        }
        scope.launch(Dispatchers.Main.immediate) {
            try {
                val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
                val ctrl = MediaController.Builder(appContext, token).buildAsync().await()
                val replay: List<(MediaController) -> Unit>
                synchronized(lock) {
                    controller = ctrl
                    connecting = false
                    replay = pending.toList()
                    pending.clear()
                }
                ctrl.addListener(listener)
                applyPersistedSettings(ctrl, settings.settings.value)
                refresh(ctrl)
                replay.forEach { it(ctrl) }
                startPersistTicker()
            } catch (_: Exception) {
                synchronized(lock) {
                    connecting = false
                }
            }
        }
    }

    override fun release() {
        persistJob?.cancel()
        persistJob = null
        val ctrl = synchronized(lock) {
            pending.clear()
            controller.also { controller = null }
        }
        ctrl?.removeListener(listener)
        ctrl?.release()
    }

    override fun playQueue(tracks: List<Track>, startIndex: Int, shuffle: Boolean) {
        if (tracks.isEmpty()) {
            return
        }
        val items = tracks.map { it.toMediaItem() }
        val index = startIndex.coerceIn(0, tracks.size - 1)
        runWhenConnected { ctrl ->
            mediaById = tracks.associateBy { it.id.toString() }
            ctrl.setMediaItems(items, index, 0L)
            ctrl.shuffleModeEnabled = shuffle
            settings.update { it.copy(shuffle = shuffle) }
            ctrl.prepare()
            ctrl.play()
        }
    }

    override fun togglePlay() {
        runWhenConnected { ctrl ->
            if (ctrl.mediaItemCount == 0) {
                return@runWhenConnected
            }
            if (ctrl.isPlaying) {
                ctrl.pause()
            } else {
                ctrl.play()
            }
        }
    }

    override fun next() {
        runWhenConnected { ctrl ->
            val count = ctrl.mediaItemCount
            if (count == 0) {
                return@runWhenConnected
            }
            if (ctrl.currentMediaItemIndex >= count - 1) {
                ctrl.seekTo(0, 0L)
            } else {
                ctrl.seekToNextMediaItem()
            }
        }
    }

    override fun previous() {
        runWhenConnected { ctrl ->
            if (ctrl.mediaItemCount == 0) {
                return@runWhenConnected
            }
            if (ctrl.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) {
                ctrl.seekTo(0L)
            } else if (ctrl.currentMediaItemIndex == 0) {
                ctrl.seekTo(ctrl.mediaItemCount - 1, 0L)
            } else {
                ctrl.seekToPreviousMediaItem()
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        runWhenConnected { ctrl ->
            ctrl.seekTo(positionMs.coerceAtLeast(0L))
        }
    }

    override fun setShuffle(enabled: Boolean) {
        runWhenConnected { ctrl ->
            ctrl.shuffleModeEnabled = enabled
        }
        settings.update { it.copy(shuffle = enabled) }
    }

    override fun setRepeatOne(enabled: Boolean) {
        runWhenConnected { ctrl ->
            ctrl.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
        settings.update { it.copy(repeatOne = enabled) }
    }

    /** Sets the device media volume; [volume] is 0..1 mapped onto the stream's discrete steps. */
    override fun setVolume(volume: Float) {
        val coerced = volume.coerceIn(0f, 1f)
        runWhenConnected { ctrl ->
            if (!ctrl.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)) {
                return@runWhenConnected
            }
            val info = ctrl.deviceInfo
            val range = info.maxVolume - info.minVolume
            if (range <= 0) {
                return@runWhenConnected
            }
            val step = info.minVolume + (coerced * range).roundToInt()
            if (step != ctrl.deviceVolume) {
                ctrl.setDeviceVolume(step, 0)
            }
        }
    }

    /**
     * Restores the last-played queue position without starting playback. No-op when
     * a queue is already loaded or the last track is not in [tracks].
     */
    fun restore(tracks: List<Track>) {
        if (tracks.isEmpty()) {
            return
        }
        runWhenConnected { ctrl -> refresh(ctrl) }
        val saved = settings.settings.value
        val lastId = saved.lastTrackId ?: return
        val index = tracks.indexOfFirst { it.id == lastId }
        if (index < 0) {
            return
        }
        val position = saved.lastPositionMs.coerceAtLeast(0L)
        runWhenConnected { ctrl ->
            if (ctrl.mediaItemCount != 0) {
                return@runWhenConnected
            }
            mediaById = tracks.associateBy { it.id.toString() }
            ctrl.setMediaItems(tracks.map { it.toMediaItem() }, index, position)
            ctrl.prepare()
        }
    }

    private inline fun runWhenConnected(crossinline block: (MediaController) -> Unit) {
        val ctrl = synchronized(lock) {
            val current = controller
            if (current == null) {
                pending.addLast { block(it) }
            }
            current
        }
        if (ctrl != null) {
            block(ctrl)
        }
    }

    private fun refresh(ctrl: Player) {
        val wasPlaying = _state.value.isPlaying
        val current = ctrl.currentMediaItem?.mediaId?.let { mediaById[it] ?: it.toLongOrNull()?.let(trackResolver) }
        val next = PlaybackState(
            current = current,
            isPlaying = ctrl.isPlaying,
            durationMs = ctrl.duration.coerceAtLeast(0L),
            shuffle = ctrl.shuffleModeEnabled,
            repeatOne = ctrl.repeatMode == Player.REPEAT_MODE_ONE,
            queueIndex = ctrl.currentMediaItemIndex.coerceAtLeast(0),
            queueSize = ctrl.mediaItemCount,
            volume = deviceVolumeFraction(ctrl),
        )
        _state.value = next
        if (wasPlaying && !next.isPlaying) {
            savePosition(ctrl, current)
        }
    }

    private fun savePosition(ctrl: Player, current: Track?) {
        val id = current?.id ?: ctrl.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val position = ctrl.currentPosition.coerceAtLeast(0L)
        settings.update { it.copy(lastTrackId = id, lastPositionMs = position) }
    }

    private fun startPersistTicker() {
        persistJob?.cancel()
        persistJob = scope.launch(Dispatchers.Main.immediate) {
            while (true) {
                delay(PERSIST_INTERVAL_MS)
                val ctrl = synchronized(lock) { controller } ?: break
                if (_state.value.isPlaying) {
                    savePosition(ctrl, _state.value.current)
                }
            }
        }
    }

    private fun applyPersistedSettings(ctrl: MediaController, saved: AppSettings) {
        // Loudness is owned by the device media volume; keep the player's own gain at unity.
        ctrl.volume = 1f
        ctrl.shuffleModeEnabled = saved.shuffle
        ctrl.repeatMode = if (saved.repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    private fun deviceVolumeFraction(ctrl: Player): Float {
        if (!ctrl.isCommandAvailable(Player.COMMAND_GET_DEVICE_VOLUME)) {
            return _state.value.volume
        }
        val info = ctrl.deviceInfo
        val range = info.maxVolume - info.minVolume
        if (range <= 0) {
            return _state.value.volume
        }
        return ((ctrl.deviceVolume - info.minVolume).toFloat() / range).coerceIn(0f, 1f)
    }

    private fun Track.toMediaItem(): MediaItem {
        val artwork = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId,
        )
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artwork)
                    .build(),
            )
            .build()
    }

    companion object {
        /** Device media volume as 0..1, read synchronously so the knob is right before the controller connects. */
        private fun currentMediaVolume(context: Context): Float {
            val audio = context.getSystemService(AudioManager::class.java) ?: return 0f
            val min = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max <= min) return 0f
            return ((audio.getStreamVolume(AudioManager.STREAM_MUSIC) - min).toFloat() / (max - min)).coerceIn(0f, 1f)
        }

        private const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L
        private const val PERSIST_INTERVAL_MS = 10_000L
    }
}
