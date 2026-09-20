package com.rodrigos01.aipodcasts.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.rodrigos01.aipodcasts.R
import com.rodrigos01.aipodcasts.player.PodcastAudioController
import com.rodrigos01.aipodcasts.ui.theme.ExpressiveShapes

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    audioController: PodcastAudioController,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val episode by audioController.currentEpisode.collectAsState()
    val podcastTitle by audioController.currentPodcastTitle.collectAsState()
    val isPlaying by audioController.isPlaying.collectAsState()
    val isBuffering by audioController.isBuffering.collectAsState()
    val positionMs by audioController.currentPositionMs.collectAsState()
    val durationMs by audioController.durationMs.collectAsState()
    val playbackSpeed by audioController.playbackSpeed.collectAsState()
    val generatedAudioSeconds by audioController.generatedAudioSeconds.collectAsState()

    var isUserSeeking by remember { androidx.compose.runtime.mutableStateOf(false) }
    var userSeekPosition by remember { mutableFloatStateOf(0f) }

    if (episode == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Podcast Graphic Artwork Box
            Surface(
                modifier = Modifier
                    .size(200.dp)
                    .padding(vertical = 12.dp),
                shape = ExpressiveShapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Episode Title & Podcast Title
            Text(
                text = episode!!.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = podcastTitle.ifBlank { stringResource(R.string.app_name) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Scrubber Slider / Indeterminate Progress
            val isFullyGenerated = durationMs > 0
            val generatedMs = ((generatedAudioSeconds ?: 0.0) * 1000).toLong()
            // While still generating, scrubbing is limited to audio that already exists.
            val scrubMaxMs = if (isFullyGenerated) durationMs else generatedMs
            val canScrub = scrubMaxMs > 0L

            if (canScrub) {
                Slider(
                    value = (if (isUserSeeking) userSeekPosition else positionMs.toFloat())
                        .coerceIn(0f, scrubMaxMs.toFloat()),
                    onValueChange = {
                        isUserSeeking = true
                        userSeekPosition = it
                    },
                    onValueChangeFinished = {
                        audioController.seekTo(userSeekPosition.toLong())
                        isUserSeeking = false
                    },
                    valueRange = 0f..scrubMaxMs.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp)
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                    strokeCap = StrokeCap.Round
                )
            }

            // Elapsed and Remaining Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTimeMs(if (isUserSeeking && canScrub) userSeekPosition.toLong() else positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when {
                        isFullyGenerated -> formatTimeMs(durationMs)
                        generatedMs > 0L -> stringResource(R.string.player_generated_so_far, formatTimeMs(generatedMs))
                        else -> "--:--"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transport Controls: Rewind 10s, Play/Pause, Forward 30s
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { audioController.seekRelative(-10) },
                    enabled = canScrub,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = stringResource(R.string.player_rewind_10),
                        modifier = Modifier.size(36.dp),
                        tint = if (canScrub) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                Surface(
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    IconButton(
                        onClick = { audioController.togglePlayPause() },
                        modifier = Modifier.size(76.dp)
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { audioController.seekRelative(30) },
                    enabled = canScrub,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward30,
                        contentDescription = stringResource(R.string.player_forward_30),
                        modifier = Modifier.size(36.dp),
                        tint = if (canScrub) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Playback Speed Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                    FilterChip(
                        selected = kotlin.math.abs(playbackSpeed - speed) < 0.05f,
                        onClick = { audioController.setPlaybackSpeed(speed) },
                        label = {
                            Text(
                                text = "${speed}x",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        shape = ExpressiveShapes.extraSmall
                    )
                }
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
