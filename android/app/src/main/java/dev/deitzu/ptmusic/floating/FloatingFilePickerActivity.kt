package dev.deitzu.ptmusic.floating

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import dev.deitzu.ptmusic.library.MusicLibrary
import dev.deitzu.ptmusic.storage.AppStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingFilePickerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_TRACK_ID = "track_id"
        const val MODE_AUDIO = 1
        const val MODE_LRC = 2
    }

    private val audioPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        lifecycleScope.launch(Dispatchers.IO) {
            val store = AppStore(this@FloatingFilePickerActivity)
            val library = MusicLibrary(this@FloatingFilePickerActivity)
            uris.forEach { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    store.upsertTrack(
                        library.importUri(uri, store.loadSettings().autoTag)
                    )
                }
            }
            refreshFloating()
            withContext(Dispatchers.Main) { finish() }
        }
    }

    private val lrcPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }

        val trackId = intent.getLongExtra(EXTRA_TRACK_ID, Long.MIN_VALUE)
        lifecycleScope.launch(Dispatchers.IO) {
            val store = AppStore(this@FloatingFilePickerActivity)
            val text = runCatching {
                contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
            }.getOrDefault("")

            if (trackId != Long.MIN_VALUE && text.isNotBlank()) {
                store.findTrack(trackId)?.let { track ->
                    store.upsertTrack(
                        track.copy(
                            lyrics = text,
                            romanizedLyrics = "",
                            translatedLyrics = ""
                        )
                    )
                }
            }
            refreshFloating()
            withContext(Dispatchers.Main) { finish() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getIntExtra(EXTRA_MODE, MODE_AUDIO)) {
            MODE_LRC -> lrcPicker.launch(arrayOf("text/*", "application/octet-stream"))
            else -> audioPicker.launch(arrayOf("audio/*"))
        }
    }

    private suspend fun refreshFloating() {
        withContext(Dispatchers.Main) {
            runCatching {
                startService(
                    Intent(this@FloatingFilePickerActivity, FloatingPlayerService::class.java)
                        .setAction(FloatingPlayerService.ACTION_REFRESH)
                )
            }
        }
    }
}
