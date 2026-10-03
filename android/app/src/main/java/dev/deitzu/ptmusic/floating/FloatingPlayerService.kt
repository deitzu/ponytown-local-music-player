package dev.deitzu.ptmusic.floating

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.deitzu.ptmusic.MainActivity
import dev.deitzu.ptmusic.audio.AudioVisualizer
import dev.deitzu.ptmusic.audio.PlayerService
import dev.deitzu.ptmusic.lyrics.LrcParser
import dev.deitzu.ptmusic.lyrics.LyricsBundle
import dev.deitzu.ptmusic.lyrics.LyricsCandidate
import dev.deitzu.ptmusic.lyrics.LyricsRepository
import dev.deitzu.ptmusic.model.Track
import dev.deitzu.ptmusic.storage.AppStore
import dev.deitzu.ptmusic.storage.PlayerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class FloatingState(
    val title: String = "-",
    val artist: String = "-",
    val album: String = "",
    val playing: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val currentId: Long? = null,
    val volume: Float = 1f,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val original: String = "",
    val romanized: String = "",
    val translated: String = "",
    val alpha: Float = 1f
)

data class FloatingTheme(
    val name: String,
    val bg: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val subtext: Color,
    val accent: Color
)

private val FLOATING_THEMES = listOf(
    FloatingTheme("Amber", Color(0xDD141414), Color(0xFF222222), Color(0xFF444444), Color.White, Color(0xFFAAAAAA), Color(0xFFFFB74D)),
    FloatingTheme("Emerald", Color(0xDD141414), Color(0xFF222222), Color(0xFF444444), Color.White, Color(0xFFAAAAAA), Color(0xFF81C784)),
    FloatingTheme("Cyan", Color(0xDD141414), Color(0xFF222222), Color(0xFF444444), Color.White, Color(0xFFAAAAAA), Color(0xFF4DD0E1)),
    FloatingTheme("Violet", Color(0xDD141414), Color(0xFF222222), Color(0xFF444444), Color.White, Color(0xFFAAAAAA), Color(0xFFB39DDB))
)

class FloatingPlayerService : LifecycleService() {
    private lateinit var store: AppStore
    private val repo by lazy { LyricsRepository(store) }
    private val library by lazy { dev.deitzu.ptmusic.library.MusicLibrary(applicationContext) }

    private val _state = MutableStateFlow(FloatingState())
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private lateinit var _settings: MutableStateFlow<PlayerSettings>
    private val _lyrics = MutableStateFlow<LyricsBundle?>(null)

    private var controller: MediaController? = null
    private var composeView: ComposeView? = null
    private var lyricView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var layout: WindowManager.LayoutParams? = null
    private var lyricLayout: WindowManager.LayoutParams? = null
    private var viewTreeOwner: FloatingViewTreeOwner? = null
    private var lyricViewTreeOwner: FloatingViewTreeOwner? = null
    private var lastInteraction = System.currentTimeMillis()
    private var lyricTrackId: Long? = null

    override fun onCreate() {
        super.onCreate()
        store = AppStore(applicationContext)
        _settings = MutableStateFlow(store.loadSettings())
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        startOverlayForeground()
        refreshTracks()
        createOverlay()
        createLyricOverlay()
        connectController()

        lifecycleScope.launch {
            while (true) {
                val c = controller
                val settings = _settings.value
                val position = c?.currentPosition?.coerceAtLeast(0L) ?: 0L
                val id = c?.currentMediaItem?.mediaId?.toLongOrNull()
                if (id != null && id != lyricTrackId) loadLyricsForCurrent(id)

                val line = _lyrics.value?.lines?.let { LrcParser.lineAt(it, position) }
                val idleMs = (settings.idleFade * 1000f).toLong()
                val alpha = if (System.currentTimeMillis() - lastInteraction >= idleMs) {
                    settings.idleOpacity.coerceIn(0.1f, 1f)
                } else 1f

                _state.value = FloatingState(
                    title = c?.mediaMetadata?.title?.toString().orEmpty().ifBlank { "-" },
                    artist = c?.mediaMetadata?.artist?.toString().orEmpty().ifBlank { "-" },
                    album = c?.mediaMetadata?.albumTitle?.toString().orEmpty(),
                    playing = c?.isPlaying == true,
                    position = position,
                    duration = c?.duration?.takeIf { it > 0 } ?: 0L,
                    currentId = id,
                    volume = c?.volume ?: 1f,
                    shuffle = c?.shuffleModeEnabled == true,
                    repeat = c?.repeatMode ?: Player.REPEAT_MODE_OFF,
                    original = line?.original.orEmpty(),
                    romanized = line?.romanized.orEmpty(),
                    translated = line?.translated.orEmpty(),
                    alpha = alpha
                )
                updateLyricLayout(settings)
                delay(250)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> refreshTracks()
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (composeView == null) {
            startOverlayForeground()
            createOverlay()
            createLyricOverlay()
            connectController()
        }
        touch()
        return START_STICKY
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlayerService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { controller = it }
                    .onFailure {
                        Toast.makeText(this, "Player connection failed", Toast.LENGTH_SHORT).show()
                    }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun refreshTracks() {
        lifecycleScope.launch(Dispatchers.IO) {
            val permission = if (Build.VERSION.SDK_INT >= 33) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            _tracks.value = if (
                ContextCompat.checkSelfPermission(this@FloatingPlayerService, permission) == PackageManager.PERMISSION_GRANTED
            ) {
                runCatching { library.mergeWithStored(library.scan(), store.loadTracks()) }
                    .getOrElse { store.loadTracks().filter { it.imported } }
            } else {
                store.loadTracks().filter { it.imported }
            }
        }
    }

    private fun loadLyricsForCurrent(id: Long) {
        lyricTrackId = id
        lifecycleScope.launch(Dispatchers.IO) {
            val track = store.findTrack(id) ?: return@launch
            val result = runCatching { repo.loadBundle(track, _settings.value) }.getOrNull() ?: return@launch
            store.upsertTrack(result.first)
            _lyrics.value = result.second
            refreshTracks()
        }
    }

    private fun saveTrack(track: Track) {
        lifecycleScope.launch(Dispatchers.IO) {
            store.upsertTrack(track)
            refreshTracks()
            if (_state.value.currentId == track.id) lyricTrackId = null
        }
    }

    private fun deleteTrack(track: Track) {
        if (_state.value.currentId == track.id) controller?.stop()
        lifecycleScope.launch(Dispatchers.IO) {
            store.removeTrack(track.id)
            refreshTracks()
        }
    }

    private fun clearTracks() {
        controller?.stop()
        lifecycleScope.launch(Dispatchers.IO) {
            store.clearTracks()
            _lyrics.value = null
            lyricTrackId = null
            refreshTracks()
        }
    }

    private fun updateSettings(next: PlayerSettings) {
        _settings.value = next
        store.saveSettings(next)
    }

    private fun playTrack(track: Track, queue: List<Track>) {
        val c = controller ?: return
        val items = queue.ifEmpty { listOf(track) }
        val index = items.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        c.setMediaItems(items.map(::mediaItem), index, 0L)
        c.shuffleModeEnabled = _state.value.shuffle
        c.repeatMode = _state.value.repeat
        c.prepare()
        c.play()
        touch()
    }

    private fun adjustOffset(track: Track, delta: Long) {
        saveTrack(track.copy(lrcOffsetMs = (track.lrcOffsetMs + delta).coerceIn(-30_000L, 30_000L)))
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

    private fun createOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = ComposeView(this)
        val owner = FloatingViewTreeOwner()
        viewTreeOwner = owner
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        val pos = store.loadFloatingPosition()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = pos.first
            y = pos.second
        }

        view.setContent {
            val state by _state.collectAsState()
            val tracks by _tracks.collectAsState()
            val settings by _settings.collectAsState()
            FloatingOverlay(
                state = state,
                tracks = tracks,
                settings = settings,
                onSettingsChanged = ::updateSettings,
                onPlay = { controller?.let { if (it.isPlaying) it.pause() else it.play() }; touch() },
                onNext = { controller?.seekToNextMediaItem(); touch() },
                onPrevious = { controller?.seekToPreviousMediaItem(); touch() },
                onSeek = { controller?.seekTo(it); touch() },
                onVolume = { controller?.volume = it; touch() },
                onShuffle = {
                    controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
                    touch()
                },
                onRepeat = {
                    controller?.let {
                        it.repeatMode = when (it.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    }
                    touch()
                },
                onPlayTrack = { playTrack(it, tracks) },
                onSaveTrack = ::saveTrack,
                onDeleteTrack = ::deleteTrack,
                onAddFiles = {
                    startActivity(
                        Intent(this, FloatingFilePickerActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(FloatingFilePickerActivity.EXTRA_MODE, FloatingFilePickerActivity.MODE_AUDIO)
                    )
                },
                onUploadLrc = { id ->
                    startActivity(
                        Intent(this, FloatingFilePickerActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(FloatingFilePickerActivity.EXTRA_MODE, FloatingFilePickerActivity.MODE_LRC)
                            .putExtra(FloatingFilePickerActivity.EXTRA_TRACK_ID, id)
                    )
                },
                onSearchLrc = { query, callback ->
                    lifecycleScope.launch {
                        callback(runCatching { repo.search(query) }.getOrDefault(emptyList()))
                    }
                },
                onAdjustOffset = ::adjustOffset,
                onClear = ::clearTracks,
                onExpand = {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                    touch()
                },
                onClose = {
                    store.saveSettings(store.loadSettings().copy(floatingEnabled = false))
                    stopSelf()
                },
                onMinimize = {
                    updateSettings(store.loadSettings().copy(
                        floatingMinimized = !store.loadSettings().floatingMinimized
                    ))
                    touch()
                },
                onDrag = { dx, dy ->
                    lp.x += dx
                    lp.y += dy
                    runCatching { wm.updateViewLayout(view, lp) }
                    touch()
                },
                onDragEnd = { store.saveFloatingPosition(lp.x, lp.y) }
            )
        }

        runCatching {
            wm.addView(view, lp)
            windowManager = wm
            composeView = view
            layout = lp
        }.onFailure {
            Toast.makeText(this, "Floating window permission unavailable", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun createLyricOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = ComposeView(this)
        val owner = FloatingViewTreeOwner()
        lyricViewTreeOwner = owner
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = subtitleBottomMargin(_settings.value.lrcPosPercent)
        }

        view.setContent {
            val state by _state.collectAsState()
            val settings by _settings.collectAsState()
            FloatingSubtitle(state, settings, FLOATING_THEMES.getOrElse(settings.theme) { FLOATING_THEMES.first() })
        }

        runCatching {
            wm.addView(view, lp)
            lyricView = view
            lyricLayout = lp
        }.onFailure {
            lyricViewTreeOwner?.destroy()
            lyricViewTreeOwner = null
        }
    }

    private fun subtitleBottomMargin(percent: Int): Int =
        (resources.displayMetrics.heightPixels * (percent.coerceIn(5, 50) / 100f)).toInt()

    private fun updateLyricLayout(settings: PlayerSettings) {
        lyricLayout?.let { lp ->
            lp.y = subtitleBottomMargin(settings.lrcPosPercent)
            runCatching {
                lyricView?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(it, lp) }
            }
        }
    }

    private fun touch() {
        lastInteraction = System.currentTimeMillis()
    }

    private fun startOverlayForeground() {
        val id = "floating_controls"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(id, "Floating player", NotificationManager.IMPORTANCE_LOW))
        }
        val pending = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, id)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("PT Local Music Player")
            .setContentText("Floating controls active")
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(91, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(91, notification)
        }
    }

    override fun onDestroy() {
        runCatching { composeView?.disposeComposition() }
        runCatching { lyricView?.disposeComposition() }
        runCatching {
            windowManager?.let { wm ->
                composeView?.let { wm.removeView(it) }
                lyricView?.let { wm.removeView(it) }
            }
        }
        composeView = null
        lyricView = null
        viewTreeOwner?.destroy()
        viewTreeOwner = null
        lyricViewTreeOwner?.destroy()
        lyricViewTreeOwner = null
        controller?.release()
        controller = null
        windowManager = null
        layout = null
        lyricLayout = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.deitzu.ptmusic.FLOATING_START"
        const val ACTION_REFRESH = "dev.deitzu.ptmusic.FLOATING_REFRESH"
        const val ACTION_STOP = "dev.deitzu.ptmusic.FLOATING_STOP"
    }
}

private class FloatingViewTreeOwner : SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

@Composable
private fun FloatingOverlay(
    state: FloatingState,
    tracks: List<Track>,
    settings: PlayerSettings,
    onSettingsChanged: (PlayerSettings) -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Float) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onSaveTrack: (Track) -> Unit,
    onDeleteTrack: (Track) -> Unit,
    onAddFiles: () -> Unit,
    onUploadLrc: (Long) -> Unit,
    onSearchLrc: (String, (List<LyricsCandidate>) -> Unit) -> Unit,
    onAdjustOffset: (Track, Long) -> Unit,
    onClear: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onDrag: (Int, Int) -> Unit,
    onDragEnd: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme = FLOATING_THEMES.getOrElse(settings.theme) { FLOATING_THEMES.first() }
    val visualizer = remember { AudioVisualizer(context) }
    val levels by visualizer.levels.collectAsState()
    val filters = remember { mutableStateListOf<String>() }

    var settingsOpen by remember { mutableStateOf(false) }
    var listOpen by remember { mutableStateOf(false) }
    var tagTrack by remember { mutableStateOf<Track?>(null) }
    var lyricTrack by remember { mutableStateOf<Track?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<LyricsCandidate>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }

    val allTags = tracks.flatMap { it.tags }.distinct().sorted()
    val filteredTracks = tracks.filter {
        when {
            filters.contains("Untagged") -> it.tags.isEmpty()
            filters.isEmpty() -> true
            else -> filters.all(it.tags::contains)
        }
    }

    LaunchedEffect(settings.visualizer) {
        if (
            settings.visualizer &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) {
            visualizer.start(AppStore(context).getAudioSessionId())
        } else {
            visualizer.stop()
        }
    }
    DisposableEffect(Unit) {
        onDispose { visualizer.release() }
    }

    if (settings.floatingMinimized) {
        Box(Modifier.widthIn(min = 120.dp, max = 220.dp).alpha(state.alpha)) {
            Surface(color = theme.bg, shape = RoundedCornerShape(8.dp), tonalElevation = 7.dp) {
                Row(
                    Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, amount ->
                                change.consume()
                                onDrag(amount.x.toInt(), amount.y.toInt())
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(state.title, Modifier.weight(1f), color = theme.accent, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TinyButton(if (state.playing) "Ⅱ" else "▶", onPlay, false, theme)
                    IconButton(onClick = onMinimize, modifier = Modifier.size(25.dp)) { Text("+") }
                    IconButton(onClick = onClose, modifier = Modifier.size(25.dp)) { Text("×") }
                }
            }
        }
        return
    }

    Box(Modifier.width(220.dp).alpha(state.alpha)) {
        Surface(
            color = theme.bg,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 8.dp
        ) {
            Column(Modifier.padding(10.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, amount ->
                                    change.consume()
                                    onDrag(amount.x.toInt(), amount.y.toInt())
                                },
                                onDragEnd = onDragEnd
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🎵 PT Player",
                        Modifier.weight(1f),
                        color = theme.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    IconButton(onClick = { settingsOpen = !settingsOpen }, modifier = Modifier.size(28.dp)) { Text("⚙", fontSize = 13.sp) }
                    IconButton(onClick = onMinimize, modifier = Modifier.size(28.dp)) { Text("_") }
                    IconButton(onClick = onExpand, modifier = Modifier.size(28.dp)) { Text("↗") }
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) { Text("×") }
                }

                if (settingsOpen) {
                    FloatingSettings(settings, theme, onSettingsChanged) { clearConfirm = true }
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(45.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        if (settings.visualizer) {
                            VisualizerBars(levels, theme.accent, Modifier.fillMaxSize().alpha(0.35f))
                        }
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(state.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(state.artist, color = theme.accent, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(Modifier.height(5.dp))

                    if (settings.lrcMode == 2 && settings.floatingLyrics &&
                        (state.original.isNotBlank() || state.romanized.isNotBlank() || state.translated.isNotBlank())
                    ) {
                        LyricCard(state.original, state.romanized, state.translated, settings, theme)
                        if (settings.quickOffset && state.currentId != null) {
                            tracks.firstOrNull { it.id == state.currentId }?.let { track ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { onAdjustOffset(track, -500L) }, contentPadding = PaddingValues(0.dp)) { Text("-") }
                                    Text("%.1fs".format(track.lrcOffsetMs / 1000.0), color = theme.subtext, fontSize = 9.sp)
                                    TextButton(onClick = { onAdjustOffset(track, 500L) }, contentPadding = PaddingValues(0.dp)) { Text("+") }
                                }
                            }
                        }
                    }

                    Text(
                        "${formatDuration(state.position)} / ${formatDuration(state.duration)}",
                        Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        color = theme.subtext,
                        fontSize = 10.sp
                    )
                    Slider(
                        value = if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { if (state.duration > 0) onSeek((state.duration * it).toLong()) }
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TinyButton(if (state.shuffle) "⇄*" else "⇄", onShuffle, state.shuffle, theme)
                        TinyButton("⏮", onPrevious, false, theme)
                        TinyButton(if (state.playing) "Ⅱ" else "▶", onPlay, false, theme)
                        TinyButton("⏭", onNext, false, theme)
                        TinyButton(
                            if (state.repeat == Player.REPEAT_MODE_ONE) "↻1" else "↻",
                            onRepeat,
                            state.repeat != Player.REPEAT_MODE_OFF,
                            theme
                        )
                    }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("🔊", fontSize = 10.sp)
                        Slider(value = state.volume.coerceIn(0f, 1f), onValueChange = onVolume, modifier = Modifier.weight(1f))
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SmallAction("+ Add", onAddFiles, theme)
                        SmallAction(if (listOpen) "▼ List" else "▲ List", { listOpen = !listOpen }, theme)
                    }

                    if (listOpen) {
                        if (allTags.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                (listOf("Untagged") + allTags).forEach { tag ->
                                    val selected = filters.contains(tag)
                                    Text(
                                        if (selected) "[$tag]" else tag,
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) theme.accent else theme.surface)
                                            .clickable {
                                                if (tag == "Untagged") {
                                                    filters.clear()
                                                    if (!selected) filters.add(tag)
                                                } else {
                                                    filters.remove("Untagged")
                                                    if (selected) filters.remove(tag) else filters.add(tag)
                                                }
                                            }
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                        color = if (selected) Color.Black else theme.subtext,
                                        fontSize = 8.sp
                                    )
                                }
                                if (filters.isNotEmpty()) {
                                    Text("Clear", Modifier.clickable { filters.clear() }.padding(3.dp), color = theme.accent, fontSize = 8.sp)
                                }
                            }
                        }

                        LazyColumn(Modifier.fillMaxWidth().height(120.dp)) {
                            items(filteredTracks, key = Track::id) { track ->
                                FloatingTrackRow(
                                    track,
                                    track.id == state.currentId,
                                    theme,
                                    { onPlayTrack(track) },
                                    { tagTrack = track },
                                    {
                                        lyricTrack = track
                                        searchQuery = (track.artist + " " + track.title).trim()
                                        searchResults = emptyList()
                                    },
                                    { onDeleteTrack(track) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (tagTrack != null) {
        TagDialog(tagTrack!!, { tagTrack = null }) {
            onSaveTrack(tagTrack!!.copy(tags = it))
            tagTrack = null
        }
    }

    if (lyricTrack != null) {
        LyricsSearchDialog(
            track = lyricTrack!!,
            query = searchQuery,
            results = searchResults,
            loading = searchLoading,
            onQuery = { searchQuery = it },
            onSearch = {
                searchLoading = true
                onSearchLrc(searchQuery) {
                    searchResults = it
                    searchLoading = false
                }
            },
            onUpload = { onUploadLrc(lyricTrack!!.id) },
            onApply = {
                onSaveTrack(lyricTrack!!.copy(lyrics = it.syncedLyrics, romanizedLyrics = "", translatedLyrics = ""))
                lyricTrack = null
            },
            onDismiss = { lyricTrack = null }
        )
    }

    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text("Clear saved library?") },
            text = { Text("This removes the saved metadata and imported entries. Device audio files are not deleted.") },
            confirmButton = { TextButton(onClick = { onClear(); clearConfirm = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("Cancel") }}
        )
    }
}

@Composable
private fun FloatingSubtitle(state: FloatingState, settings: PlayerSettings, theme: FloatingTheme) {
    if (!settings.floatingLyrics || settings.lrcMode != 1) return
    val hasText = (settings.showOriginal && state.original.isNotBlank()) ||
        (settings.showRomanized && state.romanized.isNotBlank()) ||
        (settings.showTranslated && state.translated.isNotBlank())
    if (!hasText) return

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            color = when (settings.lrcStyle) {
                1 -> Color.Transparent
                2 -> Color.White.copy(alpha = 0.12f)
                else -> Color.Black.copy(alpha = 0.62f)
            },
            shape = when (settings.lrcStyle) {
                2 -> RoundedCornerShape(6.dp)
                else -> CutCornerShape(4.dp)
            },
            tonalElevation = if (settings.lrcStyle == 1) 0.dp else 2.dp
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (settings.showOriginal && state.original.isNotBlank()) {
                    Text(state.original, color = Color.White, fontSize = settings.lrcFontSize.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (settings.showRomanized && state.romanized.isNotBlank()) {
                    Text(state.romanized, color = theme.text.copy(alpha = 0.88f), fontSize = settings.lrcSubSize.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (settings.showTranslated && state.translated.isNotBlank()) {
                    Text(state.translated, color = theme.accent.copy(alpha = 0.92f), fontSize = settings.lrcSubSize.sp, fontStyle = FontStyle.Italic, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun FloatingSettings(settings: PlayerSettings, theme: FloatingTheme, onChange: (PlayerSettings) -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(theme.surface).padding(8.dp)) {
        Text("Settings", color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Choice("Theme", FLOATING_THEMES.map { it.name }, settings.theme.coerceIn(0, 3)) { onChange(settings.copy(theme = it)) }
        Choice("Lrc Mode", listOf("Off", "Overlay", "Embedded"), settings.lrcMode.coerceIn(0, 2)) { onChange(settings.copy(lrcMode = it)) }
        Choice("Lrc Style", listOf("YouTube", "Glow", "Glass"), settings.lrcStyle.coerceIn(0, 2)) { onChange(settings.copy(lrcStyle = it)) }
        SliderSetting("Lrc Pos (Y)", settings.lrcPosPercent.toFloat(), 5f, 50f) { onChange(settings.copy(lrcPosPercent = it.toInt())) }
        SliderSetting("Font Size", settings.lrcFontSize.toFloat(), 12f, 24f) { onChange(settings.copy(lrcFontSize = it.toInt())) }
        SliderSetting("Idle Fade (s)", settings.idleFade, 2f, 10f) { onChange(settings.copy(idleFade = (it * 2).toInt() / 2f)) }
        SliderSetting("Idle Opacity", settings.idleOpacity, 0.1f, 1f) { onChange(settings.copy(idleOpacity = (it * 10).toInt() / 10f)) }
        MiniToggle("Auto-Fetch API", settings.autoFetch) { onChange(settings.copy(autoFetch = it)) }
        MiniToggle("Auto-select LRC", settings.autoSelectLrc) { onChange(settings.copy(autoSelectLrc = it)) }
        MiniToggle("Auto-Tag (ID3)", settings.autoTag) { onChange(settings.copy(autoTag = it)) }
        MiniToggle("Audio Visualizer", settings.visualizer) { onChange(settings.copy(visualizer = it)) }
        MiniToggle("Quick LRC Offset", settings.quickOffset) { onChange(settings.copy(quickOffset = it)) }
        MiniToggle("Toast Notification", settings.toastNotification) { onChange(settings.copy(toastNotification = it)) }
        MiniToggle("Show Original", settings.showOriginal) { onChange(settings.copy(showOriginal = it)) }
        MiniToggle("Show Romanized", settings.showRomanized) { onChange(settings.copy(showRomanized = it)) }
        MiniToggle("Show Translation", settings.showTranslated) { onChange(settings.copy(showTranslated = it)) }
        TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("Danger: Clear All Tracks") }
    }
}

@Composable
private fun FloatingTrackRow(
    track: Track,
    current: Boolean,
    theme: FloatingTheme,
    onPlay: () -> Unit,
    onTags: () -> Unit,
    onLyrics: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(track.title, color = if (current) theme.accent else theme.text, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(track.artist)
                    if (track.tags.isNotEmpty()) append(" • [").append(track.tags.joinToString(", ")).append("]")
                    if (track.lyrics.isNotBlank()) append(" ✓")
                },
                color = theme.subtext,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(formatDuration(track.durationMs), color = theme.subtext, fontSize = 8.sp)
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(25.dp)) { Text("⋮", color = theme.subtext) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play") }, onClick = { menu = false; onPlay() })
                DropdownMenuItem(text = { Text("Edit Tags") }, onClick = { menu = false; onTags() })
                DropdownMenuItem(text = { Text("Lyrics / LRC") }, onClick = { menu = false; onLyrics() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun TagDialog(track: Track, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var value by remember { mutableStateOf(track.tags.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Tags") },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, label = { Text("Comma separated") }) },
        confirmButton = { TextButton(onClick = { onSave(value.split(',').map(String::trim).filter(String::isNotBlank).distinct()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LyricsSearchDialog(
    track: Track,
    query: String,
    results: List<LyricsCandidate>,
    loading: Boolean,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onUpload: () -> Unit,
    onApply: (LyricsCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lyrics / LRC") },
        text = {
            Column(Modifier.widthIn(max = 340.dp)) {
                Text("${track.artist} • ${track.title}", color = Color.Gray, fontSize = 10.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.weight(1f), singleLine = true, label = { Text("LRCLIB search") })
                    TextButton(onClick = onSearch, enabled = !loading) { Text("Search") }
                }
                Button(onClick = onUpload, modifier = Modifier.fillMaxWidth()) { Text("Upload .lrc") }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.fillMaxWidth().height(220.dp)) {
                    items(results) { item ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onApply(item) }) {
                            Column(Modifier.padding(7.dp)) {
                                Text(item.trackName, fontWeight = FontWeight.Bold)
                                Text("${item.artistName} • ${item.albumName} • ${formatSeconds(item.durationSec)}", color = Color.Gray, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun Choice(label: String, options: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Color.LightGray, fontSize = 9.sp)
        Box {
            TextButton(onClick = { open = true }, contentPadding = PaddingValues(0.dp)) { Text(options.getOrElse(selected) { options.first() }, fontSize = 9.sp) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(text = { Text(option, fontSize = 10.sp) }, onClick = { onSelected(index); open = false })
                }
            }
        }
    }
}

@Composable
private fun MiniToggle(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Color.LightGray, fontSize = 9.sp)
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun SliderSetting(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(72.dp), color = Color.LightGray, fontSize = 9.sp)
        Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TinyButton(label: String, onClick: () -> Unit, active: Boolean, theme: FloatingTheme) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(if (active) theme.accent else theme.surface)
    ) {
        Text(label, color = if (active) Color.Black else theme.text, fontSize = 12.sp)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SmallAction(label: String, onClick: () -> Unit, theme: FloatingTheme) {
    TextButton(onClick = onClick, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 0.dp)) {
        Text(label, color = theme.text, fontSize = 10.sp)
    }
}

@Composable
private fun VisualizerBars(levels: List<Float>, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bw = size.width / (levels.size * 1.7f)
        val gap = bw * 0.7f
        levels.forEachIndexed { i, value ->
            val h = (size.height * (0.15f + value * 0.85f)).coerceAtLeast(2f)
            drawRoundRect(
                color = accent,
                topLeft = Offset(i * (bw + gap), size.height - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
    }
}

@Composable
private fun LyricCard(original: String, romanized: String, translated: String, settings: PlayerSettings, theme: FloatingTheme) {
    val bg = when (settings.lrcStyle) {
        0 -> Color.Black.copy(alpha = 0.55f)
        2 -> Color.White.copy(alpha = 0.1f)
        else -> Color.Transparent
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(bg).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (settings.showOriginal && original.isNotBlank()) Text(original, color = Color.White, fontSize = settings.lrcFontSize.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (settings.showRomanized && romanized.isNotBlank()) Text(romanized, color = theme.text.copy(alpha = 0.88f), fontSize = settings.lrcSubSize.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (settings.showTranslated && translated.isNotBlank()) Text(translated, color = theme.accent.copy(alpha = 0.92f), fontSize = settings.lrcSubSize.sp, fontStyle = FontStyle.Italic, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return String.format("%02d:%02d", total / 60L, total % 60L)
}

private fun formatSeconds(sec: Long): String =
    String.format("%02d:%02d", sec / 60L, sec % 60L)
