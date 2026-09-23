package com.rodrigos01.aipodcasts.ui.screens.episode

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.drive.GoogleDriveHelper
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.EpisodeCreateInput
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

// One episode within a suggestion needs its own speakers - a split's two
// parts often cast a different guest (each has its own EpisodeDraft.guests).
data class SpeakerSelection(
    val hostIds: Set<String> = emptySet(),
    val guests: List<EpisodeGuest> = emptyList()
) {
    val totalSpeakers: Int get() = hostIds.size + guests.size
}

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
    // (a natural split) shown side by side in a horizontal pager.
    val suggestions: List<EpisodeSuggestion> = emptyList(),
    val selectedSuggestionIndex: Int = 0,
    // Which episode within the selected suggestion the pager currently shows -
    // drives revision targeting and which speaker selection is being edited.
    val selectedEpisodeIndex: Int = 0,
    // One entry per episode in the selected suggestion, same order.
    val speakerSelections: List<SpeakerSelection> = emptyList(),
    val revisionInstruction: String = "",
    val confirmedEpisode: Episode? = null,
    val errorMessage: String? = null
) {
    val currentEpisodes: List<EpisodeDraft>
        get() = suggestions.getOrNull(selectedSuggestionIndex)?.episodes ?: emptyList()

    val selectedDraft: EpisodeDraft?
        get() = currentEpisodes.getOrNull(selectedEpisodeIndex)

    val selectedSpeakerSelection: SpeakerSelection
        get() = speakerSelections.getOrNull(selectedEpisodeIndex) ?: SpeakerSelection()
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
    private fun defaultSpeakerSelection(draft: EpisodeDraft?): SpeakerSelection {
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
        return SpeakerSelection(selectedHosts, selectedGuests)
    }

    private fun defaultSpeakerSelections(episodes: List<EpisodeDraft>): List<SpeakerSelection> =
        episodes.map { defaultSpeakerSelection(it) }

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

                val firstEpisodes = suggestions.firstOrNull()?.episodes ?: emptyList()

                _uiState.value = _uiState.value.copy(
                    isDrafting = false,
                    suggestions = suggestions,
                    selectedSuggestionIndex = 0,
                    selectedEpisodeIndex = 0,
                    speakerSelections = defaultSpeakerSelections(firstEpisodes),
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
        val episodes = _uiState.value.suggestions[index].episodes
        _uiState.value = _uiState.value.copy(
            selectedSuggestionIndex = index,
            selectedEpisodeIndex = 0,
            speakerSelections = defaultSpeakerSelections(episodes)
        )
    }

    // Called as the horizontal pager settles on a page, so revisions and
    // speaker edits apply to whichever part of a split is currently shown.
    fun selectEpisodePage(index: Int) {
        if (index !in _uiState.value.currentEpisodes.indices || index == _uiState.value.selectedEpisodeIndex) return
        _uiState.value = _uiState.value.copy(selectedEpisodeIndex = index)
    }

    fun onRevisionInstructionChanged(instruction: String) {
        _uiState.value = _uiState.value.copy(revisionInstruction = instruction)
    }

    fun applyRevision(podcastId: String, instructionOverride: String? = null, episodeIndexOverride: Int? = null) {
        val currentSuggestions = _uiState.value.suggestions
        if (currentSuggestions.isEmpty()) return
        val instruction = (instructionOverride ?: _uiState.value.revisionInstruction).trim()
        if (instruction.isBlank()) return

        val targetSuggestionIndex = _uiState.value.selectedSuggestionIndex
        val episodeCount = _uiState.value.currentEpisodes.size
        val requestedEpisodeIndex = episodeIndexOverride ?: _uiState.value.selectedEpisodeIndex
        // Omit entirely when there's only one episode in the suggestion - "revise
        // just this one" and "revise the whole suggestion" are the same request.
        val targetEpisodeIndex = if (episodeCount > 1) requestedEpisodeIndex else null

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRevising = true, errorMessage = null)
            try {
                val revised = episodeRepo.reviseEpisodeSuggestions(
                    podcastId = podcastId,
                    suggestions = currentSuggestions,
                    length = _uiState.value.episodeLength,
                    targetSuggestionIndex = targetSuggestionIndex,
                    targetEpisodeIndex = targetEpisodeIndex,
                    instruction = instruction
                )
                val clampedSuggestionIndex = targetSuggestionIndex.coerceIn(0, (revised.size - 1).coerceAtLeast(0))
                val newEpisodes = revised.getOrNull(clampedSuggestionIndex)?.episodes ?: emptyList()
                val clampedEpisodeIndex = _uiState.value.selectedEpisodeIndex
                    .coerceIn(0, (newEpisodes.size - 1).coerceAtLeast(0))

                // Keep speaker picks for episodes that weren't touched by this
                // revision; only the revised episode(s) get fresh defaults.
                val previousSelections = _uiState.value.speakerSelections
                val newSpeakerSelections = newEpisodes.mapIndexed { index, draft ->
                    val wasRevised = targetEpisodeIndex == null || targetEpisodeIndex == index
                    if (!wasRevised && index < previousSelections.size) {
                        previousSelections[index]
                    } else {
                        defaultSpeakerSelection(draft)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isRevising = false,
                    suggestions = revised,
                    selectedSuggestionIndex = clampedSuggestionIndex,
                    selectedEpisodeIndex = clampedEpisodeIndex,
                    speakerSelections = newSpeakerSelections,
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

    private fun updateSelectedSpeakerSelection(transform: (SpeakerSelection) -> SpeakerSelection) {
        val index = _uiState.value.selectedEpisodeIndex
        val current = _uiState.value.speakerSelections
        if (index !in current.indices) return
        val updated = current.toMutableList()
        updated[index] = transform(updated[index])
        _uiState.value = _uiState.value.copy(speakerSelections = updated)
    }

    fun toggleHostSelection(hostId: String) {
        updateSelectedSpeakerSelection { selection ->
            val current = selection.hostIds.toMutableSet()
            if (current.contains(hostId)) {
                current.remove(hostId)
            } else if (selection.totalSpeakers < 2) {
                current.add(hostId)
            }
            selection.copy(hostIds = current)
        }
    }

    fun toggleGuestSelection(guest: EpisodeGuest) {
        updateSelectedSpeakerSelection { selection ->
            val current = selection.guests.toMutableList()
            val exists = current.any { it.name == guest.name }
            if (exists) {
                current.removeAll { it.name == guest.name }
            } else if (selection.totalSpeakers < 2) {
                current.add(guest)
            }
            selection.copy(guests = current)
        }
    }

    fun confirmAndStartGeneration(podcastId: String) {
        val episodes = _uiState.value.currentEpisodes
        if (episodes.isEmpty()) return
        val selections = _uiState.value.speakerSelections
        if (selections.size != episodes.size) return

        val invalidIndex = selections.indexOfFirst { it.totalSpeakers != 2 }
        if (invalidIndex != -1) {
            val label = if (episodes.size > 1) "Part ${invalidIndex + 1}" else "This episode"
            _uiState.value = _uiState.value.copy(
                errorMessage = "$label must have exactly 2 speakers (2 hosts, or 1 host + 1 guest)"
            )
            return
        }

        val sourceIds = _uiState.value.selectedSourceIds.toList()
        val length = _uiState.value.episodeLength
        val inputs = episodes.mapIndexed { index, draft ->
            EpisodeCreateInput(
                title = draft.title,
                topics = draft.topics,
                length = length,
                sourceIds = sourceIds,
                participantHostIds = selections[index].hostIds.toList(),
                guests = selections[index].guests,
                productionNotes = draft.productionNotes
            )
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConfirming = true, errorMessage = null)
            try {
                val created = episodeRepo.createEpisodes(podcastId, inputs)
                _uiState.value = _uiState.value.copy(
                    isConfirming = false,
                    confirmedEpisode = created.firstOrNull()
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
