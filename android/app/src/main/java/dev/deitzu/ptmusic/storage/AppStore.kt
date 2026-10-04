package dev.deitzu.ptmusic.storage

import android.content.Context
import android.net.Uri
import dev.deitzu.ptmusic.model.Track
import org.json.JSONArray
import org.json.JSONObject

data class PlayerSettings(
    val theme: Int = 0,
    val lrcMode: Int = 1,
    val lrcStyle: Int = 0,
    val lrcPosPercent: Int = 20,
    val lrcFontSize: Int = 16,
    val lrcSubSize: Int = 13,
    val autoFetch: Boolean = true,
    val autoSelectLrc: Boolean = true,
    val visualizer: Boolean = false,
    val quickOffset: Boolean = true,
    val idleFade: Float = 3.5f,
    val idleOpacity: Float = 0.3f,
    val toastNotification: Boolean = true,
    val autoTag: Boolean = true,
    val showOriginal: Boolean = true,
    val showRomanized: Boolean = true,
    val showTranslated: Boolean = true,
    val floatingEnabled: Boolean = false,
    val floatingLyrics: Boolean = true,
    val floatingMinimized: Boolean = false
)

class AppStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pt_local_music_player", Context.MODE_PRIVATE)

    fun loadTracks(): List<Track> {
        val raw = prefs.getString("tracks", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            buildList {
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val ta = o.optJSONArray("tags")
                    val tags = if (ta == null) emptyList() else buildList {
                        for (j in 0 until ta.length()) add(ta.optString(j))
                    }
                    add(
                        Track(
                            id = o.getLong("id"),
                            title = o.optString("title", "Unknown title"),
                            artist = o.optString("artist", "Unknown artist"),
                            album = o.optString("album", "Unknown album"),
                            genre = o.optString("genre", ""),
                            durationMs = o.optLong("durationMs", 0L),
                            uri = Uri.parse(o.getString("uri")),
                            lyrics = o.optString("lyrics", ""),
                            romanizedLyrics = o.optString("romanizedLyrics", ""),
                            translatedLyrics = o.optString("translatedLyrics", ""),
                            lrcOffsetMs = o.optLong("lrcOffsetMs", 0L),
                            tags = tags.filter(String::isNotBlank),
                            imported = o.optBoolean("imported", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveTracks(tracks: List<Track>) {
        val a = JSONArray()
        tracks.forEach { t ->
            val tags = JSONArray()
            t.tags.forEach(tags::put)
            a.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("artist", t.artist)
                    .put("album", t.album)
                    .put("genre", t.genre)
                    .put("durationMs", t.durationMs)
                    .put("uri", t.uri.toString())
                    .put("lyrics", t.lyrics)
                    .put("romanizedLyrics", t.romanizedLyrics)
                    .put("translatedLyrics", t.translatedLyrics)
                    .put("lrcOffsetMs", t.lrcOffsetMs)
                    .put("tags", tags)
                    .put("imported", t.imported)
            )
        }
        prefs.edit().putString("tracks", a.toString()).apply()
    }

    fun upsertTrack(track: Track) {
        val list = loadTracks().toMutableList()
        val i = list.indexOfFirst { it.id == track.id || it.uri == track.uri }
        if (i >= 0) list[i] = track else list += track
        saveTracks(list)
    }

    fun removeTrack(id: Long) {
        saveTracks(loadTracks().filterNot { it.id == id })
    }

    fun clearTracks() {
        prefs.edit().remove("tracks").apply()
    }

    fun findTrack(id: Long): Track? =
        loadTracks().firstOrNull { it.id == id }

    fun loadSettings(): PlayerSettings {
        val raw = prefs.getString("settings", null) ?: return PlayerSettings()
        return runCatching {
            val o = JSONObject(raw)
            PlayerSettings(
                theme = o.optInt("theme", 0),
                lrcMode = o.optInt("lrcMode", 1),
                lrcStyle = o.optInt("lrcStyle", 0),
                lrcPosPercent = o.optInt("lrcPosPercent", 20),
                lrcFontSize = o.optInt("lrcFontSize", 16),
                lrcSubSize = o.optInt("lrcSubSize", 13),
                autoFetch = o.optBoolean("autoFetch", true),
                autoSelectLrc = o.optBoolean("autoSelectLrc", true),
                visualizer = o.optBoolean("visualizer", false),
                quickOffset = o.optBoolean("quickOffset", true),
                idleFade = o.optDouble("idleFade", 3.5).toFloat(),
                idleOpacity = o.optDouble("idleOpacity", 0.3).toFloat(),
                toastNotification = o.optBoolean("toastNotification", true),
                autoTag = o.optBoolean("autoTag", true),
                showOriginal = o.optBoolean("showOriginal", true),
                showRomanized = o.optBoolean("showRomanized", true),
                showTranslated = o.optBoolean("showTranslated", true),
                floatingEnabled = o.optBoolean("floatingEnabled", false),
                floatingLyrics = o.optBoolean("floatingLyrics", true),
                floatingMinimized = o.optBoolean("floatingMinimized", false)
            )
        }.getOrDefault(PlayerSettings())
    }

    fun saveSettings(s: PlayerSettings) {
        prefs.edit().putString(
            "settings",
            JSONObject()
                .put("theme", s.theme)
                .put("lrcMode", s.lrcMode)
                .put("lrcStyle", s.lrcStyle)
                .put("lrcPosPercent", s.lrcPosPercent)
                .put("lrcFontSize", s.lrcFontSize)
                .put("lrcSubSize", s.lrcSubSize)
                .put("autoFetch", s.autoFetch)
                .put("autoSelectLrc", s.autoSelectLrc)
                .put("visualizer", s.visualizer)
                .put("quickOffset", s.quickOffset)
                .put("idleFade", s.idleFade)
                .put("idleOpacity", s.idleOpacity)
                .put("toastNotification", s.toastNotification)
                .put("autoTag", s.autoTag)
                .put("showOriginal", s.showOriginal)
                .put("showRomanized", s.showRomanized)
                .put("showTranslated", s.showTranslated)
                .put("floatingEnabled", s.floatingEnabled)
                .put("floatingLyrics", s.floatingLyrics)
                .put("floatingMinimized", s.floatingMinimized)
                .toString()
        ).apply()
    }

    fun setAudioSessionId(id: Int) {
        prefs.edit().putInt("audio_session_id", id).apply()
    }

    fun getAudioSessionId(): Int =
        prefs.getInt("audio_session_id", 0)

    fun saveFloatingPosition(x: Int, y: Int) {
        prefs.edit().putInt("float_x", x).putInt("float_y", y).apply()
    }

    fun loadFloatingPosition(): Pair<Int, Int> =
        Pair(prefs.getInt("float_x", 24), prefs.getInt("float_y", 96))
}
