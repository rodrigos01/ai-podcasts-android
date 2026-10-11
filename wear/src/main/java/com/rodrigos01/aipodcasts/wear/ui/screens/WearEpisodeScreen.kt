package com.rodrigos01.aipodcasts.wear.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.rodrigos01.aipodcasts.sync.SyncedPositionStore
import com.rodrigos01.aipodcasts.wear.data.WearEpisode
import com.rodrigos01.aipodcasts.wear.data.WearPodcastRepository
import com.rodrigos01.aipodcasts.wear.player.WearAudioController
import com.rodrigos01.aipodcasts.wear.ui.LoadState
import com.rodrigos01.aipodcasts.wear.ui.collectLoadState

/**
 * Shown when an episode is tapped. Full-width filled "Play" (from the start) and, when there is
 * saved progress, "Resume" buttons, with the episode's description below them.
 */
@UnstableApi
@Composable
fun WearEpisodeScreen(
    podcastId: String,
    episodeId: String,
    podcastTitle: String,
    repository: WearPodcastRepository,
    positions: SyncedPositionStore,
    audioController: WearAudioController,
    onStartPlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by collectLoadState(key = episodeId) { repository.episodeFlow(podcastId, episodeId) }

    // While this episode is the active one the live position wins; otherwise what was saved.
    val activeEpisode by audioController.currentEpisode.collectAsState()
    val activePositionMs by audioController.positionMs.collectAsState()
    val isPlaying by audioController.isPlaying.collectAsState()
    var savedPositionMs by remember { mutableLongStateOf(positions.getPositionMs(episodeId)) }
    LaunchedEffect(episodeId, activeEpisode?.id, isPlaying) {
        savedPositionMs = positions.getPositionMs(episodeId)
    }
    // Progress made on the phone while this screen is open.
    LaunchedEffect(episodeId) {
        positions.remoteUpdates.collect { updated ->
            if (updated == episodeId) savedPositionMs = positions.getPositionMs(episodeId)
        }
    }

    when (val current = state) {
        is LoadState.Loading -> StatusMessage("Loading…", modifier)
        is LoadState.Error -> StatusMessage(current.message, modifier)
        is LoadState.Success -> {
            val episode = current.value
            if (episode == null) {
                StatusMessage("Episode not found", modifier)
                return
            }
            val positionMs = if (activeEpisode?.id == episode.id) activePositionMs else savedPositionMs
            val listState = rememberScalingLazyListState()

            ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = contentPadding
                ) {
                    item { ListHeader { Text(episode.title, maxLines = 3) } }

                    item {
                        Button(
                            onClick = {
                                audioController.play(podcastId, podcastTitle, episode, fromBeginning = true)
                                onStartPlayback()
                            },
                            enabled = episode.canPlay,
                            modifier = Modifier.fillMaxWidth(),
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                        ) {
                            Text("Play")
                        }
                    }

                    if (SyncedPositionStore.canResume(positionMs)) {
                        item {
                            Button(
                                onClick = {
                                    audioController.play(podcastId, podcastTitle, episode, fromBeginning = false)
                                    onStartPlayback()
                                },
                                enabled = episode.canPlay,
                                modifier = Modifier.fillMaxWidth(),
                                icon = { Icon(Icons.Default.Replay, contentDescription = null) },
                                secondaryLabel = { Text(formatTimeMs(positionMs)) }
                            ) {
                                Text("Resume")
                            }
                        }
                    }

                    if (!episode.canPlay) {
                        item { Text(notPlayableMessage(episode), textAlign = TextAlign.Center) }
                    }

                    if (episode.description.isNotBlank()) {
                        item {
                            Text(
                                text = episode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun notPlayableMessage(episode: WearEpisode): String =
    if (episode.status.equals("failed", ignoreCase = true)) "Generation failed" else "Still generating…"
