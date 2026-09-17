package com.rodrigos01.aipodcasts.ui.screens.episode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodrigos01.aipodcasts.R
import com.rodrigos01.aipodcasts.data.model.EpisodeDraft
import com.rodrigos01.aipodcasts.ui.components.ExpressiveTopAppBar
import com.rodrigos01.aipodcasts.ui.components.VoiceChip
import com.rodrigos01.aipodcasts.ui.theme.ExpressiveShapes

@Composable
fun EpisodeWizardScreen(
    podcastId: String,
    onNavigateBack: () -> Unit,
    onEpisodeConfirmed: (String, String) -> Unit,
    viewModel: EpisodeWizardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(podcastId) {
        viewModel.initWizard(podcastId)
    }

    LaunchedEffect(uiState.confirmedEpisode) {
        uiState.confirmedEpisode?.let { episode ->
            onEpisodeConfirmed(podcastId, episode.id)
        }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.episode_wizard_title),
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            if (uiState.step == 1) {
                // Step 1: Select Sources & Prompt
                Text(
                    text = stringResource(R.string.episode_wizard_step_sources_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.episode_wizard_step_sources_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                if (uiState.availableSources.isEmpty()) {
                    Text(
                        text = stringResource(R.string.podcast_detail_no_sources),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "Available Sources (${uiState.selectedSourceIds.size}/${uiState.availableSources.size} selected)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.availableSources.forEach { source ->
                        val isSelected = uiState.selectedSourceIds.contains(source.id)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { viewModel.toggleSourceSelection(source.id) },
                            shape = ExpressiveShapes.small,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleSourceSelection(source.id) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = source.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${source.contents.length} chars",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = uiState.steeringPrompt,
                    onValueChange = { viewModel.onSteeringPromptChanged(it) },
                    label = { Text(stringResource(R.string.episode_wizard_prompt_label)) },
                    placeholder = { Text(stringResource(R.string.episode_wizard_prompt_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = ExpressiveShapes.small,
                    enabled = !uiState.isDrafting
                )

                if (uiState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.generateDraft(podcastId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = ExpressiveShapes.medium,
                    enabled = !uiState.isDrafting && uiState.selectedSourceIds.isNotEmpty()
                ) {
                    if (uiState.isDrafting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.episode_wizard_generating_draft))
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.episode_wizard_generate_draft_button),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Step 2: Review Draft, Configure Speakers & Confirm
                Text(
                    text = stringResource(R.string.episode_wizard_step_draft_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                uiState.draft?.let { draft ->
                    DraftReviewCard(draft = draft)
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Episode Length Selector
                Text(
                    text = stringResource(R.string.episode_wizard_length_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column {
                    listOf(
                        "short" to R.string.episode_length_short,
                        "medium" to R.string.episode_length_medium,
                        "long" to R.string.episode_length_long
                    ).forEach { (lengthKey, stringResId) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setEpisodeLength(lengthKey) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = uiState.episodeLength == lengthKey,
                                onClick = { viewModel.setEpisodeLength(lengthKey) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(stringResId), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Speaker Selection (Constraint: Exactly 2 speakers)
                val totalSelectedSpeakers = uiState.selectedHostIds.size + uiState.selectedGuests.size
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveShapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        if (totalSelectedSpeakers == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.episode_wizard_participants_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = if (totalSelectedSpeakers == 2) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer,
                                shape = ExpressiveShapes.extraSmall
                            ) {
                                Text(
                                    text = stringResource(R.string.episode_wizard_speakers_count, totalSelectedSpeakers),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = if (totalSelectedSpeakers == 2) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.episode_wizard_participants_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Show Hosts
                        uiState.podcast?.hosts?.forEach { host ->
                            val isSelected = uiState.selectedHostIds.contains(host.id)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.toggleHostSelection(host.id) },
                                label = { Text("Host: ${host.name} (${host.voice})") },
                                leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                shape = ExpressiveShapes.small
                            )
                        }

                        // Guest Speakers from Draft
                        uiState.draft?.guests?.forEach { guest ->
                            val isSelected = uiState.selectedGuests.any { it.name == guest.name }
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.toggleGuestSelection(guest) },
                                label = { Text("Guest: ${guest.name} (${guest.voice}) - ${guest.persona}") },
                                leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                shape = ExpressiveShapes.small
                            )
                        }

                        if (totalSelectedSpeakers != 2) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.episode_wizard_speakers_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Revise Draft Field
                OutlinedTextField(
                    value = uiState.revisionInstruction,
                    onValueChange = { viewModel.onRevisionInstructionChanged(it) },
                    label = { Text(stringResource(R.string.episode_wizard_revise_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveShapes.small,
                    enabled = !uiState.isRevising && !uiState.isConfirming
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { viewModel.applyRevision(podcastId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveShapes.small,
                    enabled = uiState.revisionInstruction.isNotBlank() && !uiState.isRevising && !uiState.isConfirming
                ) {
                    if (uiState.isRevising) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.episode_wizard_revise_button))
                    }
                }

                if (uiState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Confirm & Start Generation Button
                Button(
                    onClick = { viewModel.confirmAndStartGeneration(podcastId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = ExpressiveShapes.medium,
                    enabled = totalSelectedSpeakers == 2 && !uiState.isConfirming && !uiState.isRevising
                ) {
                    if (uiState.isConfirming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.episode_wizard_confirming))
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.episode_wizard_confirm_button),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun DraftReviewCard(draft: EpisodeDraft) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = draft.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = draft.topics,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (draft.productionNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Production Notes:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = draft.productionNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
