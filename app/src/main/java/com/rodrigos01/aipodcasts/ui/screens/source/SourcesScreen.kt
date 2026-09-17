package com.rodrigos01.aipodcasts.ui.screens.source

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.R
import com.rodrigos01.aipodcasts.ui.components.EmptyState
import com.rodrigos01.aipodcasts.ui.components.ExpressiveTopAppBar
import com.rodrigos01.aipodcasts.ui.screens.detail.SourceItemCard
import com.rodrigos01.aipodcasts.ui.theme.ExpressiveShapes
import com.rodrigos01.aipodcasts.util.FileUtils

@Composable
fun SourcesScreen(
    podcastId: String,
    onNavigateBack: () -> Unit,
    viewModel: SourcesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentUserEmail = AIPodcastsApplication.instance.authRepository.currentUser?.email

    val localFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onLocalFileSelected(context, uri)
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDriveConsentResult(context, result.data)
        } else {
            viewModel.setError("Google Drive authorization was cancelled")
        }
    }

    val driveFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
            viewModel.onDriveFileSelected(
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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Mode selection tabs
                        TabRow(
                            selectedTabIndex = uiState.addSourceMode.ordinal,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Tab(
                                selected = uiState.addSourceMode == AddSourceMode.LOCAL_FILE,
                                onClick = { viewModel.setAddSourceMode(AddSourceMode.LOCAL_FILE) },
                                text = { Text(stringResource(R.string.sources_tab_file)) }
                            )
                            Tab(
                                selected = uiState.addSourceMode == AddSourceMode.GOOGLE_DRIVE,
                                onClick = { viewModel.setAddSourceMode(AddSourceMode.GOOGLE_DRIVE) },
                                text = { Text(stringResource(R.string.sources_tab_drive)) }
                            )
                            Tab(
                                selected = uiState.addSourceMode == AddSourceMode.PLAIN_TEXT,
                                onClick = { viewModel.setAddSourceMode(AddSourceMode.PLAIN_TEXT) },
                                text = { Text(stringResource(R.string.sources_tab_text)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        when (uiState.addSourceMode) {
                            AddSourceMode.LOCAL_FILE -> {
                                OutlinedButton(
                                    onClick = {
                                        localFilePickerLauncher.launch(
                                            arrayOf("application/pdf", "text/plain")
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ExpressiveShapes.small,
                                    enabled = !uiState.isUploading
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.sources_select_file_button))
                                }

                                if (uiState.selectedFileName != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    FileCard(
                                        fileName = uiState.selectedFileName ?: "",
                                        isTextFile = uiState.isTextFile
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = uiState.sourceTitle,
                                        onValueChange = { viewModel.onTitleChanged(it) },
                                        label = { Text(stringResource(R.string.sources_source_title_label)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = ExpressiveShapes.small,
                                        singleLine = true,
                                        enabled = !uiState.isUploading
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
                                    enabled = !uiState.isUploading && !uiState.isDriveResolving
                                ) {
                                    Icon(Icons.Default.Cloud, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.sources_pick_drive_button))
                                }

                                if (uiState.selectedFileName != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    DriveFileCard(
                                        fileName = uiState.selectedFileName ?: "",
                                        isResolving = uiState.isDriveResolving,
                                        isResolved = uiState.driveFileId != null
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = uiState.sourceTitle,
                                        onValueChange = { viewModel.onTitleChanged(it) },
                                        label = { Text(stringResource(R.string.sources_source_title_label)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = ExpressiveShapes.small,
                                        singleLine = true,
                                        enabled = !uiState.isUploading
                                    )
                                }
                            }

                            AddSourceMode.PLAIN_TEXT -> {
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
                            }
                        }

                        if (uiState.isUploading) {
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
                    val canSubmit = when (uiState.addSourceMode) {
                        AddSourceMode.PLAIN_TEXT ->
                            uiState.sourceTitle.isNotBlank() && uiState.sourceContent.isNotBlank()
                        AddSourceMode.LOCAL_FILE ->
                            uiState.selectedFileUri != null
                        AddSourceMode.GOOGLE_DRIVE ->
                            (uiState.driveFileId != null && uiState.driveAccessToken != null) || uiState.selectedFileUri != null
                    } && !uiState.isUploading && !uiState.isDriveResolving

                    Button(
                        onClick = { viewModel.submitSource(context, podcastId) },
                        enabled = canSubmit,
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

@Composable
private fun FileCard(
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
private fun DriveFileCard(
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
