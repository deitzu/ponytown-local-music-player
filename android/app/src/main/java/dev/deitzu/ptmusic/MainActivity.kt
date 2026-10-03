package dev.deitzu.ptmusic

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import dev.deitzu.ptmusic.model.Track
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicApp(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicApp(vm: MainViewModel) {
    val tracks by vm.tracks.collectAsState()
    val query by vm.query.collectAsState()
    val controller by vm.controller.collectAsState()
    val connected by vm.connected.collectAsState()

    var position by remember { mutableLongStateOf(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (it) vm.refresh()
    }

    LaunchedEffect(Unit) {
        val permissionName =
            if (Build.VERSION.SDK_INT >= 33) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        permissionLauncher.launch(permissionName)
    }

    LaunchedEffect(controller) {
        while (controller != null) {
            position = controller?.currentPosition ?: 0L
            delay(400)
        }
    }

    val filtered = remember(tracks, query) {
        if (query.isBlank()) {
            tracks
        } else {
            tracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
            }
        }
    }

    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("PT Local Music Player") },
                    actions = {
                        IconButton(onClick = vm::refresh) {
                            Text("↻")
                        }
                    }
                )
            },
            bottomBar = {
                MiniPlayer(
                    controller = controller,
                    position = position,
                    onToggle = vm::togglePlayPause,
                    onNext = vm::next,
                    onPrevious = vm::previous,
                    onSeek = vm::seekTo
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search") }
                )

                Spacer(Modifier.height(8.dp))

                when {
                    !connected -> {
                        CircularProgressIndicator(Modifier.padding(16.dp))
                    }

                    filtered.isEmpty() -> {
                        Text(
                            "No local music found.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            items(filtered, key = Track::id) { track ->
                                TrackRow(track) { vm.play(track) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${track.artist} • ${track.album}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(formatDuration(track.durationMs))
        }
    }
}

@Composable
private fun MiniPlayer(
    controller: MediaController?,
    position: Long,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val title = controller?.mediaMetadata?.title?.toString() ?: "Nothing playing"
    val duration = controller?.duration?.takeIf { it > 0 } ?: 0L
    val progress = if (duration > 0) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(tonalElevation = 4.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Slider(
                value = progress,
                onValueChange = { value ->
                    if (duration > 0) onSeek((duration * value).toLong())
                }
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatDuration(position))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onPrevious, enabled = controller != null) {
                        Text("Prev")
                    }
                    Spacer(Modifier.padding(horizontal = 2.dp))
                    Button(onClick = onToggle, enabled = controller != null) {
                        Text(if (controller?.isPlaying == true) "Pause" else "Play")
                    }
                    Spacer(Modifier.padding(horizontal = 2.dp))
                    Button(onClick = onNext, enabled = controller != null) {
                        Text("Next")
                    }
                }

                Text(formatDuration(duration))
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
