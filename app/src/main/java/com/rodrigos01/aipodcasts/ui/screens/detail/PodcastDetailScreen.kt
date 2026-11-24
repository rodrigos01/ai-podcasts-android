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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
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
import com.rodrigos01.aipodcasts.ui.components.EmptyState
import com.rodrigos01.aipodcasts.ui.components.ExpressiveTopAppBar
import com.rodrigos01.aipodcasts.ui.components.LocalContentPadding
import com.rodrigos01.aipodcasts.ui.components.StatusBadge
import com.rodrigos01.aipodcasts.ui.components.VoiceChip
import com.rodrigos01.aipodcasts.ui.components.plus
import com.rodrigos01.aipodcasts.ui.theme.AIPodcastsTheme
import com.rodrigos01.aipodcasts.ui.theme.ExpressiveShapes

/** How many lines of the show description are visible before "Show more". */
private const val DESCRIPTION_COLLAPSED_LINES = 4

@Composable
fun PodcastDetailScreen(
    podcastId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEpisodeWizard: (String) -> Unit,
    onNavigateToEpisodeDetail: (String, String) -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: PodcastDetailViewModel = viewModel(factory = PodcastDetailViewModel.provideFactory(podcastId))
) {
    val uiState by viewModel.uiState.collectAsState()

    PodcastDetailScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToEpisodeWizard = { onNavigateToEpisodeWizard(podcastId) },
        onNavigateToEpisodeDetail = { episodeId -> onNavigateToEpisodeDetail(podcastId, episodeId) },
        onNavigateToEdit = { onNavigateToEdit(podcastId) },
        onPromptDeleteEpisode = { viewModel.promptDeleteEpisode(it) },
        onDismissDeleteEpisodeDialog = { viewModel.dismissDeleteEpisodeDialog() },
        onConfirmDeleteEpisode = { viewModel.confirmDeleteEpisode() }
    )
}

@Composable
private fun PodcastDetailScreen(
    uiState: PodcastDetailUiState,
    onNavigateBack: () -> Unit,
    onNavigateToEpisodeWizard: () -> Unit,
    onNavigateToEpisodeDetail: (String) -> Unit,
    onNavigateToEdit: () -> Unit,
    onPromptDeleteEpisode: (Episode) -> Unit,
    onDismissDeleteEpisodeDialog: () -> Unit,
    onConfirmDeleteEpisode: () -> Unit
) {
    val contentPadding = LocalContentPadding.current
    val podcast = uiState.podcast
    val listState = rememberLazyListState()
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }

    // The show title is the first list item, shown at full size. As it scrolls away the app
    // bar's own (single-line) title fades in, so the bar reads as expanded -> collapsed.
    var titleHeightPx by remember { mutableIntStateOf(0) }
    val barTitleAlpha by remember(podcast) {
        derivedStateOf {
            when {
                podcast == null -> 1f
                listState.firstVisibleItemIndex > 0 -> 1f
                titleHeightPx <= 0 -> 0f
                else -> (listState.firstVisibleItemScrollOffset / titleHeightPx.toFloat()).coerceIn(0f, 1f)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveTopAppBar(
                title = podcast?.title ?: stringResource(R.string.podcast_detail_title),
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                titleAlpha = barTitleAlpha,
                actions = {
                    if (podcast != null) {
                        IconButton(onClick = onNavigateToEdit) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.action_edit)
                            )
                        }
                    }
                }
            )

            if (podcast == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (uiState.isLoading) CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = contentPadding + PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 0.dp,
                        // Clear the floating "new episode" button.
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "title") {
                        Text(
                            text = podcast.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                                .onSizeChanged { titleHeightPx = it.height }
                        )
                    }

                    item(key = "description") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = podcast.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = if (detailsExpanded) Int.MAX_VALUE else DESCRIPTION_COLLAPSED_LINES,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                                    Text(
                                        stringResource(
                                            if (detailsExpanded) R.string.podcast_detail_show_less
                                            else R.string.podcast_detail_show_more
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (detailsExpanded) {
                        aboutShowItems(podcast)
                    }

                    item(key = "episodes_header") {
                        Text(
                            text = stringResource(R.string.podcast_detail_episodes_header),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    if (uiState.episodes.isEmpty()) {
                        item(key = "episodes_empty") {
                            EmptyState(
                                icon = Icons.Default.GraphicEq,
                                title = stringResource(R.string.podcast_detail_no_episodes),
                                description = "",
                                actionButtonText = stringResource(R.string.podcast_detail_new_episode_fab),
                                onActionClick = onNavigateToEpisodeWizard
                            )
                        }
                    } else {
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

/** The rest of the "about show" content, shown under the description once expanded. */
private fun androidx.compose.foundation.lazy.LazyListScope.aboutShowItems(podcast: Podcast) {
    item(key = "about_hosts_header") {
        Text(
            text = stringResource(R.string.podcast_detail_hosts_header),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
    items(podcast.hosts, key = { "host_${it.id}_${it.name}" }) { host ->
        HostDetailCard(host = host)
    }

    if (podcast.structure.isNotBlank()) {
        item(key = "about_structure") {
            AboutTextSection(
                header = stringResource(R.string.podcast_detail_structure_header),
                body = podcast.structure
            )
        }
    }

    if (!podcast.extraInstructions.isNullOrBlank()) {
        item(key = "about_extra_instructions") {
            AboutTextSection(
                header = stringResource(R.string.podcast_extra_instructions_label),
                body = podcast.extraInstructions
            )
        }
    }
}

@Composable
private fun AboutTextSection(header: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(
            text = header,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
                StatusBadge(status = episode.status)
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

@Preview(showSystemUi = true, name = "Podcast Detail")
@Composable
fun PodcastDetailPreview() {
    val samplePodcast = Podcast(
        id = "1",
        title = "AI in the Real World",
        description = "A deep dive into how AI is changing our daily lives, from medicine and finance to " +
            "the way we work, learn and create. Every week we unpack one idea with a new guest, " +
            "separating the hype from what is actually shipping today.",
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
                episodes = sampleEpisodes
            ),
            onNavigateBack = {},
            onNavigateToEpisodeWizard = {},
            onNavigateToEpisodeDetail = {},
            onNavigateToEdit = {},
            onPromptDeleteEpisode = {},
            onDismissDeleteEpisodeDialog = {},
            onConfirmDeleteEpisode = {}
        )
    }
}
