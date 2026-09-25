package com.rodrigos01.aipodcasts.ui.screens.home

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodrigos01.aipodcasts.R
import com.rodrigos01.aipodcasts.data.model.Host
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.ui.components.EmptyState
import com.rodrigos01.aipodcasts.ui.components.ExpressiveTopAppBar
import com.rodrigos01.aipodcasts.ui.components.LocalContentPadding
import com.rodrigos01.aipodcasts.ui.components.VoiceChip
import com.rodrigos01.aipodcasts.ui.components.plus
import com.rodrigos01.aipodcasts.ui.theme.AIPodcastsTheme
import com.rodrigos01.aipodcasts.ui.theme.ExpressiveShapes

@Composable
fun HomeScreen(
    onNavigateToWizard: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    HomeScreen(
        uiState = uiState,
        onNavigateToWizard = onNavigateToWizard,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToSettings = onNavigateToSettings,
        onDeletePodcast = { viewModel.promptDeletePodcast(it) },
        onDismissDeleteDialog = { viewModel.dismissDeleteDialog() },
        onConfirmDeletePodcast = { viewModel.confirmDeletePodcast() }
    )
}

@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onNavigateToWizard: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onDeletePodcast: (Podcast) -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onConfirmDeletePodcast: () -> Unit
) {
    val contentPadding = LocalContentPadding.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveTopAppBar(
                title = stringResource(R.string.home_title),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    }
                }
            )

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    uiState.isLoading && uiState.podcasts.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    uiState.podcasts.isEmpty() -> {
                        EmptyState(
                            icon = Icons.Default.Podcasts,
                            title = stringResource(R.string.home_empty_title),
                            description = stringResource(R.string.home_empty_desc),
                            actionButtonText = stringResource(R.string.home_create_podcast_button),
                            onActionClick = onNavigateToWizard
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(uiState.podcasts, key = { it.id }) { podcast ->
                                PodcastCard(
                                    podcast = podcast,
                                    onClick = { onNavigateToDetail(podcast.id) },
                                    onDelete = { onDeletePodcast(podcast) }
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onNavigateToWizard,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp
                ),
            shape = ExpressiveShapes.medium,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.home_create_podcast_button)
            )
        }

        // Delete Confirmation Dialog
        uiState.podcastToDelete?.let { podcast ->
            AlertDialog(
                onDismissRequest = onDismissDeleteDialog,
                title = { Text(stringResource(R.string.home_podcast_delete_confirm_title)) },
                text = { Text(stringResource(R.string.home_podcast_delete_confirm_message, podcast.title)) },
                confirmButton = {
                    TextButton(onClick = onConfirmDeletePodcast) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDeleteDialog) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = ExpressiveShapes.large
            )
        }
    }
}

@Composable
fun PodcastCard(
    podcast: Podcast,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = ExpressiveShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = podcast.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.home_hosts_count, podcast.hosts.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = podcast.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (podcast.hosts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    podcast.hosts.take(2).forEach { host ->
                        VoiceChip(voiceName = "${host.name} (${host.voice})", persona = host.persona)
                    }
                }
            }
        }
    }
}

@Preview(showSystemUi = true, name = "Empty State")
@Composable
fun HomeScreenEmptyPreview() {
    AIPodcastsTheme {
        HomeScreen(
            uiState = HomeUiState(),
            onNavigateToWizard = {},
            onNavigateToDetail = {},
            onNavigateToSettings = {},
            onDeletePodcast = {},
            onDismissDeleteDialog = {},
            onConfirmDeletePodcast = {}
        )
    }
}

@Preview(showSystemUi = true, name = "Populated State")
@Composable
fun HomeScreenPopulatedPreview() {
    val samplePodcasts = listOf(
        Podcast(
            id = "1",
            title = "AI in the Real World",
            description = "A deep dive into how AI is changing our daily lives, from healthcare to finance.",
            structure = "Interview",
            hosts = listOf(
                Host(id = "h1", name = "Sarah Chen", voice = "Nova", persona = "Tech Expert"),
                Host(id = "h2", name = "Mark Davis", voice = "Echo", persona = "Journalist")
            )
        ),
        Podcast(
            id = "2",
            title = "Future Talk",
            description = "Exploring the next century of human evolution and technology.",
            structure = "Discussion",
            hosts = listOf(
                Host(id = "h3", name = "Elena Rodriguez", voice = "Shimmer", persona = "Futurist")
            )
        )
    )

    AIPodcastsTheme {
        HomeScreen(
            uiState = HomeUiState(podcasts = samplePodcasts),
            onNavigateToWizard = {},
            onNavigateToDetail = {},
            onNavigateToSettings = {},
            onDeletePodcast = {},
            onDismissDeleteDialog = {},
            onConfirmDeletePodcast = {}
        )
    }
}
