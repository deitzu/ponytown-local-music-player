package dev.deitzu.ptmusic.library

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.provider.OpenableColumns
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
        val out = mutableListOf<Track>()
        resolver.query(
            collection,
            projection,
            MediaStore.Audio.Media.IS_MUSIC + " != 0",
            null,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val itemId = cursor.getLong(id)
                out += Track(
                    id = itemId,
                    title = cursor.getStringOrNull(title).orEmpty().ifBlank { "Unknown title" },
                    artist = cursor.getStringOrNull(artist).orEmpty().ifBlank { "Unknown artist" },
                    album = cursor.getStringOrNull(album).orEmpty().ifBlank { "Unknown album" },
                    durationMs = cursor.getLong(duration),
                    uri = ContentUris.withAppendedId(collection, itemId)
                )
            }
        }
        return out
    }

    fun importUri(uri: android.net.Uri, autoTag: Boolean): Track {
        val display = displayName(uri)
        val fallback = display?.substringBeforeLast('.', display).orEmpty().ifBlank { "Imported track" }
        val retriever = MediaMetadataRetriever()
        var title = fallback
        var artist = ""
        var album = ""
        var genre = ""
        var duration = 0L
        try {
            retriever.setDataSource(resolver, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim().orEmpty().ifBlank { fallback }
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim().orEmpty()
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim().orEmpty()
            genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.trim().orEmpty()
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
        } finally {
            retriever.release()
        }
        val tags = if (autoTag) genre.split(',', ';', '/').map(String::trim).filter(String::isNotBlank).distinct() else emptyList()
        return Track(
            id = stableId(uri),
            title = title,
            artist = artist.ifBlank { "Unknown artist" },
            album = album.ifBlank { "Unknown album" },
            genre = genre,
            durationMs = duration,
            uri = uri,
            tags = tags,
            imported = true
        )
    }

    fun mergeWithStored(live: List<Track>, stored: List<Track>): List<Track> {
        val byUri = stored.associateBy { it.uri.toString() }
        val out = live.map { liveTrack ->
            val saved = byUri[liveTrack.uri.toString()]
            if (saved == null) liveTrack else saved.copy(
                id = liveTrack.id,
                title = saved.title.ifBlank { liveTrack.title },
                artist = saved.artist.ifBlank { liveTrack.artist },
                album = saved.album.ifBlank { liveTrack.album },
                durationMs = if (liveTrack.durationMs > 0) liveTrack.durationMs else saved.durationMs
            )
        }.toMutableList()
        val liveUris = live.map { it.uri.toString() }.toHashSet()
        stored.filter { it.imported && it.uri.toString() !in liveUris }.forEach { out += it }
        return out.distinctBy { it.uri.toString() }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    private fun displayName(uri: android.net.Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

    private fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

    private fun stableId(uri: android.net.Uri): Long =
        -(uri.toString().hashCode().toLong() and 0x7fffffffL).coerceAtLeast(1L)
}
