package com.rodrigos01.aipodcasts.ui.screens.episode

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.R
import com.rodrigos01.aipodcasts.data.model.EpisodeDraft
import com.rodrigos01.aipodcasts.ui.components.ExpressiveTopAppBar
import com.rodrigos01.aipodcasts.ui.components.VoiceChip
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

    // Pages through the episodes of the currently selected suggestion (1 page
    // for a single episode, 2 for a split). Kept in sync with the ViewModel's
    // selectedEpisodeIndex in both directions below.
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
            viewModel.onAddSourceLocalFileSelected(context, uri)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Available Sources (${uiState.selectedSourceIds.size}/${uiState.availableSources.size} selected)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.availableSources.isNotEmpty()) {
                        TextButton(onClick = { viewModel.toggleSelectAllSources() }) {
                            Text(
                                text = if (uiState.selectedSourceIds.size == uiState.availableSources.size) {
                                    stringResource(R.string.episode_wizard_deselect_all_sources)
                                } else {
                                    stringResource(R.string.episode_wizard_select_all_sources)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { viewModel.openAddSourceDialog() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveShapes.small
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.podcast_detail_add_source_button))
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.availableSources.isEmpty()) {
                    Text(
                        text = stringResource(R.string.episode_wizard_no_sources_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
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

                Spacer(modifier = Modifier.height(18.dp))

                // Episode Length Selector - chosen up front, shapes generation
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
                                .clickable(enabled = !uiState.isDrafting) { viewModel.setEpisodeLength(lengthKey) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = uiState.episodeLength == lengthKey,
                                onClick = { viewModel.setEpisodeLength(lengthKey) },
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
                    onClick = { viewModel.generateSuggestions(podcastId) },
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
                // Step 2: Choose Suggestion, Configure Speakers & Confirm
                Text(
                    text = stringResource(R.string.episode_wizard_step_draft_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Suggestion selector tabs (1-2 independent suggestions from the wizard)
                if (uiState.suggestions.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.suggestions.indices.forEach { index ->
                            val isSelected = index == uiState.selectedSuggestionIndex
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectSuggestion(index) },
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
                            episodePagerState.currentPage + 1,
                            currentEpisodes.size
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                HorizontalPager(
                    state = episodePagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    currentEpisodes.getOrNull(page)?.let { draft ->
                        DraftReviewCard(
                            draft = draft,
                            isRevising = uiState.isRevising,
                            onApplyPredictedChange = { change ->
                                viewModel.applyRevision(podcastId, instructionOverride = change, episodeIndexOverride = page)
                            }
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
                            val isActive = index == episodePagerState.currentPage
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

                // Speaker Selection (Constraint: Exactly 2 speakers)
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
                        uiState.selectedDraft?.guests?.forEach { guest ->
                            val isSelected = currentSpeakerSelection.guests.any { it.name == guest.name }
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
                    onClick = { viewModel.applyRevision(podcastId, episodeIndexOverride = episodePagerState.currentPage) },
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

        // Add Source Dialog
        if (uiState.addSource.showAddDialog) {
            val addSourceState = uiState.addSource
            AlertDialog(
                onDismissRequest = { if (!addSourceState.isUploading) viewModel.dismissAddSourceDialog() },
                title = {
                    Text(
                        text = stringResource(R.string.sources_add_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TabRow(
                            selectedTabIndex = addSourceState.addSourceMode.ordinal,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Tab(
                                selected = addSourceState.addSourceMode == AddSourceMode.LOCAL_FILE,
                                onClick = { viewModel.setAddSourceMode(AddSourceMode.LOCAL_FILE) },
                                text = { Text(stringResource(R.string.sources_tab_file)) }
                            )
                            Tab(
                                selected = addSourceState.addSourceMode == AddSourceMode.GOOGLE_DRIVE,
                                onClick = { viewModel.setAddSourceMode(AddSourceMode.GOOGLE_DRIVE) },
                                text = { Text(stringResource(R.string.sources_tab_drive)) }
                            )
                            Tab(
                                selected = addSourceState.addSourceMode == AddSourceMode.PLAIN_TEXT,
                                onClick = { viewModel.setAddSourceMode(AddSourceMode.PLAIN_TEXT) },
                                text = { Text(stringResource(R.string.sources_tab_text)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        when (addSourceState.addSourceMode) {
                            AddSourceMode.LOCAL_FILE -> {
                                OutlinedButton(
                                    onClick = {
                                        localFilePickerLauncher.launch(
                                            arrayOf("application/pdf", "text/plain")
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ExpressiveShapes.small,
                                    enabled = !addSourceState.isUploading
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.sources_select_file_button))
                                }

                                if (addSourceState.selectedFileName != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    AddSourceFileCard(
                                        fileName = addSourceState.selectedFileName,
                                        isTextFile = addSourceState.isTextFile
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = addSourceState.sourceTitle,
                                        onValueChange = { viewModel.onAddSourceTitleChanged(it) },
                                        label = { Text(stringResource(R.string.sources_source_title_label)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = ExpressiveShapes.small,
                                        singleLine = true,
                                        enabled = !addSourceState.isUploading
                                    )
                                }
                            }

                            AddSourceMode.GOOGLE_DRIVE -> {
                                OutlinedButton(
                                    onClick = {
                                        driveFilePickerLauncher.launch(
                                            FileUtils.createGoogleDrivePickerIntent(
                                                context = context,
                                                accountEmail = currentUserEmail
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ExpressiveShapes.small,
                                    enabled = !addSourceState.isUploading && !addSourceState.isDriveResolving
                                ) {
                                    Icon(Icons.Default.Cloud, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.sources_pick_drive_button))
                                }

                                if (addSourceState.selectedFileName != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    AddSourceDriveFileCard(
                                        fileName = addSourceState.selectedFileName,
                                        isResolving = addSourceState.isDriveResolving,
                                        isResolved = addSourceState.driveFileId != null
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = addSourceState.sourceTitle,
                                        onValueChange = { viewModel.onAddSourceTitleChanged(it) },
                                        label = { Text(stringResource(R.string.sources_source_title_label)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = ExpressiveShapes.small,
                                        singleLine = true,
                                        enabled = !addSourceState.isUploading
                                    )
                                }
                            }

                            AddSourceMode.PLAIN_TEXT -> {
                                OutlinedTextField(
                                    value = addSourceState.sourceTitle,
                                    onValueChange = { viewModel.onAddSourceTitleChanged(it) },
                                    label = { Text(stringResource(R.string.sources_source_title_label)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ExpressiveShapes.small,
                                    singleLine = true,
                                    enabled = !addSourceState.isUploading
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = addSourceState.sourceContent,
                                    onValueChange = { viewModel.onAddSourceContentChanged(it) },
                                    label = { Text(stringResource(R.string.sources_source_content_label)) },
                                    placeholder = { Text(stringResource(R.string.sources_source_content_hint)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp),
                                    shape = ExpressiveShapes.small,
                                    enabled = !addSourceState.isUploading
                                )
                            }
                        }

                        if (addSourceState.isUploading) {
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

                        if (addSourceState.errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = addSourceState.errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    val canSubmit = when (addSourceState.addSourceMode) {
                        AddSourceMode.PLAIN_TEXT ->
                            addSourceState.sourceTitle.isNotBlank() && addSourceState.sourceContent.isNotBlank()
                        AddSourceMode.LOCAL_FILE ->
                            addSourceState.selectedFileUri != null
                        AddSourceMode.GOOGLE_DRIVE ->
                            (addSourceState.driveFileId != null && addSourceState.driveAccessToken != null) ||
                                addSourceState.selectedFileUri != null
                    } && !addSourceState.isUploading && !addSourceState.isDriveResolving

                    Button(
                        onClick = { viewModel.submitAddSource(context, podcastId) },
                        enabled = canSubmit,
                        shape = ExpressiveShapes.small
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissAddSourceDialog() },
                        enabled = !addSourceState.isUploading
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = ExpressiveShapes.large
            )
        }
    }
}

@Composable
private fun AddSourceFileCard(
    fileName: String,
    isTextFile: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isTextFile) Icons.Default.Description else Icons.Default.PictureAsPdf,
            contentDescription = null,
            tint = if (isTextFile) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AddSourceDriveFileCard(
    fileName: String,
    isResolving: Boolean,
    isResolved: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isResolving) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.sources_drive_resolving),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (isResolved) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.sources_drive_linked),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DraftReviewCard(
    draft: EpisodeDraft,
    isRevising: Boolean = false,
    onApplyPredictedChange: (String) -> Unit = {}
) {
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
