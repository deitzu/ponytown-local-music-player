package dev.deitzu.ptmusic.audio

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.deitzu.ptmusic.MainActivity
import dev.deitzu.ptmusic.storage.AppStore

@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val sessionId = runCatching { audioManager.generateAudioSessionId() }.getOrDefault(C.AUDIO_SESSION_ID_UNSET)
        val builder = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setMaxSeekToPreviousPositionMs(0L)
        if (sessionId > 0) builder.setAudioSessionId(sessionId)
        player = builder.build()
        AppStore(this).setAudioSessionId(player?.audioSessionId ?: sessionId)

        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(pending)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        player?.release()
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
