package com.rodrigos01.aipodcasts.ui.screens.episode

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.R
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.EpisodeGuest
import com.rodrigos01.aipodcasts.ui.components.ExpressiveTopAppBar
import com.rodrigos01.aipodcasts.ui.components.LocalContentPadding
import com.rodrigos01.aipodcasts.ui.components.StatusBadge
import com.rodrigos01.aipodcasts.ui.components.VoiceChip
import com.rodrigos01.aipodcasts.ui.components.plus
import com.rodrigos01.aipodcasts.ui.theme.AIPodcastsTheme
import com.rodrigos01.aipodcasts.ui.theme.ExpressiveShapes

@UnstableApi
@Composable
fun EpisodeDetailScreen(
    podcastId: String,
    episodeId: String,
    onNavigateBack: () -> Unit,
    viewModel: EpisodeDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val audioController = AIPodcastsApplication.instance.audioController
    val activeEpisode by audioController.currentEpisode.collectAsState()
    val activePositionMs by audioController.currentPositionMs.collectAsState()
    val isPlaying by audioController.isPlaying.collectAsState()

    LaunchedEffect(podcastId, episodeId) {
        viewModel.loadEpisode(podcastId, episodeId)
        viewModel.refreshSavedPosition()
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }

    EpisodeDetailScreen(
        uiState = uiState,
        activeEpisodeId = activeEpisode?.id,
        activePositionMs = activePositionMs,
        isPlaying = isPlaying,
        onNavigateBack = onNavigateBack,
        onDeleteClick = { viewModel.promptDelete() },
        onPlayAudio = { forceRestart -> viewModel.playAudio(forceRestart) },
        onRegenerate = { viewModel.regenerate(podcastId, episodeId) },
        onConfirmDelete = { viewModel.confirmDelete(podcastId, episodeId) },
        onDismissDelete = { viewModel.dismissDeleteConfirm() })
}

@UnstableApi
@Composable
private fun EpisodeDetailScreen(
    uiState: EpisodeDetailUiState,
    activeEpisodeId: String?,
    activePositionMs: Long,
    isPlaying: Boolean,
    onNavigateBack: () -> Unit,
    onDeleteClick: () -> Unit,
    onPlayAudio: (Boolean) -> Unit,
    onRegenerate: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit
) {
    val isCurrentActiveEpisode = activeEpisodeId == uiState.episode?.id
    val currentPosition = if (isCurrentActiveEpisode) activePositionMs else uiState.savedPositionMs
    val hasSavedProgress = currentPosition >= 3000L
    val formattedSavedTime = formatTimeMs(currentPosition)

    Column(modifier = Modifier.fillMaxSize()) {
        ExpressiveTopAppBar(
            title = uiState.episode?.title ?: stringResource(R.string.episode_detail_title),
            canNavigateBack = true,
            onNavigateBack = onNavigateBack,
            actions = {
                if (uiState.episode != null) {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete)
                        )
                    }
                }
            }
        )
        val episode = uiState.episode
        if (uiState.isLoading && episode == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LocalContentPadding.current),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (episode != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = LocalContentPadding.current + PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveShapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = episode.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                StatusBadge(status = uiState.status)
                            }

                            if (episode.topics.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = episode.topics,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val isStillGenerating =
                                uiState.status.equals("generating", ignoreCase = true)
                            val isStreamable =
                                uiState.status.equals("streamable", ignoreCase = true)
                            val canPlay =
                                isStreamable || uiState.status.equals("ready", ignoreCase = true)
                            if (isStillGenerating || isStreamable) {
                                Spacer(modifier = Modifier.height(14.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val stage = uiState.progress?.stage ?: "Processing conversation"
                                    Text(
                                        text = stringResource(
                                            R.string.episode_progress_stage, stage
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    val words = uiState.progress?.wordCount
                                    if (words != null && words > 0) {
                                        Text(
                                            text = stringResource(
                                                R.string.episode_progress_words, words
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(
                                        if (isStreamable) R.string.episode_streamable_notice
                                        else R.string.episode_live_stream_notice
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { onPlayAudio(false) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = ExpressiveShapes.medium,
                                enabled = canPlay
                            ) {
                                Icon(
                                    imageVector = if (isCurrentActiveEpisode && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        isCurrentActiveEpisode && isPlaying -> stringResource(R.string.player_pause)
                                        hasSavedProgress -> stringResource(
                                            R.string.episode_resume_button, formattedSavedTime
                                        )

                                        else -> stringResource(R.string.episode_play_stream_button)
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (hasSavedProgress && !(isCurrentActiveEpisode && isPlaying)) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { onPlayAudio(true) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = ExpressiveShapes.medium,
                                    enabled = canPlay
                                ) {
                                    Icon(
                                        Icons.Default.Replay,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.episode_restart_button),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            if (uiState.status.equals("failed", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = onRegenerate,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ExpressiveShapes.small,
                                    enabled = !uiState.isRegenerating
                                ) {
                                    if (uiState.isRegenerating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.episode_regenerate_button))
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        Text(
                            text = "Speakers",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            episode.guests.forEach { guest ->
                                VoiceChip(
                                    voiceName = "${guest.name} (Guest)", persona = guest.persona
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.episode_transcript_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                val transcriptRaw = episode.transcript
                if (transcriptRaw == null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveShapes.small,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Text(
                                text = stringResource(R.string.episode_transcript_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveShapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = transcriptRaw.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.showDeleteConfirm) {
            val title = uiState.episode?.title ?: ""
            AlertDialog(
                onDismissRequest = { if (!uiState.isDeleting) onDismissDelete() },
                        title = { Text(stringResource(R.string.episode_delete_confirm_title)) },
                        text = { Text(stringResource(R.string.episode_delete_confirm, title)) },
                        confirmButton = {
                            TextButton(
                                onClick = onConfirmDelete, enabled = !uiState.isDeleting
                            ) {
                                Text(
                                    stringResource(R.string.action_delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = onDismissDelete, enabled = !uiState.isDeleting
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        },
                        shape = ExpressiveShapes.large
            )
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@UnstableApi
@Preview(showSystemUi = true, name = "Episode Ready")
@Composable
fun EpisodeDetailReadyPreview() {
    val sampleEpisode = Episode(
        id = "e1",
        title = "The Future of Medicine",
        status = "ready",
        topics = "Health tech, AI diagnostics",
        guests = listOf(EpisodeGuest(name = "Dr. Smith", voice = "Echo", persona = "MD"))
    )

    AIPodcastsTheme {
        EpisodeDetailScreen(
            uiState = EpisodeDetailUiState(episode = sampleEpisode, status = "ready"),
            activeEpisodeId = null,
            activePositionMs = 0L,
            isPlaying = false,
            onNavigateBack = {},
            onDeleteClick = {},
            onPlayAudio = {},
            onRegenerate = {},
            onConfirmDelete = {},
            onDismissDelete = {})
    }
}

@UnstableApi
@Preview(showSystemUi = true, name = "Episode Generating")
@Composable
fun EpisodeDetailGeneratingPreview() {
    val sampleEpisode = Episode(
        id = "e1",
        title = "The Future of Medicine",
        status = "generating",
        topics = "Health tech, AI diagnostics"
    )

    AIPodcastsTheme {
        EpisodeDetailScreen(
            uiState = EpisodeDetailUiState(
                episode = sampleEpisode,
                status = "generating",
                progress = com.rodrigos01.aipodcasts.data.model.EpisodeProgress(
                    stage = "Writing script", wordCount = 450
                )
            ),
            activeEpisodeId = null,
            activePositionMs = 0L,
            isPlaying = false,
            onNavigateBack = {},
            onDeleteClick = {},
            onPlayAudio = {},
            onRegenerate = {},
            onConfirmDelete = {},
            onDismissDelete = {})
    }
}
