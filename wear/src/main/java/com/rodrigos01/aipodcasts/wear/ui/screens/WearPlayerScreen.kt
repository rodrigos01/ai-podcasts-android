package com.rodrigos01.aipodcasts.wear.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.rodrigos01.aipodcasts.wear.player.WearAudioController
import com.rodrigos01.aipodcasts.wear.theme.AIPodcastsWearTheme

private const val SKIP_SECONDS = 10

@UnstableApi
@Composable
fun WearPlayerScreen(audioController: WearAudioController, modifier: Modifier = Modifier) {
    val episode by audioController.currentEpisode.collectAsState()
    val podcastTitle by audioController.podcastTitle.collectAsState()
    val isPlaying by audioController.isPlaying.collectAsState()
    val isBuffering by audioController.isBuffering.collectAsState()
    val positionMs by audioController.positionMs.collectAsState()
    val durationMs by audioController.durationMs.collectAsState()
    val error by audioController.error.collectAsState()
    val pendingSeekMs by audioController.pendingSeekMs.collectAsState()

    WearPlayerScreen(
        episodeTitle = episode?.title.orEmpty(),
        podcastTitle = podcastTitle,
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        positionMs = pendingSeekMs ?: positionMs,
        isSeekPending = pendingSeekMs != null,
        durationMs = durationMs,
        error = error,
        onPlayPauseClick = audioController::togglePlayPause,
        onSkipBackClick = { audioController.seekRelative(-SKIP_SECONDS) },
        onSkipForwardClick = { audioController.seekRelative(SKIP_SECONDS) },
        modifier = modifier
    )
}

@Composable
fun WearPlayerScreen(
    episodeTitle: String,
    podcastTitle: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    isSeekPending: Boolean,
    durationMs: Long,
    error: String?,
    onPlayPauseClick: () -> Unit,
    onSkipBackClick: () -> Unit,
    onSkipForwardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            episodeTitle,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            podcastTitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSkipBackClick) {
                Icon(Icons.Default.Replay10, contentDescription = "Back $SKIP_SECONDS seconds")
            }
            FilledIconButton(onClick = onPlayPauseClick) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
            IconButton(onClick = onSkipForwardClick) {
                Icon(Icons.Default.Forward10, contentDescription = "Forward $SKIP_SECONDS seconds")
            }
        }

        Text(
            when {
                error != null -> error
                isBuffering && !isSeekPending -> "Buffering…"
                durationMs > 0 -> "${formatTimeMs(positionMs)} / ${formatTimeMs(durationMs)}"
                else -> formatTimeMs(positionMs)
            },
            style = MaterialTheme.typography.labelMedium,
            // The skip target, not yet applied: highlighted until the seek actually happens.
            color = if (isSeekPending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun WearPlayerScreenPreview() {
    AIPodcastsWearTheme {
        WearPlayerScreen(
            episodeTitle = "The Origins of Espresso",
            podcastTitle = "Coffee Talk",
            isPlaying = true,
            isBuffering = false,
            positionMs = 131_000,
            isSeekPending = false,
            durationMs = 228_000,
            error = null,
            onPlayPauseClick = {},
            onSkipBackClick = {},
            onSkipForwardClick = {}
        )
    }
}
