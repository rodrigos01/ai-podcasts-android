package com.rodrigos01.aipodcasts.ui.screens.episode

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.drive.GoogleDriveHelper
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.EpisodeDraft
import com.rodrigos01.aipodcasts.data.model.EpisodeGuest
import com.rodrigos01.aipodcasts.data.model.EpisodeSuggestion
import com.rodrigos01.aipodcasts.data.model.Host
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.model.Source
import com.rodrigos01.aipodcasts.data.repository.EpisodeRepository
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
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

data class AddSourceUiState(
    val showAddDialog: Boolean = false,
    val isUploading: Boolean = false,
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

data class EpisodeWizardUiState(
    val step: Int = 1, // 1: Sources, Prompt & Length, 2: Choose Suggestion & Configure Speakers
    val podcast: Podcast? = null,
    val availableSources: List<Source> = emptyList(),
    val selectedSourceIds: Set<String> = emptySet(),
    val addSource: AddSourceUiState = AddSourceUiState(),
    val steeringPrompt: String = "",
    val episodeLength: String = "short", // "short", "medium", "long" - chosen up front, shapes generation
    val isDrafting: Boolean = false,
    val isRevising: Boolean = false,
    val isConfirming: Boolean = false,
    // 1-2 independent suggestions from the wizard; each is itself 1-2 episodes
    // (a natural split), but this client only surfaces the first episode of
    // whichever suggestion is selected - no split-editing UI yet.
    val suggestions: List<EpisodeSuggestion> = emptyList(),
    val selectedSuggestionIndex: Int = 0,
    val revisionInstruction: String = "",
    val selectedHostIds: Set<String> = emptySet(),
    val selectedGuests: List<EpisodeGuest> = emptyList(),
    val confirmedEpisode: Episode? = null,
    val errorMessage: String? = null
) {
    val selectedDraft: EpisodeDraft?
        get() = suggestions.getOrNull(selectedSuggestionIndex)?.episodes?.firstOrNull()
}

class EpisodeWizardViewModel(
    private val podcastRepo: PodcastRepository = AIPodcastsApplication.instance.podcastRepository,
    private val sourceRepo: SourceRepository = AIPodcastsApplication.instance.sourceRepository,
    private val episodeRepo: EpisodeRepository = AIPodcastsApplication.instance.episodeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EpisodeWizardUiState())
    val uiState: StateFlow<EpisodeWizardUiState> = _uiState.asStateFlow()

    fun initWizard(podcastId: String) {
        viewModelScope.launch {
            try {
                val podcast = podcastRepo.getPodcast(podcastId)
                val sources = sourceRepo.getSources(podcastId)
                _uiState.value = _uiState.value.copy(
                    podcast = podcast,
                    availableSources = sources,
                    selectedSourceIds = emptySet()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage ?: e.message)
            }
        }
    }

    fun toggleSourceSelection(sourceId: String) {
        val current = _uiState.value.selectedSourceIds.toMutableSet()
        if (current.contains(sourceId)) {
            current.remove(sourceId)
        } else {
            current.add(sourceId)
        }
        _uiState.value = _uiState.value.copy(selectedSourceIds = current)
    }

    fun toggleSelectAllSources() {
        val allIds = _uiState.value.availableSources.map { it.id }.toSet()
        val allSelected = allIds.isNotEmpty() && _uiState.value.selectedSourceIds.containsAll(allIds)
        _uiState.value = _uiState.value.copy(
            selectedSourceIds = if (allSelected) emptySet() else allIds
        )
    }

    // --- Inline "Add Source" dialog (mirrors the flow that used to live only on the Sources screen) ---

    fun openAddSourceDialog() {
        _uiState.value = _uiState.value.copy(addSource = AddSourceUiState(showAddDialog = true))
    }

    fun dismissAddSourceDialog() {
        _uiState.value = _uiState.value.copy(
            addSource = _uiState.value.addSource.copy(
                showAddDialog = false,
                selectedFileUri = null,
                selectedFileName = null,
                driveFileId = null,
                driveAccessToken = null
            )
        )
    }

    fun setAddSourceMode(mode: AddSourceMode) {
        _uiState.value = _uiState.value.copy(
            addSource = _uiState.value.addSource.copy(addSourceMode = mode, errorMessage = null)
        )
    }

    fun onAddSourceTitleChanged(title: String) {
        _uiState.value = _uiState.value.copy(addSource = _uiState.value.addSource.copy(sourceTitle = title))
    }

    fun onAddSourceContentChanged(content: String) {
        _uiState.value = _uiState.value.copy(addSource = _uiState.value.addSource.copy(sourceContent = content))
    }

    fun onAddSourceLocalFileSelected(context: Context, uri: Uri) {
        val fileName = FileUtils.getFileName(context, uri)
        val isText = FileUtils.isTextFile(context, uri, fileName)
        _uiState.value = _uiState.value.copy(
            addSource = _uiState.value.addSource.copy(
                selectedFileUri = uri,
                selectedFileName = fileName,
                isTextFile = isText,
                sourceTitle = fileName,
                errorMessage = null
            )
        )
    }

    fun setAddSourceError(message: String) {
        _uiState.value = _uiState.value.copy(
            addSource = _uiState.value.addSource.copy(
                errorMessage = message,
                isDriveResolving = false,
                isUploading = false
            )
        )
    }

    private var pendingDriveUri: Uri? = null
    private var pendingRawTitle: String? = null
    private var pendingCleanTitle: String? = null

    fun onAddSourceDriveFileSelected(
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
            addSource = _uiState.value.addSource.copy(
                selectedFileUri = uri,
                selectedFileName = rawTitle,
                sourceTitle = rawTitle,
                isTextFile = isText,
                isDriveResolving = true,
                driveFileId = extractedDocId,
                driveStatusText = null,
                errorMessage = null
            )
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
                            addSource = _uiState.value.addSource.copy(
                                isDriveResolving = false,
                                errorMessage = "Authorization failed: no resolution intent"
                            )
                        )
                    }
                } else {
                    val token = authResult.accessToken
                    if (token != null) {
                        finishDriveResolution(rawTitle, cleanTitle, extractedDocId, token)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            addSource = _uiState.value.addSource.copy(
                                isDriveResolving = false,
                                errorMessage = "Failed to obtain Google Drive access token"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    addSource = _uiState.value.addSource.copy(
                        isDriveResolving = false,
                        errorMessage = e.localizedMessage ?: "Failed to authorize Google Drive access"
                    )
                )
            }
        }
    }

    fun onAddSourceDriveConsentResult(context: Context, data: Intent?) {
        val authResult = GoogleDriveHelper.getAuthorizationResultFromIntent(context, data)
        val token = authResult?.accessToken
        val rawTitle = pendingRawTitle ?: _uiState.value.addSource.selectedFileName ?: "Google Drive Document"
        val cleanTitle = pendingCleanTitle ?: rawTitle
        val initialDocId = _uiState.value.addSource.driveFileId

        if (token != null) {
            viewModelScope.launch {
                finishDriveResolution(rawTitle, cleanTitle, initialDocId, token)
            }
        } else {
            _uiState.value = _uiState.value.copy(
                addSource = _uiState.value.addSource.copy(
                    isDriveResolving = false,
                    errorMessage = "Google Drive authorization was not granted."
                )
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
            addSource = _uiState.value.addSource.copy(
                driveFileId = resolvedId,
                driveAccessToken = token,
                isDriveResolving = false,
                driveStatusText = if (resolvedId != null) "Google Drive document linked" else "Google Drive file selected"
            )
        )
    }

    fun submitAddSource(context: Context, podcastId: String) {
        val addSourceState = _uiState.value.addSource

        fun onCreated(created: Source) {
            _uiState.value = _uiState.value.copy(
                addSource = AddSourceUiState(),
                availableSources = listOf(created) + _uiState.value.availableSources,
                selectedSourceIds = _uiState.value.selectedSourceIds + created.id
            )
        }

        fun onFailed(e: Exception) {
            _uiState.value = _uiState.value.copy(
                addSource = _uiState.value.addSource.copy(
                    isUploading = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            )
        }

        when (addSourceState.addSourceMode) {
            AddSourceMode.PLAIN_TEXT -> {
                val title = addSourceState.sourceTitle.trim()
                val content = addSourceState.sourceContent.trim()
                if (title.isBlank() || content.isBlank()) return

                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(addSource = addSourceState.copy(isUploading = true, errorMessage = null))
                    try {
                        onCreated(sourceRepo.createTextSource(podcastId, title, content))
                    } catch (e: Exception) {
                        onFailed(e)
                    }
                }
            }

            AddSourceMode.LOCAL_FILE -> {
                val uri = addSourceState.selectedFileUri ?: return
                val title = addSourceState.sourceTitle.trim()
                val fileName = addSourceState.selectedFileName ?: "document"
                val finalTitle = title.ifBlank { fileName }
                val isText = addSourceState.isTextFile

                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(addSource = addSourceState.copy(isUploading = true, errorMessage = null))
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
                        onCreated(created)
                    } catch (e: Exception) {
                        onFailed(e)
                    }
                }
            }

            AddSourceMode.GOOGLE_DRIVE -> {
                val fileId = addSourceState.driveFileId
                val accessToken = addSourceState.driveAccessToken
                val title = addSourceState.sourceTitle.trim()
                val fileName = addSourceState.selectedFileName ?: "Google Drive Document"
                val finalTitle = title.ifBlank { fileName }

                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(addSource = addSourceState.copy(isUploading = true, errorMessage = null))
                    try {
                        val created = if (fileId != null && accessToken != null) {
                            sourceRepo.createDriveSource(
                                podcastId = podcastId,
                                fileId = fileId,
                                accessToken = accessToken,
                                title = finalTitle
                            )
                        } else {
                            val uri = addSourceState.selectedFileUri
                                ?: throw IllegalStateException("No Google Drive file selected")
                            if (addSourceState.isTextFile) {
                                val textContent = FileUtils.readTextFromUri(context, uri)
                                sourceRepo.createTextSource(podcastId, finalTitle, textContent)
                            } else {
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
                        onCreated(created)
                    } catch (e: Exception) {
                        onFailed(e)
                    }
                }
            }
        }
    }

    fun onSteeringPromptChanged(prompt: String) {
        _uiState.value = _uiState.value.copy(steeringPrompt = prompt)
    }

    // Auto-configure default speakers to meet the 2-speaker rule for a freshly
    // (re)generated or newly selected draft.
    private fun defaultSpeakerSelection(draft: EpisodeDraft?): Pair<Set<String>, List<EpisodeGuest>> {
        val hosts = _uiState.value.podcast?.hosts ?: emptyList()
        val selectedHosts = mutableSetOf<String>()
        val selectedGuests = mutableListOf<EpisodeGuest>()

        if (hosts.size >= 2) {
            selectedHosts.addAll(hosts.take(2).map { it.id })
        } else if (hosts.size == 1) {
            selectedHosts.add(hosts.first().id)
            if (draft != null && draft.guests.isNotEmpty()) {
                selectedGuests.add(draft.guests.first())
            }
        }
        return selectedHosts to selectedGuests
    }

    fun generateSuggestions(podcastId: String) {
        val sourceIds = _uiState.value.selectedSourceIds.toList()
        if (sourceIds.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please select at least one source for this episode")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDrafting = true, errorMessage = null)
            try {
                val suggestions = episodeRepo.generateEpisodeSuggestions(
                    podcastId = podcastId,
                    sourceIds = sourceIds,
                    length = _uiState.value.episodeLength,
                    prompt = _uiState.value.steeringPrompt.trim().ifBlank { null }
                )

                val (selectedHosts, selectedGuests) = defaultSpeakerSelection(
                    suggestions.firstOrNull()?.episodes?.firstOrNull()
                )

                _uiState.value = _uiState.value.copy(
                    isDrafting = false,
                    suggestions = suggestions,
                    selectedSuggestionIndex = 0,
                    selectedHostIds = selectedHosts,
                    selectedGuests = selectedGuests,
                    step = 2
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDrafting = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    fun selectSuggestion(index: Int) {
        if (index !in _uiState.value.suggestions.indices) return
        val draft = _uiState.value.suggestions[index].episodes.firstOrNull()
        val (selectedHosts, selectedGuests) = defaultSpeakerSelection(draft)
        _uiState.value = _uiState.value.copy(
            selectedSuggestionIndex = index,
            selectedHostIds = selectedHosts,
            selectedGuests = selectedGuests
        )
    }

    fun onRevisionInstructionChanged(instruction: String) {
        _uiState.value = _uiState.value.copy(revisionInstruction = instruction)
    }

    fun applyRevision(podcastId: String, instructionOverride: String? = null) {
        val currentSuggestions = _uiState.value.suggestions
        if (currentSuggestions.isEmpty()) return
        val instruction = (instructionOverride ?: _uiState.value.revisionInstruction).trim()
        if (instruction.isBlank()) return

        val targetIndex = _uiState.value.selectedSuggestionIndex

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRevising = true, errorMessage = null)
            try {
                val revised = episodeRepo.reviseEpisodeSuggestions(
                    podcastId = podcastId,
                    suggestions = currentSuggestions,
                    length = _uiState.value.episodeLength,
                    targetSuggestionIndex = targetIndex,
                    instruction = instruction
                )
                val clampedIndex = targetIndex.coerceIn(0, (revised.size - 1).coerceAtLeast(0))
                _uiState.value = _uiState.value.copy(
                    isRevising = false,
                    suggestions = revised,
                    selectedSuggestionIndex = clampedIndex,
                    revisionInstruction = if (instructionOverride != null) _uiState.value.revisionInstruction else ""
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRevising = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    fun setEpisodeLength(length: String) {
        _uiState.value = _uiState.value.copy(episodeLength = length)
    }

    fun toggleHostSelection(hostId: String) {
        val current = _uiState.value.selectedHostIds.toMutableSet()
        if (current.contains(hostId)) {
            current.remove(hostId)
        } else {
            val totalSpeakers = current.size + _uiState.value.selectedGuests.size
            if (totalSpeakers < 2) {
                current.add(hostId)
            }
        }
        _uiState.value = _uiState.value.copy(selectedHostIds = current)
    }

    fun toggleGuestSelection(guest: EpisodeGuest) {
        val current = _uiState.value.selectedGuests.toMutableList()
        val exists = current.any { it.name == guest.name }
        if (exists) {
            current.removeAll { it.name == guest.name }
        } else {
            val totalSpeakers = _uiState.value.selectedHostIds.size + current.size
            if (totalSpeakers < 2) {
                current.add(guest)
            }
        }
        _uiState.value = _uiState.value.copy(selectedGuests = current)
    }

    fun confirmAndStartGeneration(podcastId: String) {
        val draft = _uiState.value.selectedDraft ?: return
        val totalSpeakers = _uiState.value.selectedHostIds.size + _uiState.value.selectedGuests.size
        if (totalSpeakers != 2) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "You must select exactly 2 speakers (2 hosts, or 1 host + 1 guest)"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConfirming = true, errorMessage = null)
            try {
                val created = episodeRepo.createEpisode(
                    podcastId = podcastId,
                    title = draft.title,
                    topics = draft.topics,
                    length = _uiState.value.episodeLength,
                    sourceIds = _uiState.value.selectedSourceIds.toList(),
                    participantHostIds = _uiState.value.selectedHostIds.toList(),
                    guests = _uiState.value.selectedGuests,
                    productionNotes = draft.productionNotes
                )
                _uiState.value = _uiState.value.copy(
                    isConfirming = false,
                    confirmedEpisode = created
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConfirming = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }
}
