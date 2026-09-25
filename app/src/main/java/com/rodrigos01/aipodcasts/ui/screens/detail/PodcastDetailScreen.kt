package com.rodrigos01.aipodcasts.ui.screens.detail

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodrigos01.aipodcasts.R
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.Host
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.model.Source
import com.rodrigos01.aipodcasts.ui.components.EmptyState
import com.rodrigos01.aipodcasts.ui.components.ExpressiveTopAppBar
import com.rodrigos01.aipodcasts.ui.components.LocalContentPadding
import com.rodrigos01.aipodcasts.ui.components.StatusBadge
import com.rodrigos01.aipodcasts.ui.components.VoiceChip
import com.rodrigos01.aipodcasts.ui.components.plus
import com.rodrigos01.aipodcasts.ui.theme.AIPodcastsTheme
import com.rodrigos01.aipodcasts.ui.theme.ExpressiveShapes

@Composable
fun PodcastDetailScreen(
    podcastId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEpisodeWizard: (String) -> Unit,
    onNavigateToEpisodeDetail: (String, String) -> Unit,
    viewModel: PodcastDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(podcastId) {
        viewModel.loadPodcast(podcastId)
    }

    PodcastDetailScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToEpisodeWizard = { onNavigateToEpisodeWizard(podcastId) },
        onNavigateToEpisodeDetail = { episodeId -> onNavigateToEpisodeDetail(podcastId, episodeId) },
        onTabSelected = { viewModel.selectTab(it) },
        onDeleteSource = { sourceId -> viewModel.deleteSource(podcastId, sourceId) },
        onPromptDeleteEpisode = { viewModel.promptDeleteEpisode(it) },
        onDismissDeleteEpisodeDialog = { viewModel.dismissDeleteEpisodeDialog() },
        onConfirmDeleteEpisode = { viewModel.confirmDeleteEpisode(podcastId) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PodcastDetailScreen(
    uiState: PodcastDetailUiState,
    onNavigateBack: () -> Unit,
    onNavigateToEpisodeWizard: () -> Unit,
    onNavigateToEpisodeDetail: (String) -> Unit,
    onTabSelected: (Int) -> Unit,
    onDeleteSource: (String) -> Unit,
    onPromptDeleteEpisode: (Episode) -> Unit,
    onDismissDeleteEpisodeDialog: () -> Unit,
    onConfirmDeleteEpisode: () -> Unit
) {
    val contentPadding = LocalContentPadding.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveTopAppBar(
                title = uiState.podcast?.title ?: stringResource(R.string.podcast_detail_title),
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
            val tabs = listOf(
                stringResource(R.string.podcast_detail_tab_episodes),
                stringResource(R.string.podcast_detail_tab_sources),
                stringResource(R.string.podcast_detail_tab_about)
            )

            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { onTabSelected(index) },
                        text = {
                            Text(
                                text = label,
                                fontWeight = if (uiState.selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            when {
                uiState.isLoading && uiState.podcast == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.selectedTab == 0 -> {
                    // Episodes Tab
                    if (uiState.episodes.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.GraphicEq,
                            title = stringResource(R.string.podcast_detail_no_episodes),
                            description = "",
                            actionButtonText = stringResource(R.string.podcast_detail_new_episode_fab),
                            onActionClick = onNavigateToEpisodeWizard
                        )
                    } else {
                        LazyColumn(
                            contentPadding = contentPadding + PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.episodes, key = { it.id }) { episode ->
                                EpisodeItemCard(
                                    episode = episode,
                                    onClick = { onNavigateToEpisodeDetail(episode.id) },
                                    onDelete = { onPromptDeleteEpisode(episode) }
                                )
                            }
                        }
                    }
                }
                uiState.selectedTab == 1 -> {
                    // Sources Tab
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.sources_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (uiState.sources.isEmpty()) {
                            EmptyState(
                                icon = Icons.Default.Description,
                                title = stringResource(R.string.podcast_detail_no_sources),
                                description = stringResource(R.string.sources_desc)
                            )
                        } else {
                            LazyColumn(
                                contentPadding = contentPadding + PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(uiState.sources, key = { it.id }) { source ->
                                    SourceItemCard(
                                        source = source,
                                        onDelete = { onDeleteSource(source.id) }
                                    )
                                }
                            }
                        }
                    }
                }
                uiState.selectedTab == 2 -> {
                    // About Show Tab
                    val podcast = uiState.podcast
                    if (podcast != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(contentPadding + PaddingValues(20.dp))
                        ) {
                            Text(
                                text = podcast.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = podcast.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = stringResource(R.string.podcast_detail_hosts_header),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            podcast.hosts.forEach { host ->
                                HostDetailCard(host = host)
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (podcast.structure.isNotBlank()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = stringResource(R.string.podcast_detail_structure_header),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = podcast.structure,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        uiState.episodeToDelete?.let { episode ->
            AlertDialog(
                onDismissRequest = onDismissDeleteEpisodeDialog,
                title = { Text(stringResource(R.string.episode_delete_confirm_title)) },
                text = { Text(stringResource(R.string.episode_delete_confirm, episode.title)) },
                confirmButton = {
                    TextButton(onClick = onConfirmDeleteEpisode) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDeleteEpisodeDialog) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = ExpressiveShapes.large
            )
        }

        if (uiState.selectedTab == 0) {
            FloatingActionButton(
                onClick = onNavigateToEpisodeWizard,
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
                    contentDescription = stringResource(R.string.podcast_detail_new_episode_fab)
                )
            }
        }
    }
}

@Composable
fun EpisodeItemCard(
    episode: Episode,
    onClick: () -> Unit,
    onDelete: () -> Unit = {}
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(status = episode.status)
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (episode.topics.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = episode.topics,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Length: ${episode.length.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )

                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun SourceItemCard(
    source: Source,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${source.contents.length} characters",
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
    }
}

@Composable
fun HostDetailCard(host: Host) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = host.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                VoiceChip(voiceName = host.voice)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = host.persona,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showSystemUi = true, name = "Podcast Detail - Episodes")
@Composable
fun PodcastDetailEpisodesPreview() {
    val samplePodcast = Podcast(
        id = "1",
        title = "AI in the Real World",
        description = "A deep dive into how AI is changing our daily lives.",
        structure = "Interview style",
        hosts = listOf(Host(id = "h1", name = "Sarah Chen", voice = "Nova", persona = "Tech Expert"))
    )
    val sampleEpisodes = listOf(
        Episode(id = "e1", title = "The Future of Medicine", status = "completed", length = "medium", topics = "Health tech, AI diagnostics"),
        Episode(id = "e2", title = "AI in Finance", status = "generating", length = "short", topics = "Stock market, algorithmic trading")
    )

    AIPodcastsTheme {
        PodcastDetailScreen(
            uiState = PodcastDetailUiState(
                podcast = samplePodcast,
                episodes = sampleEpisodes,
                selectedTab = 0
            ),
            onNavigateBack = {},
            onNavigateToEpisodeWizard = {},
            onNavigateToEpisodeDetail = {},
            onTabSelected = {},
            onDeleteSource = {},
            onPromptDeleteEpisode = {},
            onDismissDeleteEpisodeDialog = {},
            onConfirmDeleteEpisode = {}
        )
    }
}

@Preview(showSystemUi = true, name = "Podcast Detail - Sources")
@Composable
fun PodcastDetailSourcesPreview() {
    val samplePodcast = Podcast(id = "1", title = "AI in the Real World", description = "", structure = "")
    val sampleSources = listOf(
        Source(id = "s1", title = "Medical Journal Article", contents = "Content summary..."),
        Source(id = "s2", title = "Finance Report 2023", contents = "Market data...")
    )

    AIPodcastsTheme {
        PodcastDetailScreen(
            uiState = PodcastDetailUiState(
                podcast = samplePodcast,
                sources = sampleSources,
                selectedTab = 1
            ),
            onNavigateBack = {},
            onNavigateToEpisodeWizard = {},
            onNavigateToEpisodeDetail = {},
            onTabSelected = {},
            onDeleteSource = {},
            onPromptDeleteEpisode = {},
            onDismissDeleteEpisodeDialog = {},
            onConfirmDeleteEpisode = {}
        )
    }
}
