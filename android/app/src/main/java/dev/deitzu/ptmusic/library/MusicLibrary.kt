package dev.deitzu.ptmusic.library

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import dev.deitzu.ptmusic.model.Track

class MusicLibrary(private val resolver: ContentResolver) {

    fun scan(): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )

        return buildList {
            resolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol).orEmpty().ifBlank { "Unknown title" }
                    val artist = cursor.getString(artistCol).orEmpty().ifBlank { "Unknown artist" }
                    val album = cursor.getString(albumCol).orEmpty().ifBlank { "Unknown album" }

                    add(
                        Track(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = cursor.getLong(durationCol),
                            uri = ContentUris.withAppendedId(collection, id)
                        )
                    )
                }
            }
        }
    }
}
