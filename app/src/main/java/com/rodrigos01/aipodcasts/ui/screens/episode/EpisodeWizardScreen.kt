package com.rodrigos01.aipodcasts.ui.screens.episode

import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.R
import com.rodrigos01.aipodcasts.data.model.EpisodeDraft
import com.rodrigos01.aipodcasts.data.model.EpisodeGuest
import com.rodrigos01.aipodcasts.data.model.EpisodeSuggestion
import com.rodrigos01.aipodcasts.data.model.Host
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.model.Source
import androidx.compose.foundation.layout.PaddingValues
import com.rodrigos01.aipodcasts.ui.components.EditableTextField
import com.rodrigos01.aipodcasts.ui.components.ExpressiveTopAppBar
import com.rodrigos01.aipodcasts.ui.components.LocalContentPadding
import com.rodrigos01.aipodcasts.ui.components.PersonEditDialog
import com.rodrigos01.aipodcasts.ui.components.VoiceChip
import com.rodrigos01.aipodcasts.ui.components.plus
import com.rodrigos01.aipodcasts.ui.theme.AIPodcastsTheme
import com.rodrigos01.aipodcasts.ui.voice.VoiceDesignController
import com.rodrigos01.aipodcasts.ui.theme.ExpressiveShapes
import com.rodrigos01.aipodcasts.util.FileUtils

@Composable
fun EpisodeWizardScreen(
    podcastId: String,
    onNavigateBack: () -> Unit,
    onEpisodeConfirmed: (String, String) -> Unit,
    viewModel: EpisodeWizardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentUserEmail = AIPodcastsApplication.instance.authRepository.currentUser?.email

    val episodePagerState = rememberPagerState(pageCount = { uiState.currentEpisodes.size.coerceAtLeast(1) })

    LaunchedEffect(episodePagerState) {
        snapshotFlow { episodePagerState.currentPage }.collect { page ->
            viewModel.selectEpisodePage(page)
        }
    }

    LaunchedEffect(uiState.selectedSuggestionIndex, uiState.selectedEpisodeIndex) {
        if (episodePagerState.currentPage != uiState.selectedEpisodeIndex) {
            episodePagerState.scrollToPage(uiState.selectedEpisodeIndex)
        }
    }

    val localFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onAddSourceLocalFileSelected(context, podcastId, uri)
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onAddSourceDriveConsentResult(context, result.data)
        } else {
            viewModel.setAddSourceError("Google Drive authorization was cancelled")
        }
    }

    val driveFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
            viewModel.onAddSourceDriveFileSelected(
                context = context,
                podcastId = podcastId,
                uri = result.data!!.data!!,
                accountEmail = currentUserEmail,
                onNeedConsent = { intentSenderRequest ->
                    consentLauncher.launch(intentSenderRequest)
                }
            )
        }
    }

    LaunchedEffect(podcastId) {
        viewModel.initWizard(podcastId)
    }

    LaunchedEffect(uiState.confirmedEpisode) {
        uiState.confirmedEpisode?.let { episode ->
            onEpisodeConfirmed(podcastId, episode.id)
        }
    }

    EpisodeWizardScreen(
        uiState = uiState,
        pagerState = episodePagerState,
        onNavigateBack = onNavigateBack,
        onRemoveSource = { viewModel.toggleSourceSelection(it) },
        onPickLocalFile = { localFilePickerLauncher.launch(arrayOf("application/pdf", "text/plain")) },
        onPickDriveFile = {
            driveFilePickerLauncher.launch(
                FileUtils.createGoogleDrivePickerIntent(context, currentUserEmail)
            )
        },
        onOpenTextSourceDialog = { viewModel.openTextSourceDialog() },
        onDismissTextSourceDialog = { viewModel.dismissTextSourceDialog() },
        onAddSourceTitleChanged = { viewModel.onAddSourceTitleChanged(it) },
        onAddSourceContentChanged = { viewModel.onAddSourceContentChanged(it) },
        onSubmitTextSource = { viewModel.submitTextSource(podcastId) },
        onOpenExistingSourcesDialog = { viewModel.openExistingSourcesDialog() },
        onDismissExistingSourcesDialog = { viewModel.dismissExistingSourcesDialog() },
        onConfirmExistingSources = { viewModel.setSelectedSources(it) },
        onSteeringPromptChanged = { viewModel.onSteeringPromptChanged(it) },
        onSetEpisodeLength = { viewModel.setEpisodeLength(it) },
        onGenerateSuggestions = { viewModel.generateSuggestions(podcastId) },
        onSelectSuggestion = { viewModel.selectSuggestion(it) },
        onRevisionInstructionChanged = { viewModel.onRevisionInstructionChanged(it) },
        onApplyRevision = { page -> viewModel.applyRevision(podcastId, episodeIndexOverride = page) },
        onToggleHostSelection = { viewModel.toggleHostSelection(it) },
        onToggleGuestSelection = { viewModel.toggleGuestSelection(it) },
        onConfirmAndStartGeneration = { viewModel.confirmAndStartGeneration(podcastId) },
        onUpdateDraft = { viewModel.updateSelectedDraft(it) },
        onUpdateGuest = { index, name, persona -> viewModel.updateGuest(index, name, persona) },
        onChooseGuestVoice = { viewModel.chooseGuestVoice(it) },
        voiceDesign = viewModel.voiceDesign
    )
}

@Composable
private fun EpisodeWizardScreen(
    uiState: EpisodeWizardUiState,
    pagerState: PagerState,
    onNavigateBack: () -> Unit,
    onRemoveSource: (String) -> Unit,
    onPickLocalFile: () -> Unit,
    onPickDriveFile: () -> Unit,
    onOpenTextSourceDialog: () -> Unit,
    onDismissTextSourceDialog: () -> Unit,
    onAddSourceTitleChanged: (String) -> Unit,
    onAddSourceContentChanged: (String) -> Unit,
    onSubmitTextSource: () -> Unit,
    onOpenExistingSourcesDialog: () -> Unit,
    onDismissExistingSourcesDialog: () -> Unit,
    onConfirmExistingSources: (Set<String>) -> Unit,
    onSteeringPromptChanged: (String) -> Unit,
    onSetEpisodeLength: (String) -> Unit,
    onGenerateSuggestions: () -> Unit,
    onSelectSuggestion: (Int) -> Unit,
    onRevisionInstructionChanged: (String) -> Unit,
    onApplyRevision: (Int?) -> Unit,
    onToggleHostSelection: (String) -> Unit,
    onToggleGuestSelection: (EpisodeGuest) -> Unit,
    onConfirmAndStartGeneration: () -> Unit,
    onUpdateDraft: ((EpisodeDraft) -> EpisodeDraft) -> Unit,
    onUpdateGuest: (Int, String, String) -> Unit,
    onChooseGuestVoice: (Int) -> Unit,
    voiceDesign: VoiceDesignController?
) {
    var editingGuestIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ExpressiveTopAppBar(
            title = stringResource(R.string.episode_wizard_title),
            canNavigateBack = true,
            onNavigateBack = onNavigateBack
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LocalContentPadding.current + PaddingValues(horizontal = 20.dp, vertical = 12.dp))
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

                val addSourceState = uiState.addSource
                val canAddSource = !addSourceState.isAdding && !uiState.isDrafting
                val selectedSources = uiState.availableSources.filter { it.id in uiState.selectedSourceIds }

                Text(
                    text = stringResource(R.string.episode_wizard_add_sources_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SourceActionButton(
                        icon = Icons.Default.FolderOpen,
                        label = stringResource(R.string.sources_tab_file),
                        onClick = onPickLocalFile,
                        enabled = canAddSource,
                        modifier = Modifier.weight(1f)
                    )
                    SourceActionButton(
                        icon = Icons.Default.Cloud,
                        label = stringResource(R.string.sources_tab_drive),
                        onClick = onPickDriveFile,
                        enabled = canAddSource,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SourceActionButton(
                        icon = Icons.Default.TextFields,
                        label = stringResource(R.string.sources_tab_text),
                        onClick = onOpenTextSourceDialog,
                        enabled = canAddSource,
                        modifier = Modifier.weight(1f)
                    )
                    SourceActionButton(
                        icon = Icons.Default.Folder,
                        label = stringResource(R.string.episode_wizard_existing_sources),
                        onClick = onOpenExistingSourcesDialog,
                        enabled = canAddSource && uiState.availableSources.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedSources.isEmpty() && addSourceState.pendingName == null) {
                    Text(
                        text = stringResource(R.string.episode_wizard_no_sources_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.episode_wizard_selected_sources, selectedSources.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectedSources.forEach { source ->
                            SelectedSourceBox(
                                title = source.title,
                                enabled = !uiState.isDrafting,
                                onRemove = { onRemoveSource(source.id) }
                            )
                        }
                        addSourceState.pendingName?.let { PendingSourceBox(it) }
                    }
                }

                // Errors from files / Drive (the text dialog shows its own).
                if (addSourceState.errorMessage != null && !addSourceState.showTextDialog) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = addSourceState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    value = uiState.steeringPrompt,
                    onValueChange = onSteeringPromptChanged,
                    label = { Text(stringResource(R.string.episode_wizard_prompt_label)) },
                    placeholder = { Text(stringResource(R.string.episode_wizard_prompt_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = ExpressiveShapes.small,
                    enabled = !uiState.isDrafting
                )

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
                                .clickable(enabled = !uiState.isDrafting) { onSetEpisodeLength(lengthKey) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = uiState.episodeLength == lengthKey,
                                onClick = { onSetEpisodeLength(lengthKey) },
                                enabled = !uiState.isDrafting
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(stringResId), style = MaterialTheme.typography.bodyMedium)
                        }
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

                Button(
                    onClick = onGenerateSuggestions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = ExpressiveShapes.medium,
                    enabled = !uiState.isDrafting && !uiState.addSource.isAdding && uiState.selectedSourceIds.isNotEmpty()
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
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.episode_wizard_generate_draft_button),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Step 2: Choose Suggestion, Configure Speakers & Confirm
                Text(
                    text = stringResource(R.string.episode_wizard_step_draft_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Suggestion selector tabs
                if (uiState.suggestions.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.suggestions.indices.forEach { index ->
                            val isSelected = index == uiState.selectedSuggestionIndex
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectSuggestion(index) },
                                label = {
                                    Text(
                                        text = stringResource(R.string.episode_wizard_suggestion_badge, index + 1),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = ExpressiveShapes.small
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                val currentEpisodes = uiState.currentEpisodes
                if (currentEpisodes.size > 1) {
                    Text(
                        text = stringResource(
                            R.string.episode_wizard_part_badge,
                            pagerState.currentPage + 1,
                            currentEpisodes.size
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    currentEpisodes.getOrNull(page)?.let { draft ->
                        DraftReviewCard(
                            draft = draft,
                            isRevising = uiState.isRevising,
                            isBusy = uiState.isRevising || uiState.isConfirming,
                            // Tapping a suggestion fills the revision field (which enables the
                            // revise button) rather than revising straight away.
                            onApplyPredictedChange = { change -> onRevisionInstructionChanged(change) },
                            onUpdateDraft = onUpdateDraft
                        )
                    }
                }

                if (currentEpisodes.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        currentEpisodes.indices.forEach { index ->
                            val isActive = index == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(if (isActive) 8.dp else 6.dp)
                                    .background(
                                        color = if (isActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Speaker Selection
                val currentSpeakerSelection = uiState.selectedSpeakerSelection
                val totalSelectedSpeakers = currentSpeakerSelection.totalSpeakers
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
                            val isSelected = currentSpeakerSelection.hostIds.contains(host.id)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onToggleHostSelection(host.id) },
                                label = { Text("Host: ${host.name} (${host.voice})") },
                                leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                shape = ExpressiveShapes.small
                            )
                        }

                        // Guest Speakers from Draft
                        uiState.selectedDraft?.guests?.forEachIndexed { index, guest ->
                            val isSelected = currentSpeakerSelection.guests.any { it.name == guest.name }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onToggleGuestSelection(guest) },
                                    label = { Text("Guest: ${guest.name} (${guest.voice}) - ${guest.persona}") },
                                    leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 2.dp),
                                    shape = ExpressiveShapes.small
                                )
                                IconButton(
                                    onClick = { editingGuestIndex = index },
                                    enabled = !uiState.isRevising && !uiState.isConfirming
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.person_edit_content_desc, guest.name),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
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
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    value = uiState.revisionInstruction,
                    onValueChange = onRevisionInstructionChanged,
                    label = { Text(stringResource(R.string.episode_wizard_revise_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveShapes.small,
                    enabled = !uiState.isRevising && !uiState.isConfirming
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onApplyRevision(null) },
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
                    onClick = onConfirmAndStartGeneration,
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

        // Add plain text / pick existing sources dialogs
        if (uiState.addSource.showTextDialog) {
            TextSourceDialog(
                state = uiState.addSource,
                onTitleChanged = onAddSourceTitleChanged,
                onContentChanged = onAddSourceContentChanged,
                onSubmit = onSubmitTextSource,
                onDismiss = onDismissTextSourceDialog
            )
        }
        if (uiState.addSource.showExistingDialog) {
            ExistingSourcesDialog(
                sources = uiState.availableSources,
                initiallySelected = uiState.selectedSourceIds,
                onConfirm = onConfirmExistingSources,
                onDismiss = onDismissExistingSourcesDialog
            )
        }
    }

    editingGuestIndex?.let { index ->
        uiState.selectedDraft?.guests?.getOrNull(index)?.let { guest ->
            PersonEditDialog(
                initialName = guest.name,
                initialPersona = guest.persona,
                onDismiss = { editingGuestIndex = null },
                onSave = { name, persona ->
                    onUpdateGuest(index, name, persona)
                    editingGuestIndex = null
                },
                onChooseVoice = { onChooseGuestVoice(index) },
                hasPickedVoice = guest.resolvedVoiceId != null,
                voiceDesign = voiceDesign
            )
        }
    }
}

@Composable
private fun SourceActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ExpressiveShapes.small,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A source added to the episode, shown as a box with its name and a way to take it out again. */
@Composable
private fun SelectedSourceBox(
    title: String,
    enabled: Boolean,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.small,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.episode_wizard_remove_source, title),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Placeholder box for a file / Drive document that is still being added. */
@Composable
private fun PendingSourceBox(name: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.sources_uploading_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TextSourceDialog(
    state: AddSourceUiState,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.sources_add_text_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    value = state.sourceTitle,
                    onValueChange = onTitleChanged,
                    label = { Text(stringResource(R.string.sources_source_title_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveShapes.small,
                    singleLine = true,
                    enabled = !state.isAdding
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    value = state.sourceContent,
                    onValueChange = onContentChanged,
                    label = { Text(stringResource(R.string.sources_source_content_label)) },
                    placeholder = { Text(stringResource(R.string.sources_source_content_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = ExpressiveShapes.small,
                    enabled = !state.isAdding
                )
                if (state.isAdding) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.sources_uploading_source),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (state.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = state.sourceTitle.isNotBlank() && state.sourceContent.isNotBlank() && !state.isAdding,
                shape = ExpressiveShapes.small
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isAdding) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        shape = ExpressiveShapes.large
    )
}

/** Pick any number of the show's existing sources to use in this episode. */
@Composable
private fun ExistingSourcesDialog(
    sources: List<Source>,
    initiallySelected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(initiallySelected.toList()) }
    val allSelected = sources.isNotEmpty() && sources.all { it.id in selected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.episode_wizard_existing_sources_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (sources.isEmpty()) {
                    Text(
                        text = stringResource(R.string.episode_wizard_existing_sources_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selected.size}/${sources.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { selected = if (allSelected) emptyList() else sources.map { it.id } }
                        ) {
                            Text(
                                stringResource(
                                    if (allSelected) R.string.episode_wizard_deselect_all_sources
                                    else R.string.episode_wizard_select_all_sources
                                )
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sources.forEach { source ->
                            val isSelected = source.id in selected
                            val toggle = {
                                selected = if (isSelected) selected - source.id else selected + source.id
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggle() },
                                shape = ExpressiveShapes.small,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = isSelected, onCheckedChange = { toggle() })
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = source.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
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
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected.toSet()) },
                enabled = sources.isNotEmpty(),
                shape = ExpressiveShapes.small
            ) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = ExpressiveShapes.large
    )
}

@Composable
fun DraftReviewCard(
    draft: EpisodeDraft,
    isRevising: Boolean = false,
    isBusy: Boolean = false,
    onApplyPredictedChange: (String) -> Unit = {},
    onUpdateDraft: ((EpisodeDraft) -> EpisodeDraft) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            EditableTextField(
                label = stringResource(R.string.episode_wizard_title_label),
                value = draft.title,
                onValueChange = { v -> onUpdateDraft { it.copy(title = v) } },
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textColor = MaterialTheme.colorScheme.primary,
                enabled = !isBusy
            )
            Spacer(modifier = Modifier.height(6.dp))
            EditableTextField(
                label = stringResource(R.string.episode_wizard_topics_label),
                value = draft.topics,
                onValueChange = { v -> onUpdateDraft { it.copy(topics = v) } },
                enabled = !isBusy,
                minLines = 3
            )
            Spacer(modifier = Modifier.height(10.dp))
            EditableTextField(
                label = stringResource(R.string.episode_wizard_notes_label),
                value = draft.productionNotes.orEmpty(),
                onValueChange = { v -> onUpdateDraft { it.copy(productionNotes = v) } },
                textStyle = MaterialTheme.typography.bodySmall,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                enabled = !isBusy,
                minLines = 3
            )

            if (draft.predictedChanges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.episode_wizard_predicted_changes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    draft.predictedChanges.forEach { change ->
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

@Preview(showSystemUi = true, name = "Step 1: Setup")
@Composable
fun EpisodeWizardStep1Preview() {
    val sampleSources = listOf(
        Source(id = "s1", title = "Meeting Notes", contents = "..."),
        Source(id = "s2", title = "Research Paper", contents = "...")
    )
    AIPodcastsTheme {
        EpisodeWizardScreen(
            uiState = EpisodeWizardUiState(
                step = 1,
                availableSources = sampleSources,
                selectedSourceIds = setOf("s1")
            ),
            pagerState = rememberPagerState(pageCount = { 1 }),
            onNavigateBack = {},
            onRemoveSource = {},
            onPickLocalFile = {},
            onPickDriveFile = {},
            onOpenTextSourceDialog = {},
            onDismissTextSourceDialog = {},
            onAddSourceTitleChanged = {},
            onAddSourceContentChanged = {},
            onSubmitTextSource = {},
            onOpenExistingSourcesDialog = {},
            onDismissExistingSourcesDialog = {},
            onConfirmExistingSources = {},
            onSteeringPromptChanged = {},
            onSetEpisodeLength = {},
            onGenerateSuggestions = {},
            onSelectSuggestion = {},
            onRevisionInstructionChanged = {},
            onApplyRevision = {},
            onToggleHostSelection = {},
            onToggleGuestSelection = {},
            onConfirmAndStartGeneration = {},
            onUpdateDraft = {},
            onUpdateGuest = { _, _, _ -> },
            onChooseGuestVoice = {},
            voiceDesign = null
        )
    }
}

@Preview(showSystemUi = true, name = "Step 2: Draft Review")
@Composable
fun EpisodeWizardStep2Preview() {
    val sampleSuggestion = EpisodeSuggestion(
        episodes = listOf(
            EpisodeDraft(
                title = "AI Ethics in Healthcare",
                topics = "Patient data privacy, biased algorithms",
                productionNotes = "Keep it serious but accessible."
            )
        )
    )
    val samplePodcast = Podcast(
        id = "p1",
        title = "Science Weekly",
        description = "",
        structure = "",
        hosts = listOf(Host(id = "h1", name = "Alice", voice = "Nova", persona = "Moderator"))
    )

    AIPodcastsTheme {
        EpisodeWizardScreen(
            uiState = EpisodeWizardUiState(
                step = 2,
                podcast = samplePodcast,
                suggestions = listOf(sampleSuggestion),
                selectedSuggestionIndex = 0,
                speakerSelections = listOf(SpeakerSelection(hostIds = setOf("h1")))
            ),
            pagerState = rememberPagerState(pageCount = { 1 }),
            onNavigateBack = {},
            onRemoveSource = {},
            onPickLocalFile = {},
            onPickDriveFile = {},
            onOpenTextSourceDialog = {},
            onDismissTextSourceDialog = {},
            onAddSourceTitleChanged = {},
            onAddSourceContentChanged = {},
            onSubmitTextSource = {},
            onOpenExistingSourcesDialog = {},
            onDismissExistingSourcesDialog = {},
            onConfirmExistingSources = {},
            onSteeringPromptChanged = {},
            onSetEpisodeLength = {},
            onGenerateSuggestions = {},
            onSelectSuggestion = {},
            onRevisionInstructionChanged = {},
            onApplyRevision = {},
            onToggleHostSelection = {},
            onToggleGuestSelection = {},
            onConfirmAndStartGeneration = {},
            onUpdateDraft = {},
            onUpdateGuest = { _, _, _ -> },
            onChooseGuestVoice = {},
            voiceDesign = null
        )
    }
}
