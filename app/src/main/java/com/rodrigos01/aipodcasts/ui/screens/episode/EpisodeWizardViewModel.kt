package com.rodrigos01.aipodcasts.ui.screens.episode

import com.rodrigos01.aipodcasts.data.api.userMessage
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
import com.rodrigos01.aipodcasts.ui.voice.VoiceDesignController
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
import com.rodrigos01.aipodcasts.data.repository.SourceRepository
import com.rodrigos01.aipodcasts.util.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State of adding sources to the episode. Files and Drive documents are added straight after being
 * picked (shown as a pending row while they upload); plain text and the existing-sources list each
 * have their own dialog.
 */
data class AddSourceUiState(
    val showTextDialog: Boolean = false,
    val showExistingDialog: Boolean = false,
    /** A source is being created (uploaded / read / linked). */
    val isAdding: Boolean = false,
    /** Display name of the file or Drive document being added, if any. */
    val pendingName: String? = null,
    val sourceTitle: String = "",
    val sourceContent: String = "",
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
    // Voice-design session handed out by the wizard; echoed on revise and create.
    val sessionId: String? = null,
    // The podcast's language, returned by the wizard; used when designing guest voices.
    val languageCode: String? = null,
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

    // Designs go through the podcast repository's voices endpoint; sessions are the wizard's sessionId.
    val voiceDesign = VoiceDesignController(viewModelScope, podcastRepo::designVoices)

    override fun onCleared() {
        voiceDesign.release()
    }

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
                _uiState.value = _uiState.value.copy(errorMessage = e.userMessage())
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

    /** Replaces the episode's sources with [ids] (from the "existing sources" dialog). */
    fun setSelectedSources(ids: Set<String>) {
        val known = _uiState.value.availableSources.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(
            selectedSourceIds = ids.filter { it in known }.toSet(),
            addSource = _uiState.value.addSource.copy(showExistingDialog = false)
        )
    }

    // --- Adding sources: local file / Google Drive (pick, then add straight away), plain text, existing ---

    private fun updateAddSource(transform: (AddSourceUiState) -> AddSourceUiState) {
        _uiState.value = _uiState.value.copy(addSource = transform(_uiState.value.addSource))
    }

    fun openExistingSourcesDialog() = updateAddSource { it.copy(showExistingDialog = true, errorMessage = null) }

    fun dismissExistingSourcesDialog() = updateAddSource { it.copy(showExistingDialog = false) }

    fun openTextSourceDialog() = updateAddSource {
        it.copy(showTextDialog = true, sourceTitle = "", sourceContent = "", errorMessage = null)
    }

    fun dismissTextSourceDialog() = updateAddSource {
        if (it.isAdding) it else it.copy(showTextDialog = false, errorMessage = null)
    }

    fun onAddSourceTitleChanged(title: String) = updateAddSource { it.copy(sourceTitle = title) }

    fun onAddSourceContentChanged(content: String) = updateAddSource { it.copy(sourceContent = content) }

    fun setAddSourceError(message: String?) = updateAddSource {
        it.copy(errorMessage = message, isAdding = false, pendingName = null)
    }

    /** Creates a source via [create]; on success it is added to the list and selected for the episode. */
    private fun addSource(pendingName: String?, create: suspend () -> Source) {
        updateAddSource { it.copy(isAdding = true, pendingName = pendingName, errorMessage = null) }
        viewModelScope.launch {
            try {
                val created = create()
                _uiState.value = _uiState.value.copy(
                    addSource = AddSourceUiState(),
                    availableSources = listOf(created) + _uiState.value.availableSources,
                    selectedSourceIds = _uiState.value.selectedSourceIds + created.id
                )
            } catch (e: Exception) {
                updateAddSource {
                    it.copy(isAdding = false, pendingName = null, errorMessage = e.userMessage())
                }
            }
        }
    }

    fun submitTextSource(podcastId: String) {
        val state = _uiState.value.addSource
        val title = state.sourceTitle.trim()
        val content = state.sourceContent.trim()
        if (state.isAdding || title.isBlank() || content.isBlank()) return
        addSource(pendingName = null) { sourceRepo.createTextSource(podcastId, title, content) }
    }

    fun onAddSourceLocalFileSelected(context: Context, podcastId: String, uri: Uri) {
        if (_uiState.value.addSource.isAdding) return
        val appContext = context.applicationContext
        val fileName = FileUtils.getFileName(appContext, uri)
        val title = FileUtils.cleanFileName(fileName) ?: fileName
        val isText = FileUtils.isTextFile(appContext, uri, fileName)
        addSource(pendingName = fileName) {
            uploadFromUri(appContext, podcastId, uri, fileName, title, isText, "upload_pdf_temp.pdf")
        }
    }

    private suspend fun uploadFromUri(
        context: Context,
        podcastId: String,
        uri: Uri,
        fileName: String,
        title: String,
        isText: Boolean,
        tempName: String
    ): Source =
        if (isText) {
            sourceRepo.createTextSource(podcastId, title, FileUtils.readTextFromUri(context, uri))
        } else {
            val tempFile = FileUtils.copyUriToTempFile(context, uri, tempName)
            try {
                sourceRepo.uploadPdfSource(
                    podcastId = podcastId,
                    file = tempFile,
                    fileName = fileName,
                    customTitle = title
                )
            } finally {
                tempFile.delete()
            }
        }

    // A Google Drive pick may need a consent screen in between, so what was picked is kept here
    // until authorization completes.
    private class PendingDrive(
        val podcastId: String,
        val uri: Uri,
        val rawTitle: String,
        val cleanTitle: String,
        val isText: Boolean,
        val docId: String?
    )

    private var pendingDrive: PendingDrive? = null

    fun onAddSourceDriveFileSelected(
        context: Context,
        podcastId: String,
        uri: Uri,
        accountEmail: String? = null,
        onNeedConsent: (androidx.activity.result.IntentSenderRequest) -> Unit
    ) {
        if (_uiState.value.addSource.isAdding) return
        val appContext = context.applicationContext
        val rawTitle = FileUtils.getRawDisplayName(appContext, uri) ?: "Google Drive Document"
        val pending = PendingDrive(
            podcastId = podcastId,
            uri = uri,
            rawTitle = rawTitle,
            cleanTitle = FileUtils.cleanFileName(rawTitle) ?: rawTitle,
            isText = FileUtils.isTextFile(appContext, uri, rawTitle),
            docId = GoogleDriveHelper.extractGoogleDocId(uri)
        )
        pendingDrive = pending
        updateAddSource { it.copy(isAdding = true, pendingName = rawTitle, errorMessage = null) }

        viewModelScope.launch {
            try {
                val authResult = GoogleDriveHelper.requestDocsAuthorization(appContext, accountEmail)
                if (authResult.hasResolution()) {
                    val pendingIntent = authResult.pendingIntent
                    if (pendingIntent != null) {
                        onNeedConsent(
                            androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        failDrive("Authorization failed: no resolution intent")
                    }
                } else {
                    val token = authResult.accessToken
                    if (token != null) {
                        finishDriveAdd(appContext, pending, token)
                    } else {
                        failDrive("Failed to obtain Google Drive access token")
                    }
                }
            } catch (e: Exception) {
                failDrive(e.localizedMessage ?: "Failed to authorize Google Drive access")
            }
        }
    }

    fun onAddSourceDriveConsentResult(context: Context, data: Intent?) {
        val appContext = context.applicationContext
        val pending = pendingDrive
        val token = GoogleDriveHelper.getAuthorizationResultFromIntent(appContext, data)?.accessToken
        if (pending == null) {
            failDrive("No Google Drive file selected")
        } else if (token == null) {
            failDrive("Google Drive authorization was not granted.")
        } else {
            viewModelScope.launch { finishDriveAdd(appContext, pending, token) }
        }
    }

    private fun failDrive(message: String) {
        pendingDrive = null
        setAddSourceError(message)
    }

    private suspend fun finishDriveAdd(context: Context, pending: PendingDrive, token: String) {
        pendingDrive = null
        try {
            val fileId = pending.docId
                ?: GoogleDriveHelper.resolveDriveFileId(pending.rawTitle, pending.cleanTitle, token)
            val created = if (fileId != null) {
                sourceRepo.createDriveSource(
                    podcastId = pending.podcastId,
                    fileId = fileId,
                    accessToken = token,
                    title = pending.cleanTitle
                )
            } else {
                // Couldn't link it in Drive: fall back to uploading the picked file's content.
                uploadFromUri(
                    context, pending.podcastId, pending.uri, pending.rawTitle,
                    pending.cleanTitle, pending.isText, "upload_drive_temp.pdf"
                )
            }
            _uiState.value = _uiState.value.copy(
                addSource = AddSourceUiState(),
                availableSources = listOf(created) + _uiState.value.availableSources,
                selectedSourceIds = _uiState.value.selectedSourceIds + created.id
            )
        } catch (e: Exception) {
            setAddSourceError(e.userMessage())
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
                val result = episodeRepo.generateEpisodeSuggestions(
                    podcastId = podcastId,
                    sourceIds = sourceIds,
                    length = _uiState.value.episodeLength,
                    prompt = _uiState.value.steeringPrompt.trim().ifBlank { null }
                )
                val suggestions = result.suggestions

                val firstEpisodes = suggestions.firstOrNull()?.episodes ?: emptyList()

                _uiState.value = _uiState.value.copy(
                    isDrafting = false,
                    suggestions = suggestions,
                    sessionId = result.sessionId,
                    languageCode = result.languageCode,
                    selectedSuggestionIndex = 0,
                    selectedEpisodeIndex = 0,
                    speakerSelections = defaultSpeakerSelections(firstEpisodes),
                    step = 2
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDrafting = false,
                    errorMessage = e.userMessage()
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

        val selectedEpisodes = _uiState.value.currentEpisodes
        if (selectedEpisodes.any { it.title.isBlank() || it.topics.isBlank() || it.guests.any { g -> g.name.isBlank() || g.persona.isBlank() } }) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Fill in every episode title and topics, and every guest's name and persona, before revising"
            )
            return
        }

        val targetSuggestionIndex = _uiState.value.selectedSuggestionIndex
        val episodeCount = _uiState.value.currentEpisodes.size
        val requestedEpisodeIndex = episodeIndexOverride ?: _uiState.value.selectedEpisodeIndex
        // Omit entirely when there's only one episode in the suggestion - "revise
        // just this one" and "revise the whole suggestion" are the same request.
        val targetEpisodeIndex = if (episodeCount > 1) requestedEpisodeIndex else null

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRevising = true, errorMessage = null)
            try {
                val result = episodeRepo.reviseEpisodeSuggestions(
                    podcastId = podcastId,
                    suggestions = currentSuggestions,
                    length = _uiState.value.episodeLength,
                    targetSuggestionIndex = targetSuggestionIndex,
                    targetEpisodeIndex = targetEpisodeIndex,
                    instruction = instruction,
                    sessionId = _uiState.value.sessionId
                )
                val revised = result.suggestions
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
                    sessionId = result.sessionId ?: _uiState.value.sessionId,
                    languageCode = result.languageCode ?: _uiState.value.languageCode,
                    selectedSuggestionIndex = clampedSuggestionIndex,
                    selectedEpisodeIndex = clampedEpisodeIndex,
                    speakerSelections = newSpeakerSelections,
                    revisionInstruction = if (instructionOverride != null) _uiState.value.revisionInstruction else ""
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRevising = false,
                    errorMessage = e.userMessage()
                )
            }
        }
    }

    /** Applies a manual edit to the draft currently shown in the pager. */
    fun updateSelectedDraft(transform: (EpisodeDraft) -> EpisodeDraft) {
        val state = _uiState.value
        val suggestionIndex = state.selectedSuggestionIndex
        val episodeIndex = state.selectedEpisodeIndex
        val suggestion = state.suggestions.getOrNull(suggestionIndex) ?: return
        if (episodeIndex !in suggestion.episodes.indices) return
        val episodes = suggestion.episodes.toMutableList()
        episodes[episodeIndex] = transform(episodes[episodeIndex])
        _uiState.value = state.copy(
            suggestions = state.suggestions.toMutableList().also {
                it[suggestionIndex] = suggestion.copy(episodes = episodes)
            },
            errorMessage = null
        )
    }

    /** Edits a guest of the current draft, keeping an already-selected copy of that guest in sync. */
    fun updateGuest(guestIndex: Int, name: String, persona: String) =
        editGuest(guestIndex) { it.copy(name = name, persona = persona) }

    /** Opens the voice picker for a guest of the current draft. */
    fun chooseGuestVoice(guestIndex: Int) {
        val sessionId = _uiState.value.sessionId ?: return
        val guest = _uiState.value.selectedDraft?.guests?.getOrNull(guestIndex) ?: return
        voiceDesign.open(
            sessionId = sessionId,
            personName = guest.name,
            prompt = guest.voicePrompt ?: "Name: ${guest.name}\n\n${guest.persona}",
            languageCode = _uiState.value.languageCode ?: _uiState.value.podcast?.languageCode,
            currentVoiceId = guest.resolvedVoiceId
        ) { voiceId, prompt ->
            editGuest(guestIndex) { it.copy(resolvedVoiceId = voiceId, voicePrompt = prompt) }
        }
    }

    private fun editGuest(guestIndex: Int, transform: (EpisodeGuest) -> EpisodeGuest) {
        val old = _uiState.value.selectedDraft?.guests?.getOrNull(guestIndex) ?: return
        val updated = transform(old)
        updateSelectedDraft { draft ->
            draft.copy(guests = draft.guests.toMutableList().also { it[guestIndex] = updated })
        }
        updateSelectedSpeakerSelection { selection ->
            selection.copy(guests = selection.guests.map { if (it.name == old.name) updated else it })
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

        if (episodes.any { it.title.isBlank() || it.topics.isBlank() || it.guests.any { g -> g.name.isBlank() || g.persona.isBlank() } }) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Every episode needs a title and topics, and every guest a name and persona"
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
                productionNotes = draft.productionNotes?.trim()?.ifBlank { null },
                extras = draft.extras
            )
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConfirming = true, errorMessage = null)
            try {
                val created = episodeRepo.createEpisodes(podcastId, inputs, _uiState.value.sessionId)
                _uiState.value = _uiState.value.copy(
                    isConfirming = false,
                    confirmedEpisode = created.firstOrNull()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConfirming = false,
                    errorMessage = e.userMessage()
                )
            }
        }
    }
}
