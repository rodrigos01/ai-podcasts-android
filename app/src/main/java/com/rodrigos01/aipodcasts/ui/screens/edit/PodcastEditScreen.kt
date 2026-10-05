package com.rodrigos01.aipodcasts.ui.screens.edit

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodrigos01.aipodcasts.R
import com.rodrigos01.aipodcasts.ui.components.ExpressiveTopAppBar
import com.rodrigos01.aipodcasts.ui.components.LocalContentPadding
import com.rodrigos01.aipodcasts.ui.theme.ExpressiveShapes

@Composable
fun PodcastEditScreen(
    podcastId: String,
    onNavigateBack: () -> Unit,
    viewModel: PodcastEditViewModel = viewModel(factory = PodcastEditViewModel.provideFactory(podcastId))
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val editable = !state.isLoading && !state.isSaving

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveTopAppBar(
                title = stringResource(R.string.podcast_edit_title),
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.podcast_edit_future_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChanged,
                        label = { Text(stringResource(R.string.podcast_edit_field_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveShapes.small,
                        enabled = editable
                    )
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = viewModel::onDescriptionChanged,
                        label = { Text(stringResource(R.string.podcast_edit_field_description)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveShapes.small,
                        enabled = editable
                    )
                    OutlinedTextField(
                        value = state.structure,
                        onValueChange = viewModel::onStructureChanged,
                        label = { Text(stringResource(R.string.podcast_edit_field_structure)) },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveShapes.small,
                        enabled = editable
                    )

                    Text(
                        text = stringResource(R.string.podcast_edit_hosts_header),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    state.hosts.forEachIndexed { index, host ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveShapes.small,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = host.name,
                                        onValueChange = { viewModel.onHostChanged(index, host.copy(name = it)) },
                                        label = { Text(stringResource(R.string.podcast_edit_host_name)) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = ExpressiveShapes.small,
                                        enabled = editable
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeHost(index) },
                                        enabled = editable
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.podcast_edit_remove_host),
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = host.voice,
                                    onValueChange = { viewModel.onHostChanged(index, host.copy(voice = it)) },
                                    label = { Text(stringResource(R.string.podcast_edit_host_voice)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ExpressiveShapes.small,
                                    enabled = editable
                                )
                                OutlinedTextField(
                                    value = host.persona,
                                    onValueChange = { viewModel.onHostChanged(index, host.copy(persona = it)) },
                                    label = { Text(stringResource(R.string.podcast_edit_host_persona)) },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ExpressiveShapes.small,
                                    enabled = editable
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::addHost,
                        enabled = editable,
                        shape = ExpressiveShapes.medium
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.podcast_edit_add_host),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    if (state.validationError) {
                        Text(
                            text = stringResource(R.string.podcast_edit_error_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (state.noHostsError) {
                        Text(
                            text = stringResource(R.string.podcast_edit_error_hosts),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
