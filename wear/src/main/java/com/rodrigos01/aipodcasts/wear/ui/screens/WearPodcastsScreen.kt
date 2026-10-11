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
import com.rodrigos01.aipodcasts.wear.data.WearPodcast
import com.rodrigos01.aipodcasts.wear.data.WearPodcastRepository
import com.rodrigos01.aipodcasts.wear.ui.LoadState
import com.rodrigos01.aipodcasts.wear.ui.collectLoadState

@Composable
fun WearPodcastsScreen(
    repository: WearPodcastRepository,
    onPodcastClick: (WearPodcast) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by collectLoadState(key = repository) { repository.podcastsFlow() }

    when (val current = state) {
        is LoadState.Loading -> StatusMessage("Loading…", modifier)
        is LoadState.Error -> StatusMessage(current.message, modifier)
        is LoadState.Success -> {
            if (current.value.isEmpty()) {
                StatusMessage("No podcasts yet. Create one on your phone.", modifier)
                return
            }
            val listState = rememberScalingLazyListState()
            ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = contentPadding
                ) {
                    item { ListHeader { Text("Your podcasts") } }
                    items(current.value, key = { it.id }) { podcast ->
                        Button(onClick = { onPodcastClick(podcast) }, modifier = Modifier.fillMaxWidth()) {
                            Text(podcast.title, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}
