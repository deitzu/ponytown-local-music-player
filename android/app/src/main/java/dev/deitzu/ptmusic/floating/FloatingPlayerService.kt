package dev.deitzu.ptmusic.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.deitzu.ptmusic.MainActivity
import dev.deitzu.ptmusic.audio.PlayerService
import dev.deitzu.ptmusic.lyrics.LrcParser
import dev.deitzu.ptmusic.lyrics.LyricsRepository
import dev.deitzu.ptmusic.model.Track
import dev.deitzu.ptmusic.storage.AppStore
import dev.deitzu.ptmusic.storage.PlayerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

data class FloatingState(
    val title: String = "PT Local Music Player",
    val artist: String = "",
    val playing: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val original: String = "",
    val romanized: String = "",
    val translated: String = "",
    val alpha: Float = 1f
)

class FloatingPlayerService : LifecycleService() {
    private val store by lazy { AppStore(this) }
    private val repo by lazy { LyricsRepository(store) }
    private val _state = MutableStateFlow(FloatingState())
    private var controller: MediaController? = null
    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var layout: WindowManager.LayoutParams? = null
    private var lastInteraction = System.currentTimeMillis()
    private var lyricTrackId: Long? = null
    private var lyricLines = emptyList<dev.deitzu.ptmusic.lyrics.LyricLineBundle>()
    private var executor: java.util.concurrent.ExecutorService? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        startOverlayForeground()
        createOverlay()
        connectController()
        lifecycleScope.launch(Dispatchers.Default) {
            while (true) {
                val c = controller
                val id = c?.currentMediaItem?.mediaId?.toLongOrNull()
                val settings = store.loadSettings()
                if (id != null && id != lyricTrackId) {
                    lyricTrackId = id
                    lyricLines = emptyList()
                    val track = store.findTrack(id) ?: Track(
                        id = id,
                        title = c?.mediaMetadata?.title?.toString().orEmpty().ifBlank { "Unknown title" },
                        artist = c?.mediaMetadata?.artist?.toString().orEmpty(),
                        album = c?.mediaMetadata?.albumTitle?.toString().orEmpty(),
                        uri = c?.currentMediaItem?.localConfiguration?.uri ?: android.net.Uri.EMPTY
                    )
                    launch {
                        lyricLines = runCatching { repo.loadBundle(track, settings).second.lines }
                            .getOrDefault(emptyList())
                    }
                }
                val pos = c?.currentPosition?.coerceAtLeast(0L) ?: 0L
                val dur = c?.duration?.takeIf { it > 0 } ?: 0L
                val line = if (settings.floatingLyrics && settings.lrcMode != 0) {
                    LrcParser.lineAt(lyricLines, pos)
                } else null
                val idle = settings.idleFade * 1000f
                val alpha = if (System.currentTimeMillis() - lastInteraction >= idle) {
                    settings.idleOpacity.coerceIn(0.1f, 1f)
                } else 1f
                _state.value = FloatingState(
                    title = c?.mediaMetadata?.title?.toString().orEmpty().ifBlank { "Nothing playing" },
                    artist = c?.mediaMetadata?.artist?.toString().orEmpty(),
                    playing = c?.isPlaying == true,
                    position = pos,
                    duration = dur,
                    original = line?.original.orEmpty(),
                    romanized = line?.romanized.orEmpty(),
                    translated = line?.translated.orEmpty(),
                    alpha = alpha
                )
                delay(250)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (composeView == null) {
            startOverlayForeground()
            createOverlay()
            connectController()
        }
        touch()
        return START_STICKY
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlayerService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        executor = Executors.newSingleThreadExecutor()
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { controller = it }
                    .onFailure {
                        Toast.makeText(this, "Player connection failed", Toast.LENGTH_SHORT).show()
                    }
            },
            executor!!
        )
    }

    private fun createOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = ComposeView(this)
        view.setViewTreeLifecycleOwner(this)
        val pos = store.loadFloatingPosition()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else WindowManager.LayoutParams.TYPE_PHONE
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
            val settings = store.loadSettings()
            FloatingOverlay(
                state = state,
                settings = settings,
                onPlay = { controller?.let { if (it.isPlaying) it.pause() else it.play() }; touch() },
                onNext = { controller?.seekToNextMediaItem(); touch() },
                onPrevious = { controller?.seekToPreviousMediaItem(); touch() },
                onSeek = { controller?.seekTo(it); touch() },
                onExpand = {
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    touch()
                },
                onMinimize = {
                    store.saveSettings(store.loadSettings().copy(floatingMinimized = !store.loadSettings().floatingMinimized))
                    touch()
                },
                onClose = {
                    store.saveSettings(store.loadSettings().copy(floatingEnabled = false))
                    stopSelf()
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

    private fun touch() { lastInteraction = System.currentTimeMillis() }

    private fun startOverlayForeground() {
        val id = "floating_controls"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(id, "Floating player", NotificationManager.IMPORTANCE_LOW))
        }
        val pending = PendingIntent.getActivity(
            this, 10,
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
        } else startForeground(91, notification)
    }

    override fun onDestroy() {
        runCatching { windowManager?.let { wm -> composeView?.let { wm.removeView(it) } } }
        composeView = null
        controller?.release()
        controller = null
        executor?.shutdownNow()
        executor = null
        windowManager = null
        layout = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.deitzu.ptmusic.FLOATING_START"
        const val ACTION_REFRESH = "dev.deitzu.ptmusic.FLOATING_REFRESH"
        const val ACTION_STOP = "dev.deitzu.ptmusic.FLOATING_STOP"
    }
}

@Composable
private fun FloatingOverlay(
    state: FloatingState,
    settings: PlayerSettings,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onExpand: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Int, Int) -> Unit,
    onDragEnd: () -> Unit
) {
    val accent = listOf(
        Color(0xFF7AA2F7), Color(0xFFC88D75), Color(0xFFBD93F9),
        Color(0xFF88C0D0), Color(0xFFCBA6F7)
    ).getOrElse(settings.theme) { Color(0xFF7AA2F7) }
    val bg = listOf(
        Color(0xDD1A1B26), Color(0xDD211A1E), Color(0xDD282A36),
        Color(0xDD2E3440), Color(0xDD1E1E2E)
    ).getOrElse(settings.theme) { Color(0xDD1A1B26) }

    Surface(
        modifier = Modifier.widthIn(min = 220.dp, max = 320.dp).alpha(state.alpha),
        color = bg,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 8.dp
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.x.toInt(), amount.y.toInt())
                        },
                        onDragEnd = onDragEnd
                    )
                }
            ) {
                Column(Modifier.weight(1f)) {
                    Text(state.title, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.artist, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                }
                IconButton(onClick = onMinimize) { Text(if (settings.floatingMinimized) "□" else "—") }
                IconButton(onClick = onExpand) { Text("↗") }
                IconButton(onClick = onClose) { Text("×") }
            }
            if (!settings.floatingMinimized) {
                if (settings.floatingLyrics && settings.lrcMode != 0 &&
                    (state.original.isNotBlank() || state.romanized.isNotBlank() || state.translated.isNotBlank())
                ) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (settings.lrcStyle == 2) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.55f))
                            .padding(8.dp)
                    ) {
                        if (settings.showOriginal) Text(state.original, color = Color.White, fontSize = settings.lrcFontSize.sp)
                        if (settings.showRomanized) Text(state.romanized, color = Color.White.copy(alpha = 0.85f), fontSize = settings.lrcSubSize.sp)
                        if (settings.showTranslated) Text(state.translated, color = accent, fontSize = settings.lrcSubSize.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = onPrevious) { Text("⏮") }
                    IconButton(onClick = onPlay) { Text(if (state.playing) "Ⅱ" else "▶") }
                    IconButton(onClick = onNext) { Text("⏭") }
                }
                if (state.duration > 0) {
                    Slider(
                        value = (state.position.toFloat() / state.duration).coerceIn(0f, 1f),
                        onValueChange = { onSeek((state.duration * it).toLong()) }
                    )
                }
            }
        }
    }
}
