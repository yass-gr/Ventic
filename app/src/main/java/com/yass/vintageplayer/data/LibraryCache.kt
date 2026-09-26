package com.yass.vintageplayer.data

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * On-disk binary snapshot of the library so first frame renders before MediaStore is touched.
 * Layout: magic int, version int, count int, then per track
 * (id long, uri utf, title utf, artist utf, album utf, albumId long,
 * durationMs long, relativePath utf, dateAddedSec long).
 */
object LibraryCache {
    private const val MAGIC = 0x56504C31
    private const val VERSION = 1
    private const val FILE_NAME = "library.bin"
    private const val MAX_TRACKS = 100_000

    fun read(ctx: Context): List<Track>? {
        try {
            val file = File(ctx.filesDir, FILE_NAME)
            if (!file.exists()) return null
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                if (input.readInt() != MAGIC) return null
                if (input.readInt() != VERSION) return null
                val count = input.readInt()
                if (count < 0 || count > MAX_TRACKS) return null
                val tracks = ArrayList<Track>(count)
                repeat(count) {
                    tracks.add(
                        Track(
                            id = input.readLong(),
                            uri = input.readUTF(),
                            title = input.readUTF(),
                            artist = input.readUTF(),
                            album = input.readUTF(),
                            albumId = input.readLong(),
                            durationMs = input.readLong(),
                            relativePath = input.readUTF(),
                            dateAddedSec = input.readLong(),
                        ),
                    )
                }
                return tracks
            }
        } catch (_: Exception) {
            return null
        }
    }

    fun write(ctx: Context, tracks: List<Track>) {
        try {
            val tmp = File(ctx.filesDir, "$FILE_NAME.tmp")
            DataOutputStream(BufferedOutputStream(tmp.outputStream())).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(tracks.size)
                for (track in tracks) {
                    output.writeLong(track.id)
                    output.writeUTF(track.uri)
                    output.writeUTF(track.title)
                    output.writeUTF(track.artist)
                    output.writeUTF(track.album)
                    output.writeLong(track.albumId)
                    output.writeLong(track.durationMs)
                    output.writeUTF(track.relativePath)
                    output.writeLong(track.dateAddedSec)
                }
                output.flush()
            }
            val dst = File(ctx.filesDir, FILE_NAME)
            if (!tmp.renameTo(dst)) {
                dst.delete()
                tmp.renameTo(dst)
            }
        } catch (_: Exception) {
            // Cache is best-effort; a failed write must never break playback or scanning.
        }
    }
}
