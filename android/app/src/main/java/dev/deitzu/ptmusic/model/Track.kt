package dev.deitzu.ptmusic.model

import android.net.Uri

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String = "",
    val durationMs: Long = 0L,
    val uri: Uri,
    val lyrics: String = "",
    val romanizedLyrics: String = "",
    val translatedLyrics: String = "",
    val lrcOffsetMs: Long = 0L,
    val tags: List<String> = emptyList(),
    val imported: Boolean = false
)
