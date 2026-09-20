package com.rodrigos01.aipodcasts.ui.screens.wizard

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodrigos01.aipodcasts.R
import com.rodrigos01.aipodcasts.data.model.PodcastOption
import com.rodrigos01.aipodcasts.ui.components.ExpressiveTopAppBar
import com.rodrigos01.aipodcasts.ui.components.VoiceChip
import com.rodrigos01.aipodcasts.ui.theme.ExpressiveShapes

@Composable
fun PodcastWizardScreen(
    onNavigateBack: () -> Unit,
    onPodcastCreated: (String) -> Unit,
    viewModel: PodcastWizardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.createdPodcast) {
        uiState.createdPodcast?.let { podcast ->
            onPodcastCreated(podcast.id)
        }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.wizard_podcast_title),
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
                // Step 1: Prompt Input
                Text(
                    text = stringResource(R.string.wizard_step_prompt_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.wizard_step_prompt_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = uiState.prompt,
                    onValueChange = { viewModel.onPromptChanged(it) },
                    label = { Text(stringResource(R.string.wizard_prompt_label)) },
                    placeholder = { Text(stringResource(R.string.wizard_prompt_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    shape = ExpressiveShapes.small,
                    enabled = !uiState.isGenerating
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.sourceMaterial,
                    onValueChange = { viewModel.onSourceMaterialChanged(it) },
                    label = { Text(stringResource(R.string.wizard_source_material_label)) },
                    placeholder = { Text(stringResource(R.string.wizard_source_material_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = ExpressiveShapes.small,
                    enabled = !uiState.isGenerating
                )

                if (uiState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = { viewModel.generateOptions() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = ExpressiveShapes.medium,
                    enabled = !uiState.isGenerating
                ) {
                    if (uiState.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.wizard_generating_options))
                    } else {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.wizard_generate_options_button),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Step 2: Review, Revise & Confirm
                Text(
                    text = stringResource(R.string.wizard_step_options_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.wizard_step_options_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Option selector tabs (3 Options)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.options.indices.forEach { index ->
                        val isSelected = (index == uiState.selectedOptionIndex)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onSelectOption(index) },
                            label = {
                                Text(
                                    text = stringResource(R.string.wizard_option_badge, index + 1),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = ExpressiveShapes.small
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Display selected concept card
                if (uiState.options.isNotEmpty() && uiState.selectedOptionIndex in uiState.options.indices) {
                    val activeOption = uiState.options[uiState.selectedOptionIndex]
                    OptionDetailCard(
                        option = activeOption,
                        isRevising = uiState.isRevising,
                        onApplyPredictedChange = { change -> viewModel.applyRevision(instructionOverride = change) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Revise Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveShapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.wizard_revise_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.revisionInstruction,
                            onValueChange = { viewModel.onRevisionInstructionChanged(it) },
                            placeholder = { Text(stringResource(R.string.wizard_revise_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveShapes.small,
                            enabled = !uiState.isRevising && !uiState.isCreating
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = uiState.reviseTargetSelectedOnly,
                                onClick = { viewModel.onReviseTargetChanged(true) },
                                label = { Text(stringResource(R.string.wizard_revise_target_selected)) },
                                shape = ExpressiveShapes.extraSmall
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilterChip(
                                selected = !uiState.reviseTargetSelectedOnly,
                                onClick = { viewModel.onReviseTargetChanged(false) },
                                label = { Text(stringResource(R.string.wizard_revise_target_all)) },
                                shape = ExpressiveShapes.extraSmall
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { viewModel.applyRevision() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveShapes.small,
                            enabled = uiState.revisionInstruction.isNotBlank() && !uiState.isRevising && !uiState.isCreating
                        ) {
                            if (uiState.isRevising) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.wizard_revising_options))
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.wizard_revise_button))
                            }
                        }
                    }
                }

                if (uiState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Confirm and Create
                Button(
                    onClick = { viewModel.confirmAndCreatePodcast() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = ExpressiveShapes.medium,
                    enabled = !uiState.isCreating && !uiState.isRevising
                ) {
                    if (uiState.isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.wizard_confirm_button),
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
fun OptionDetailCard(
    option: PodcastOption,
    isRevising: Boolean = false,
    onApplyPredictedChange: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = option.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (option.hosts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.wizard_option_hosts_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    option.hosts.forEach { host ->
                        VoiceChip(voiceName = "${host.name} (${host.voice})", persona = host.persona)
                    }
                }
            }

            if (option.structure.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.wizard_option_structure_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = option.structure,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (option.predictedChanges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.wizard_option_predicted_changes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    option.predictedChanges.forEach { change ->
                        OutlinedButton(
                            onClick = { onApplyPredictedChange(change) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveShapes.small,
                            enabled = !isRevising
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = change,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
