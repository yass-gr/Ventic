package com.yass.vintageplayer.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class MediaStoreLibrary(
    ctx: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) : LibraryRepository {
    private val appCtx = ctx.applicationContext

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    override val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    override val folders: StateFlow<List<String>> = _folders.asStateFlow()

    @Volatile
    private var allTracks: List<Track> = emptyList()

    @Volatile
    private var lastCached: List<Track>? = null

    private val started = AtomicBoolean(false)
    private val scanLock = Mutex()
    private var debounceJob: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scheduleRescan()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            scheduleRescan()
        }
    }

    init {
        appCtx.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        scope.launch {
            settings.settings.map { it.folder }.distinctUntilChanged().collect {
                publish()
            }
        }
    }

    override suspend fun start() {
        if (!started.compareAndSet(false, true)) return
        withContext(Dispatchers.IO) {
            LibraryCache.read(appCtx)?.let { cached ->
                lastCached = cached
                allTracks = cached
                publish()
            }
            scanLock.withLock { rescanInternal() }
        }
    }

    override suspend fun rescan() {
        withContext(Dispatchers.IO) {
            scanLock.withLock { rescanInternal() }
        }
    }

    private fun scheduleRescan() {
        synchronized(this) {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(1500)
                rescan()
            }
        }
    }

    private fun rescanInternal() {
        _isScanning.value = true
        try {
            allTracks = queryTracks()
            publish()
            val snapshot = allTracks
            if (snapshot != lastCached) {
                LibraryCache.write(appCtx, snapshot)
                lastCached = snapshot
            }
        } catch (e: SecurityException) {
            allTracks = emptyList()
            publish()
        } finally {
            _isScanning.value = false
        }
    }

    private fun queryTracks(): List<Track> {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val found = ArrayList<Track>(512)
        appCtx.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val relativePathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val rawArtist = cursor.getString(artistCol)
                found.add(
                    Track(
                        id = id,
                        uri = ContentUris.withAppendedId(uri, id).toString(),
                        title = cursor.getString(titleCol) ?: "Unknown title",
                        artist = if (rawArtist.isNullOrEmpty() || rawArtist == "<unknown>") {
                            "Unknown artist"
                        } else {
                            rawArtist
                        },
                        album = cursor.getString(albumCol) ?: "Unknown album",
                        albumId = cursor.getLong(albumIdCol),
                        durationMs = cursor.getLong(durationCol),
                        relativePath = cursor.getString(relativePathCol) ?: "",
                        dateAddedSec = cursor.getLong(dateAddedCol),
                    ),
                )
            }
        } ?: return allTracks
        return found.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    private fun publish() {
        val base = allTracks
        val folder = settings.settings.value.folder
        _tracks.value = if (folder.isNullOrEmpty()) {
            base
        } else {
            base.filter { it.relativePath.startsWith(folder) }
        }
        val seen = LinkedHashSet<String>()
        for (track in base) {
            topSegment(track.relativePath)?.let { seen.add(it) }
        }
        _folders.value = seen.sorted()
    }

    private fun topSegment(relativePath: String): String? {
        val slash = relativePath.indexOf('/')
        if (slash <= 0) return null
        return relativePath.substring(0, slash + 1)
    }
}
