package dev.deitzu.ptmusic.lyrics

import dev.deitzu.ptmusic.model.Track
import dev.deitzu.ptmusic.storage.AppStore
import dev.deitzu.ptmusic.storage.PlayerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LyricsCandidate(
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Long,
    val syncedLyrics: String
)

data class LyricsBundle(
    val lines: List<LyricLineBundle> = emptyList(),
    val sourceLyrics: String = ""
)

class LyricsRepository(private val store: AppStore) {
    suspend fun loadBundle(track: Track, settings: PlayerSettings): Pair<Track, LyricsBundle> =
        withContext(Dispatchers.IO) {
            var working = track
            if (working.lyrics.isBlank() && settings.autoFetch && settings.autoSelectLrc &&
                working.artist.isNotBlank() && working.title.isNotBlank()
            ) {
                lookup(working)?.let { lyrics ->
                    working = working.copy(
                        lyrics = lyrics,
                        romanizedLyrics = "",
                        translatedLyrics = ""
                    )
                }
            }
            if (working.lyrics.isNotBlank() && hasNonLatin(working.lyrics) &&
                (working.romanizedLyrics.isBlank() ||
                 working.translatedLyrics.isBlank() ||
                 stale(working.lyrics, working.romanizedLyrics))
            ) {
                val pair = enrich(working.lyrics)
                working = working.copy(romanizedLyrics = pair.first, translatedLyrics = pair.second)
            }
            store.upsertTrack(working)
            working to LyricsBundle(
                LrcParser.merge(
                    working.lyrics,
                    working.romanizedLyrics,
                    working.translatedLyrics,
                    working.lrcOffsetMs
                ),
                working.lyrics
            )
        }

    suspend fun search(query: String): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val raw = request(
            "https://lrclib.net/api/search?q=" + URLEncoder.encode(query, "UTF-8")
        ) ?: return@withContext emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return@withContext emptyList()
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val lrc = o.optString("syncedLyrics")
                if (lrc.isBlank()) continue
                add(
                    LyricsCandidate(
                        o.optString("trackName", "Unknown title"),
                        o.optString("artistName", "Unknown artist"),
                        o.optString("albumName", "Single"),
                        o.optDouble("duration", 0.0).toLong(),
                        lrc
                    )
                )
            }
        }
    }

    private fun lookup(track: Track): String? {
        val params = buildList {
            add("artist_name=" + URLEncoder.encode(track.artist, "UTF-8"))
            add("track_name=" + URLEncoder.encode(track.title, "UTF-8"))
            if (track.album.isNotBlank() && !track.album.equals("Unknown album", true)) {
                add("album_name=" + URLEncoder.encode(track.album, "UTF-8"))
            }
            val durationSec = (track.durationMs / 1000L).takeIf { it in 1L..3600L }
            if (durationSec != null) add("duration=$durationSec")
        }.joinToString("&")
        val raw = request("https://lrclib.net/api/get?$params") ?: return null
        return runCatching {
            JSONObject(raw).optString("syncedLyrics").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private suspend fun enrich(lyrics: String): Pair<String, String> = coroutineScope {
        val rx = Regex("""^(\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?\])(.*)$""")
        val rows = lyrics.lineSequence().mapNotNull { raw ->
            val m = rx.matchEntire(raw.trim()) ?: return@mapNotNull null
            m.groupValues[1] to m.groupValues[2].trim()
        }.toList()
        val results = rows.chunked(3).flatMap { chunk ->
            chunk.map { (_, text) -> async { translateLine(text) } }.awaitAll()
        }
        val rom = buildString {
            rows.forEachIndexed { i, r ->
                append(r.first).append(' ').append(results[i].first).append('\n')
            }
        }.trimEnd()
        val tr = buildString {
            rows.forEachIndexed { i, r ->
                append(r.first).append(' ').append(results[i].second).append('\n')
            }
        }.trimEnd()
        rom to tr
    }

    private fun translateLine(source: String): Pair<String, String> {
        if (source.isBlank()) return "" to ""
        val url =
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t&dt=rm&dj=1&q=" +
                URLEncoder.encode(source, "UTF-8")
        val raw = request(url) ?: return "" to source
        runCatching {
            val o = JSONObject(raw)
            val sentences = o.optJSONArray("sentences")
            if (sentences != null) {
                val translation = buildString {
                    for (i in 0 until sentences.length()) {
                        append(sentences.optJSONObject(i)?.optString("trans").orEmpty())
                    }
                }.trim()
                val translit = buildString {
                    for (i in 0 until sentences.length()) {
                        append(sentences.optJSONObject(i)?.optString("src_translit").orEmpty())
                    }
                }.trim()
                return@runCatching (if (valid(source, translit)) translit else "") to
                    translation.ifBlank { source }
            }
            null
        }.getOrNull()?.let { return it }
        return runCatching {
            val root = JSONArray(raw)
            val rows = root.optJSONArray(0) ?: return@runCatching null
            val translation = buildString {
                for (i in 0 until rows.length()) {
                    append(rows.optJSONArray(i)?.optString(0).orEmpty())
                }
            }.trim()
            val translit = buildString {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONArray(i) ?: continue
                    append(row.optString(2).ifBlank { row.optString(3) })
                }
            }.trim()
            (if (valid(source, translit)) translit else "") to translation.ifBlank { source }
        }.getOrNull() ?: ("" to source)
    }

    private fun valid(source: String, value: String): Boolean =
        value.isNotBlank() &&
            !value.trim().equals(source.trim(), true) &&
            value.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN }

    private fun stale(source: String, romanized: String): Boolean {
        if (romanized.isBlank()) return true
        val a = LrcParser.parse(source).map { it.text }
        val b = LrcParser.parse(romanized).map { it.text }
        if (a.isEmpty() || b.isEmpty()) return true
        return a.zip(b).count { it.first.equals(it.second, true) } >= a.size * 0.8
    }

    private fun hasNonLatin(text: String): Boolean =
        text.any {
            Character.isLetter(it) &&
                Character.UnicodeScript.of(it.code) !in setOf(
                    Character.UnicodeScript.LATIN,
                    Character.UnicodeScript.COMMON,
                    Character.UnicodeScript.INHERITED
                )
        }

    private fun request(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.setRequestProperty(
            "User-Agent",
            "PT Local Music Player/0.2.0 (https://github.com/deitzu/ponytown-local-music-player)"
        )
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
