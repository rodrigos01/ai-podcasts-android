package com.rodrigos01.aipodcasts.ui.screens.source

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodrigos01.aipodcasts.R
import com.rodrigos01.aipodcasts.ui.components.EmptyState
import com.rodrigos01.aipodcasts.ui.components.ExpressiveTopAppBar
import com.rodrigos01.aipodcasts.ui.screens.detail.SourceItemCard
import com.rodrigos01.aipodcasts.ui.theme.ExpressiveShapes

@Composable
fun SourcesScreen(
    podcastId: String,
    onNavigateBack: () -> Unit,
    viewModel: SourcesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadPdfSource(context, podcastId, uri)
        }
    }

    LaunchedEffect(podcastId) {
        viewModel.loadSources(podcastId)
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.sources_title),
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                shape = ExpressiveShapes.medium,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.podcast_detail_add_source_button)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.sources.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.sources.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.Description,
                        title = stringResource(R.string.podcast_detail_no_sources),
                        description = stringResource(R.string.sources_desc),
                        actionButtonText = stringResource(R.string.podcast_detail_add_source_button),
                        onActionClick = { viewModel.openAddDialog() }
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.sources, key = { it.id }) { source ->
                            SourceItemCard(
                                source = source,
                                onDelete = { viewModel.deleteSource(podcastId, source.id) }
                            )
                        }
                    }
                }
            }
        }

        // Add Source Dialog
        if (uiState.showAddDialog) {
            AlertDialog(
                onDismissRequest = { if (!uiState.isUploading) viewModel.dismissAddDialog() },
                title = {
                    Text(
                        text = stringResource(R.string.sources_add_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        OutlinedTextField(
                            value = uiState.sourceTitle,
                            onValueChange = { viewModel.onTitleChanged(it) },
                            label = { Text(stringResource(R.string.sources_source_title_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveShapes.small,
                            singleLine = true,
                            enabled = !uiState.isUploading
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = uiState.sourceContent,
                            onValueChange = { viewModel.onContentChanged(it) },
                            label = { Text(stringResource(R.string.sources_source_content_label)) },
                            placeholder = { Text(stringResource(R.string.sources_source_content_hint)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            shape = ExpressiveShapes.small,
                            enabled = !uiState.isUploading
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = { pdfPickerLauncher.launch("application/pdf") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveShapes.small,
                            enabled = !uiState.isUploading
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.sources_upload_pdf_button))
                        }

                        if (uiState.isUploading) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.sources_uploading_pdf),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (uiState.errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.createTextSource(podcastId) },
                        enabled = uiState.sourceTitle.isNotBlank() && uiState.sourceContent.isNotBlank() && !uiState.isUploading,
                        shape = ExpressiveShapes.small
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissAddDialog() },
                        enabled = !uiState.isUploading
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = ExpressiveShapes.large
            )
        }
    }
}
