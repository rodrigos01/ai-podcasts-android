package com.rodrigos01.aipodcasts.ui.screens.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.UserRecoverableAuthException
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.drive.GoogleDriveHelper
import com.rodrigos01.aipodcasts.data.model.Source
import com.rodrigos01.aipodcasts.data.repository.SourceRepository
import com.rodrigos01.aipodcasts.util.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AddSourceMode {
    LOCAL_FILE,
    GOOGLE_DRIVE,
    PLAIN_TEXT
}

data class SourcesUiState(
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val sources: List<Source> = emptyList(),
    val showAddDialog: Boolean = false,
    val addSourceMode: AddSourceMode = AddSourceMode.LOCAL_FILE,
    val sourceTitle: String = "",
    val sourceContent: String = "",
    val selectedFileUri: Uri? = null,
    val selectedFileName: String? = null,
    val isTextFile: Boolean = false,
    val isDriveResolving: Boolean = false,
    val driveFileId: String? = null,
    val driveAccessToken: String? = null,
    val driveStatusText: String? = null,
    val errorMessage: String? = null
)

class SourcesViewModel(
    private val sourceRepo: SourceRepository = AIPodcastsApplication.instance.sourceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SourcesUiState())
    val uiState: StateFlow<SourcesUiState> = _uiState.asStateFlow()

    fun loadSources(podcastId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val list = sourceRepo.getSources(podcastId)
                _uiState.value = _uiState.value.copy(isLoading = false, sources = list)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    fun openAddDialog() {
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            addSourceMode = AddSourceMode.LOCAL_FILE,
            sourceTitle = "",
            sourceContent = "",
            selectedFileUri = null,
            selectedFileName = null,
            isTextFile = false,
            isDriveResolving = false,
            driveFileId = null,
            driveAccessToken = null,
            driveStatusText = null,
            errorMessage = null
        )
    }

    fun dismissAddDialog() {
        _uiState.value = _uiState.value.copy(
            showAddDialog = false,
            selectedFileUri = null,
            selectedFileName = null,
            driveFileId = null,
            driveAccessToken = null
        )
    }

    fun setAddSourceMode(mode: AddSourceMode) {
        _uiState.value = _uiState.value.copy(
            addSourceMode = mode,
            errorMessage = null
        )
    }

    fun onTitleChanged(title: String) {
        _uiState.value = _uiState.value.copy(sourceTitle = title)
    }

    fun onContentChanged(content: String) {
        _uiState.value = _uiState.value.copy(sourceContent = content)
    }

    fun onLocalFileSelected(context: Context, uri: Uri) {
        val fileName = FileUtils.getFileName(context, uri)
        val isText = FileUtils.isTextFile(context, uri, fileName)
        _uiState.value = _uiState.value.copy(
            selectedFileUri = uri,
            selectedFileName = fileName,
            isTextFile = isText,
            sourceTitle = fileName,
            errorMessage = null
        )
    }

    fun setError(message: String) {
        _uiState.value = _uiState.value.copy(
            errorMessage = message,
            isDriveResolving = false,
            isUploading = false
        )
    }

    private var pendingDriveUri: Uri? = null
    private var pendingRawTitle: String? = null
    private var pendingCleanTitle: String? = null

    fun onDriveFileSelected(
        context: Context,
        uri: Uri,
        accountEmail: String? = null,
        onNeedConsent: (androidx.activity.result.IntentSenderRequest) -> Unit
    ) {
        val rawTitle = FileUtils.getRawDisplayName(context, uri) ?: "Google Drive Document"
        val cleanTitle = FileUtils.cleanFileName(rawTitle) ?: rawTitle
        val isText = FileUtils.isTextFile(context, uri, rawTitle)
        val extractedDocId = GoogleDriveHelper.extractGoogleDocId(uri)

        pendingDriveUri = uri
        pendingRawTitle = rawTitle
        pendingCleanTitle = cleanTitle

        _uiState.value = _uiState.value.copy(
            selectedFileUri = uri,
            selectedFileName = rawTitle,
            sourceTitle = rawTitle,
            isTextFile = isText,
            isDriveResolving = true,
            driveFileId = extractedDocId,
            driveStatusText = null,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val authResult = GoogleDriveHelper.requestDocsAuthorization(context, accountEmail)
                if (authResult.hasResolution()) {
                    val pendingIntent = authResult.pendingIntent
                    if (pendingIntent != null) {
                        onNeedConsent(
                            androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isDriveResolving = false,
                            errorMessage = "Authorization failed: no resolution intent"
                        )
                    }
                } else {
                    val token = authResult.accessToken
                    if (token != null) {
                        finishDriveResolution(rawTitle, cleanTitle, extractedDocId, token)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isDriveResolving = false,
                            errorMessage = "Failed to obtain Google Drive access token"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDriveResolving = false,
                    errorMessage = e.localizedMessage ?: "Failed to authorize Google Drive access"
                )
            }
        }
    }

    fun onDriveConsentResult(context: Context, data: Intent?) {
        val authResult = GoogleDriveHelper.getAuthorizationResultFromIntent(context, data)
        val token = authResult?.accessToken
        val rawTitle = pendingRawTitle ?: _uiState.value.selectedFileName ?: "Google Drive Document"
        val cleanTitle = pendingCleanTitle ?: rawTitle
        val initialDocId = _uiState.value.driveFileId

        if (token != null) {
            viewModelScope.launch {
                finishDriveResolution(rawTitle, cleanTitle, initialDocId, token)
            }
        } else {
            _uiState.value = _uiState.value.copy(
                isDriveResolving = false,
                errorMessage = "Google Drive authorization was not granted."
            )
        }
    }

    private suspend fun finishDriveResolution(
        rawTitle: String,
        cleanTitle: String?,
        initialDocId: String?,
        token: String
    ) {
        val resolvedId = initialDocId ?: GoogleDriveHelper.resolveDriveFileId(rawTitle, cleanTitle, token)
        _uiState.value = _uiState.value.copy(
            driveFileId = resolvedId,
            driveAccessToken = token,
            isDriveResolving = false,
            driveStatusText = if (resolvedId != null) "Google Drive document linked" else "Google Drive file selected"
        )
    }

    fun submitSource(context: Context, podcastId: String) {
        when (_uiState.value.addSourceMode) {
            AddSourceMode.PLAIN_TEXT -> {
                val title = _uiState.value.sourceTitle.trim()
                val content = _uiState.value.sourceContent.trim()
                if (title.isBlank() || content.isBlank()) return

                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(isUploading = true, errorMessage = null)
                    try {
                        val created = sourceRepo.createTextSource(podcastId, title, content)
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            showAddDialog = false,
                            sources = listOf(created) + _uiState.value.sources
                        )
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            errorMessage = e.localizedMessage ?: e.message
                        )
                    }
                }
            }

            AddSourceMode.LOCAL_FILE -> {
                val uri = _uiState.value.selectedFileUri ?: return
                val title = _uiState.value.sourceTitle.trim()
                val fileName = _uiState.value.selectedFileName ?: "document"
                val finalTitle = if (title.isNotBlank()) title else fileName
                val isText = _uiState.value.isTextFile

                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(isUploading = true, errorMessage = null)
                    try {
                        val created = if (isText) {
                            val textContent = FileUtils.readTextFromUri(context, uri)
                            sourceRepo.createTextSource(podcastId, finalTitle, textContent)
                        } else {
                            val tempFile = FileUtils.copyUriToTempFile(context, uri, "upload_pdf_temp.pdf")
                            try {
                                sourceRepo.uploadPdfSource(
                                    podcastId = podcastId,
                                    file = tempFile,
                                    fileName = fileName,
                                    customTitle = finalTitle
                                )
                            } finally {
                                tempFile.delete()
                            }
                        }

                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            showAddDialog = false,
                            sources = listOf(created) + _uiState.value.sources
                        )
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            errorMessage = e.localizedMessage ?: e.message
                        )
                    }
                }
            }

            AddSourceMode.GOOGLE_DRIVE -> {
                val fileId = _uiState.value.driveFileId
                val accessToken = _uiState.value.driveAccessToken
                val title = _uiState.value.sourceTitle.trim()
                val fileName = _uiState.value.selectedFileName ?: "Google Drive Document"
                val finalTitle = if (title.isNotBlank()) title else fileName

                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(isUploading = true, errorMessage = null)
                    try {
                        val created = if (fileId != null && accessToken != null) {
                            // Backend Drive endpoint
                            sourceRepo.createDriveSource(
                                podcastId = podcastId,
                                fileId = fileId,
                                accessToken = accessToken,
                                title = finalTitle
                            )
                        } else {
                            val uri = _uiState.value.selectedFileUri
                                ?: throw IllegalStateException("No Google Drive file selected")
                            if (_uiState.value.isTextFile) {
                                // Local fallback for streamable text
                                val textContent = FileUtils.readTextFromUri(context, uri)
                                sourceRepo.createTextSource(podcastId, finalTitle, textContent)
                            } else {
                                // Local fallback for streamable PDF
                                val tempFile = FileUtils.copyUriToTempFile(context, uri, "upload_drive_temp.pdf")
                                try {
                                    sourceRepo.uploadPdfSource(
                                        podcastId = podcastId,
                                        file = tempFile,
                                        fileName = fileName,
                                        customTitle = finalTitle
                                    )
                                } finally {
                                    tempFile.delete()
                                }
                            }
                        }

                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            showAddDialog = false,
                            sources = listOf(created) + _uiState.value.sources
                        )
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            errorMessage = e.localizedMessage ?: e.message
                        )
                    }
                }
            }
        }
    }

    fun createTextSource(podcastId: String) {
        setAddSourceMode(AddSourceMode.PLAIN_TEXT)
        submitSource(AIPodcastsApplication.instance, podcastId)
    }

    fun uploadPdfSource(context: Context, podcastId: String, uri: Uri) {
        onLocalFileSelected(context, uri)
        submitSource(context, podcastId)
    }

    fun deleteSource(podcastId: String, sourceId: String) {
        viewModelScope.launch {
            try {
                sourceRepo.deleteSource(podcastId, sourceId)
                _uiState.value = _uiState.value.copy(
                    sources = _uiState.value.sources.filter { it.id != sourceId }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage ?: e.message)
            }
        }
    }
}
