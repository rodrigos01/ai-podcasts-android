package com.rodrigos01.aipodcasts.ui.screens.edit

import com.rodrigos01.aipodcasts.ui.components.LocalSnackbarHostState
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodrigos01.aipodcasts.R
import com.rodrigos01.aipodcasts.ui.components.ExpressiveTopAppBar
import com.rodrigos01.aipodcasts.ui.components.LocalContentPadding
import com.rodrigos01.aipodcasts.ui.components.PersonEditDialog
import com.rodrigos01.aipodcasts.ui.theme.ExpressiveShapes

@Composable
fun EpisodeEditScreen(
    podcastId: String,
    episodeId: String,
    onNavigateBack: () -> Unit,
    onDeleted: () -> Unit = onNavigateBack,
    viewModel: EpisodeEditViewModel = viewModel(factory = EpisodeEditViewModel.provideFactory(podcastId, episodeId))
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = LocalSnackbarHostState.current
    var editingGuestIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.isDone) {
        if (state.isDone) onNavigateBack()
    }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onDeleted()
    }
    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearInfo()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val editable = !state.isLoading && !state.isSaving && !state.isRegenerating && !state.isClearingAudio && !state.isDeleting

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveTopAppBar(
                title = stringResource(R.string.episode_edit_title),
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                actions = {
                    if (!state.isLoading) {
                        IconButton(onClick = viewModel::promptDelete, enabled = editable) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete)
                            )
                        }
                    }
                }
            )
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        value = state.title,
                        onValueChange = viewModel::onTitleChanged,
                        label = { Text(stringResource(R.string.episode_edit_field_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveShapes.small,
                        enabled = editable
                    )
                    OutlinedTextField(
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        value = state.topics,
                        onValueChange = viewModel::onTopicsChanged,
                        label = { Text(stringResource(R.string.episode_edit_field_topics)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveShapes.small,
                        enabled = editable
                    )
                    OutlinedTextField(
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        value = state.notes,
                        onValueChange = viewModel::onNotesChanged,
                        label = { Text(stringResource(R.string.episode_edit_field_notes)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveShapes.small,
                        enabled = editable
                    )

                    if (state.guests.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.episode_edit_guests_header),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        state.guests.forEachIndexed { index, guest ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(guest.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = guest.persona,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                    if (guest.resolvedVoiceId in state.pickedVoiceIds) {
                                        Text(
                                            text = stringResource(R.string.voice_design_picked),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                IconButton(onClick = { editingGuestIndex = index }, enabled = editable) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.person_edit_content_desc, guest.name),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (state.validationError) {
                        Text(
                            text = stringResource(R.string.episode_edit_error_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // Regenerating discards what is stored, so it is only offered once nothing is unsaved.
                    TextButton(
                        onClick = viewModel::promptRegenerate,
                        enabled = editable && !viewModel.hasUnsavedChanges
                    ) {
                        if (state.isRegenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_regenerate))
                    }
                    // Keeps the script; only the audio is rebuilt (e.g. after changing a voice).
                    TextButton(
                        onClick = viewModel::promptClearAudio,
                        enabled = editable && !viewModel.hasUnsavedChanges && !state.status.equals("generating", ignoreCase = true)
                    ) {
                        if (state.isClearingAudio) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.episode_edit_regenerate_audio))
                    }
                }
                Button(
                    onClick = viewModel::save,
                    enabled = editable,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 12.dp,
                            bottom = LocalContentPadding.current.calculateBottomPadding() + 12.dp
                        )
                        .height(48.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    editingGuestIndex?.let { index ->
        state.guests.getOrNull(index)?.let { guest ->
            PersonEditDialog(
                initialName = guest.name,
                initialPersona = guest.persona,
                onDismiss = { editingGuestIndex = null },
                onSave = { name, persona ->
                    viewModel.updateGuest(index, name, persona)
                    editingGuestIndex = null
                },
                onChooseVoice = { viewModel.chooseGuestVoice(index) },
                hasPickedVoice = guest.resolvedVoiceId in state.pickedVoiceIds,
                voiceDesign = viewModel.voiceDesign
            )
        }
    }

    if (state.showClearAudioConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearAudio,
            title = { Text(stringResource(R.string.episode_clear_audio_title)) },
            text = { Text(stringResource(R.string.episode_clear_audio_confirm)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmClearAudio) {
                    Text(stringResource(R.string.episode_edit_regenerate_audio))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearAudio) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            shape = ExpressiveShapes.large
        )
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!state.isDeleting) viewModel.dismissDelete() },
            title = { Text(stringResource(R.string.episode_delete_confirm_title)) },
            text = { Text(stringResource(R.string.episode_delete_confirm, state.title)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, enabled = !state.isDeleting) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete, enabled = !state.isDeleting) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            shape = ExpressiveShapes.large
        )
    }

    if (state.showRegenerateConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRegenerate,
            title = { Text(stringResource(R.string.episode_regenerate_confirm_title)) },
            text = {
                Text(
                    if (state.regenerateAfterSave) stringResource(R.string.episode_edit_saved_regenerate)
                    else stringResource(R.string.episode_regenerate_confirm, state.title)
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = viewModel::confirmRegenerate) {
                        Text(stringResource(R.string.action_regenerate))
                    }
                    // A new voice doesn't change the script, so offer to redo just the audio.
                    if (state.regenerateAfterSave && state.pickedVoiceIds.isNotEmpty()) {
                        TextButton(onClick = viewModel::confirmClearAudio) {
                            Text(stringResource(R.string.episode_edit_regenerate_audio))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRegenerate) {
                    Text(
                        stringResource(
                            if (state.regenerateAfterSave) R.string.episode_edit_not_now else R.string.action_cancel
                        )
                    )
                }
            },
            shape = ExpressiveShapes.large
        )
    }
}
