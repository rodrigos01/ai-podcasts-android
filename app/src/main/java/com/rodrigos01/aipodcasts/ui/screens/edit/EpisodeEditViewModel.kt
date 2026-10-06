package com.rodrigos01.aipodcasts.ui.screens.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.api.userMessage
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.EpisodeGuest
import com.rodrigos01.aipodcasts.data.repository.EpisodeRepository
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
import com.rodrigos01.aipodcasts.ui.voice.VoiceDesignController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EpisodeEditUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isRegenerating: Boolean = false,
    val title: String = "",
    val topics: String = "",
    val notes: String = "",
    val guests: List<EpisodeGuest> = emptyList(),
    /** Voice ids picked on this screen, so those guests can be flagged as having a new voice. */
    val pickedVoiceIds: Set<String> = emptySet(),
    /** Shown after a save (or on tapping Regenerate): confirm redoing the episode. */
    val showRegenerateConfirm: Boolean = false,
    val regenerateAfterSave: Boolean = false,
    val validationError: Boolean = false,
    /** True once the screen has nothing left to do and should navigate back. */
    val isDone: Boolean = false,
    val errorMessage: String? = null
)

class EpisodeEditViewModel(
    private val podcastId: String,
    private val episodeId: String,
    private val episodeRepo: EpisodeRepository = AIPodcastsApplication.instance.episodeRepository,
    private val podcastRepo: PodcastRepository = AIPodcastsApplication.instance.podcastRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EpisodeEditUiState())
    val uiState: StateFlow<EpisodeEditUiState> = _uiState.asStateFlow()

    private var original: Episode? = null
    private var languageCode: String? = null

    // When editing, the voice-design session is the episode id.
    val voiceDesign = VoiceDesignController(viewModelScope, podcastRepo::designVoices)

    init {
        viewModelScope.launch {
            try {
                val episode = episodeRepo.getEpisode(podcastId, episodeId)
                original = episode
                languageCode = runCatching { podcastRepo.getPodcast(podcastId).languageCode }.getOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        title = episode.title,
                        topics = episode.topics,
                        notes = episode.productionNotes.orEmpty(),
                        guests = episode.guests
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.userMessage()) }
            }
        }
    }

    override fun onCleared() {
        voiceDesign.release()
    }

    fun onTitleChanged(value: String) = _uiState.update { it.copy(title = value, validationError = false) }
    fun onTopicsChanged(value: String) = _uiState.update { it.copy(topics = value, validationError = false) }
    fun onNotesChanged(value: String) = _uiState.update { it.copy(notes = value) }

    fun updateGuest(index: Int, name: String, persona: String) = editGuest(index) { it.copy(name = name, persona = persona) }

    /** Opens the voice picker for a guest; the picker is shown inside that guest's edit dialog. */
    fun chooseGuestVoice(index: Int) {
        val guest = _uiState.value.guests.getOrNull(index) ?: return
        voiceDesign.open(
            sessionId = episodeId,
            personName = guest.name,
            prompt = guest.voicePrompt ?: "Name: ${guest.name}\n\n${guest.persona}",
            languageCode = languageCode,
            currentVoiceId = guest.resolvedVoiceId
        ) { voiceId, prompt ->
            editGuest(index) { it.copy(resolvedVoiceId = voiceId, voicePrompt = prompt) }
            _uiState.update { it.copy(pickedVoiceIds = it.pickedVoiceIds + voiceId) }
        }
    }

    private fun editGuest(index: Int, transform: (EpisodeGuest) -> EpisodeGuest) = _uiState.update { state ->
        if (index !in state.guests.indices) state
        else state.copy(guests = state.guests.toMutableList().also { it[index] = transform(it[index]) })
    }

    /** Whether anything differs from what is stored; Regenerate is only offered when nothing does. */
    val hasUnsavedChanges: Boolean
        get() {
            val before = original ?: return false
            val s = _uiState.value
            return s.title.trim() != before.title || s.topics.trim() != before.topics ||
                s.notes.trim() != before.productionNotes.orEmpty() || s.guests != before.guests
        }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun save() {
        val before = original ?: return
        val state = _uiState.value
        if (state.isSaving) return
        val title = state.title.trim()
        val topics = state.topics.trim()
        val notes = state.notes.trim()
        // The API rejects empty strings for these, and for a guest's name and persona.
        if (title.isEmpty() || topics.isEmpty() || state.guests.any { it.name.isBlank() || it.persona.isBlank() } ||
            (notes.isEmpty() && !before.productionNotes.isNullOrEmpty())
        ) {
            _uiState.update { it.copy(validationError = true) }
            return
        }
        if (!hasUnsavedChanges) {
            _uiState.update { it.copy(isDone = true) }
            return
        }

        val guestsChanged = state.guests != before.guests
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                episodeRepo.updateEpisode(
                    podcastId,
                    episodeId,
                    title = title.takeIf { it != before.title },
                    topics = topics.takeIf { it != before.topics },
                    productionNotes = notes.takeIf { it != before.productionNotes.orEmpty() },
                    // The API wants the whole guest list back, with ids so existing guests are kept.
                    guests = state.guests.takeIf { guestsChanged }
                )
                _uiState.update { it.copy(isSaving = false, showRegenerateConfirm = true, regenerateAfterSave = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.userMessage() ?: "Failed to save episode") }
            }
        }
    }

    fun promptRegenerate() = _uiState.update { it.copy(showRegenerateConfirm = true, regenerateAfterSave = false) }

    /** Declining the regenerate prompt: after a save that finishes the edit; otherwise just closes it. */
    fun dismissRegenerate() = _uiState.update {
        it.copy(showRegenerateConfirm = false, isDone = it.regenerateAfterSave || it.isDone, regenerateAfterSave = false)
    }

    fun confirmRegenerate() {
        viewModelScope.launch {
            _uiState.update { it.copy(showRegenerateConfirm = false, isRegenerating = true, errorMessage = null) }
            try {
                val ok = episodeRepo.regenerateEpisode(podcastId, episodeId)
                _uiState.update {
                    if (ok) it.copy(isRegenerating = false, regenerateAfterSave = false, isDone = true)
                    else it.copy(isRegenerating = false, errorMessage = "Failed to regenerate episode")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRegenerating = false, errorMessage = e.userMessage() ?: "Failed to regenerate episode") }
            }
        }
    }

    companion object {
        fun provideFactory(podcastId: String, episodeId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EpisodeEditViewModel(podcastId, episodeId) as T
            }
    }
}
