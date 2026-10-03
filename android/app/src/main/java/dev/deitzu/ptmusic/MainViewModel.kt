package dev.deitzu.ptmusic

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.deitzu.ptmusic.audio.PlayerService
import dev.deitzu.ptmusic.floating.FloatingPlayerService
import dev.deitzu.ptmusic.library.MusicLibrary
import dev.deitzu.ptmusic.lyrics.LyricsBundle
import dev.deitzu.ptmusic.lyrics.LyricsCandidate
import dev.deitzu.ptmusic.lyrics.LyricsRepository
import dev.deitzu.ptmusic.model.Track
import dev.deitzu.ptmusic.storage.AppStore
import dev.deitzu.ptmusic.storage.PlayerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val store = AppStore(context)
    private val library = MusicLibrary(context)
    private val lyricsRepository = LyricsRepository(store)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks = _tracks.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _settings = MutableStateFlow(store.loadSettings())
    val settings = _settings.asStateFlow()
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller = _controller.asStateFlow()
    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()
    private val _currentTrackId = MutableStateFlow<Long?>(null)
    val currentTrackId = _currentTrackId.asStateFlow()
    private val _position = MutableStateFlow(0L)
    val position = _position.asStateFlow()
    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()
    private val _playing = MutableStateFlow(false)
    val playing = _playing.asStateFlow()
    private val _shuffle = MutableStateFlow(false)
    val shuffle = _shuffle.asStateFlow()
    private val _repeat = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeat = _repeat.asStateFlow()
    private val _volume = MutableStateFlow(1f)
    val volume = _volume.asStateFlow()
    private val _lyrics = MutableStateFlow<LyricsBundle?>(null)
    val lyrics = _lyrics.asStateFlow()
    private val _audioSessionId = MutableStateFlow(store.getAudioSessionId())
    val audioSessionId = _audioSessionId.asStateFlow()

    private var lastLyricsId: Long? = null

    init {
        connect()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                _tracks.value = store.loadTracks().filter { it.imported }
                return@launch
            }
            _tracks.value = runCatching {
                library.mergeWithStored(library.scan(), store.loadTracks())
            }.getOrElse {
                store.loadTracks().filter { it.imported }
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun addFiles(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                store.upsertTrack(library.importUri(uri, _settings.value.autoTag))
            }
            refresh()
        }
    }

    fun play(track: Track, queue: List<Track>) {
        val c = _controller.value ?: return
        val q = queue.ifEmpty { listOf(track) }
        val index = q.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        c.setMediaItems(q.map(::mediaItem), index, 0L)
        c.shuffleModeEnabled = _shuffle.value
        c.repeatMode = _repeat.value
        c.prepare()
        c.play()
        loadLyrics(track)
    }

    fun togglePlayPause() {
        _controller.value?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() { _controller.value?.seekToNextMediaItem() }
    fun previous() { _controller.value?.seekToPreviousMediaItem() }
    fun seekTo(positionMs: Long) { _controller.value?.seekTo(positionMs.coerceAtLeast(0L)) }

    fun setVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        _volume.value = v
        _controller.value?.volume = v
    }

    fun toggleShuffle() {
        val v = !(_controller.value?.shuffleModeEnabled ?: _shuffle.value)
        _shuffle.value = v
        _controller.value?.shuffleModeEnabled = v
    }

    fun cycleRepeat() {
        val next = when (_repeat.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeat.value = next
        _controller.value?.repeatMode = next
    }

    fun updateTrack(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            store.upsertTrack(track)
            refresh()
            if (_currentTrackId.value == track.id) {
                lastLyricsId = null
                loadLyrics(track)
            }
        }
    }

    fun deleteTrack(track: Track) {
        if (_currentTrackId.value == track.id) _controller.value?.stop()
        viewModelScope.launch(Dispatchers.IO) {
            store.removeTrack(track.id)
            refresh()
        }
    }

    fun clearAll() {
        _controller.value?.stop()
        viewModelScope.launch(Dispatchers.IO) {
            store.clearTracks()
            _lyrics.value = null
            lastLyricsId = null
            refresh()
        }
    }

    fun loadLyrics(track: Track) {
        if (lastLyricsId == track.id && _lyrics.value != null) return
        lastLyricsId = track.id
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                lyricsRepository.loadBundle(track, _settings.value)
            }.getOrNull()
            if (result == null) {
                _lyrics.value = LyricsBundle()
                return@launch
            }
            val updated = result.first
            store.upsertTrack(updated)
            _lyrics.value = result.second
            _tracks.value = library.mergeWithStored(library.scan(), store.loadTracks())
        }
    }

    suspend fun searchLyrics(query: String): List<LyricsCandidate> =
        lyricsRepository.search(query)

    fun applyLyrics(track: Track, candidate: LyricsCandidate) {
        updateTrack(track.copy(lyrics = candidate.syncedLyrics, romanizedLyrics = "", translatedLyrics = ""))
    }

    fun setLyricText(track: Track, text: String) {
        updateTrack(track.copy(lyrics = text, romanizedLyrics = "", translatedLyrics = ""))
    }

    fun adjustOffset(track: Track, deltaMs: Long) {
        updateTrack(track.copy(lrcOffsetMs = (track.lrcOffsetMs + deltaMs).coerceIn(-30_000L, 30_000L)))
    }

    fun updateSettings(transform: (PlayerSettings) -> PlayerSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        store.saveSettings(next)
        if (!next.floatingEnabled) {
            context.stopService(Intent(context, FloatingPlayerService::class.java))
        } else if (Settings.canDrawOverlays(context)) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingPlayerService::class.java)
                    .setAction(FloatingPlayerService.ACTION_REFRESH)
            )
        }
    }

    fun enableFloating(): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        _settings.value = _settings.value.copy(floatingEnabled = true)
        store.saveSettings(_settings.value)
        ContextCompat.startForegroundService(
            context,
            Intent(context, FloatingPlayerService::class.java)
                .setAction(FloatingPlayerService.ACTION_START)
        )
        return true
    }

    fun disableFloating() {
        _settings.value = _settings.value.copy(floatingEnabled = false)
        store.saveSettings(_settings.value)
        context.stopService(Intent(context, FloatingPlayerService::class.java))
    }

    private fun connect() {
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { c ->
                _controller.value = c
                _connected.value = true
                _shuffle.value = c.shuffleModeEnabled
                _repeat.value = c.repeatMode
                _volume.value = c.volume
            }.onFailure { _connected.value = false }
        }, ContextCompat.getMainExecutor(context))

        viewModelScope.launch {
            while (true) {
                val c = _controller.value
                if (c != null) {
                    _position.value = c.currentPosition.coerceAtLeast(0L)
                    _duration.value = c.duration.takeIf { it > 0 } ?: 0L
                    _playing.value = c.isPlaying
                    val id = c.currentMediaItem?.mediaId?.toLongOrNull()
                    if (id != null && id != _currentTrackId.value) {
                        _currentTrackId.value = id
                        store.findTrack(id)?.let { loadLyrics(it) }
                    } else {
                        _currentTrackId.value = id
                    }
                    _shuffle.value = c.shuffleModeEnabled
                    _repeat.value = c.repeatMode
                    _volume.value = c.volume
                    _audioSessionId.value = store.getAudioSessionId()
                }
                delay(250)
            }
        }
    }

    private fun mediaItem(track: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(track.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .build()
            )
            .build()

    override fun onCleared() {
        _controller.value?.release()
        super.onCleared()
    }
}
