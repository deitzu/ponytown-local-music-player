package dev.deitzu.ptmusic

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.deitzu.ptmusic.audio.PlayerService
import dev.deitzu.ptmusic.library.MusicLibrary
import dev.deitzu.ptmusic.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val library = MusicLibrary(application.contentResolver)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val controllerExecutor = Executors.newSingleThreadExecutor()

    init {
        connectController()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _tracks.value = library.scan()
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun play(track: Track) {
        val controller = _controller.value ?: return

        val item = MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(track.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .build()
            )
            .build()

        controller.setMediaItem(item)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        _controller.value?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun next() {
        _controller.value?.seekToNextMediaItem()
    }

    fun previous() {
        _controller.value?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        _controller.value?.seekTo(positionMs)
    }

    private fun connectController() {
        val token = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlayerService::class.java)
        )

        val future = MediaController.Builder(getApplication(), token).buildAsync()

        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess {
                        _controller.value = it
                        _connected.value = true
                    }
                    .onFailure {
                        _connected.value = false
                    }
            },
            controllerExecutor
        )
    }

    override fun onCleared() {
        _controller.value?.release()
        controllerExecutor.shutdownNow()
        super.onCleared()
    }
}
