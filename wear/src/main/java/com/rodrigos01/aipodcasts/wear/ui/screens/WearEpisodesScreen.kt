package com.rodrigos01.aipodcasts.wear.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.rodrigos01.aipodcasts.wear.data.WearEpisode
import com.rodrigos01.aipodcasts.wear.data.WearPodcastRepository
import com.rodrigos01.aipodcasts.wear.ui.LoadState
import com.rodrigos01.aipodcasts.wear.ui.collectLoadState

@Composable
fun WearEpisodesScreen(
    podcastId: String,
    podcastTitle: String,
    repository: WearPodcastRepository,
    onEpisodeClick: (WearEpisode) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by collectLoadState(key = podcastId) { repository.episodesFlow(podcastId) }

    when (val current = state) {
        is LoadState.Loading -> StatusMessage("Loading…", modifier)
        is LoadState.Error -> StatusMessage(current.message, modifier)
        is LoadState.Success -> {
            val listState = rememberScalingLazyListState()
            ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = contentPadding
                ) {
                    item { ListHeader { Text(podcastTitle, maxLines = 2) } }
                    if (current.value.isEmpty()) {
                        item { Text("No episodes yet") }
                    }
                    items(current.value, key = { it.id }) { episode ->
                        Button(
                            onClick = { onEpisodeClick(episode) },
                            modifier = Modifier.fillMaxWidth(),
                            secondaryLabel = statusLabel(episode)?.let { label -> { Text(label) } }
                        ) {
                            Text(episode.title, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

/** Only surfaces what blocks or qualifies playback; a ready episode needs no badge. */
internal fun statusLabel(episode: WearEpisode): String? = when {
    episode.status.equals(WearEpisode.STATUS_READY, ignoreCase = true) -> null
    episode.status.equals(WearEpisode.STATUS_STREAMABLE, ignoreCase = true) -> "Generating · playable"
    episode.status.equals("failed", ignoreCase = true) -> "Failed"
    else -> "Generating…"
}
