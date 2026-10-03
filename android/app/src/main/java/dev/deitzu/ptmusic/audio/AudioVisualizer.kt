package dev.deitzu.ptmusic.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.hypot

class AudioVisualizer(private val context: Context) {
    private val _levels = MutableStateFlow(List(16) { 0f })
    val levels: StateFlow<List<Float>> = _levels

    private var visualizer: Visualizer? = null

    fun start(audioSessionId: Int) {
        if (audioSessionId <= 0 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            stop()
            return
        }
        runCatching {
            stop()
            val effect = Visualizer(audioSessionId)
            val range = Visualizer.getCaptureSizeRange()
            effect.captureSize = range[1].coerceAtMost(128)
            effect.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
            effect.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer, waveform: ByteArray, samplingRate: Int) = Unit
                    override fun onFftDataCapture(v: Visualizer, fft: ByteArray, samplingRate: Int) {
                        val next = MutableList(16) { 0f }
                        for (i in next.indices) {
                            val b = (2 + i * 3).coerceAtMost(fft.lastIndex - 1)
                            val re = fft[b].toInt().toDouble()
                            val im = fft[b + 1].toInt().toDouble()
                            next[i] = (hypot(re, im) / 96.0).toFloat().coerceIn(0f, 1f)
                        }
                        _levels.value = next
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                false,
                true
            )
            effect.enabled = true
            visualizer = effect
        }
    }

    fun stop() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        _levels.value = List(16) { 0f }
    }

    fun release() = stop()
}
