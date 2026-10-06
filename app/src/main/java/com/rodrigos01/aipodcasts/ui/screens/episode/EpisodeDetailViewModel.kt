package com.rodrigos01.aipodcasts.ui.screens.episode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.EpisodeGuest
import com.rodrigos01.aipodcasts.data.model.EpisodeProgress
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.repository.AuthRepository
import com.rodrigos01.aipodcasts.data.repository.EpisodeRepository
import com.rodrigos01.aipodcasts.data.repository.PlaybackPositionRepository
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
import com.rodrigos01.aipodcasts.player.PodcastAudioController
import com.rodrigos01.aipodcasts.ui.voice.VoiceDesignController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EpisodeDetailUiState(
    val isLoading: Boolean = false,
    val podcast: Podcast? = null,
    val episode: Episode? = null,
    val status: String = "generating",
    val progress: EpisodeProgress? = null,
    val isPolling: Boolean = false,
    val isRegenerating: Boolean = false,
    val errorMessage: String? = null,
    val savedPositionMs: Long = 0L,
    val showDeleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val showRegenerateConfirm: Boolean = false,
    val regenerateAfterEdit: Boolean = false,
    val showEditDialog: Boolean = false,
    val isSavingEdit: Boolean = false
)

private data class EpisodeDetailInternalFlags(
    val isRegenerating: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val showRegenerateConfirm: Boolean = false,
    val regenerateAfterEdit: Boolean = false,
    val showEditDialog: Boolean = false,
    val isSavingEdit: Boolean = false,
    val errorMessage: String? = null,
    val savedPositionMs: Long = 0L
)

class EpisodeDetailViewModel(
    val podcastId: String,
    val episodeId: String,
    private val podcastRepo: PodcastRepository = AIPodcastsApplication.instance.podcastRepository,
    private val episodeRepo: EpisodeRepository = AIPodcastsApplication.instance.episodeRepository,
    private val authRepo: AuthRepository = AIPodcastsApplication.instance.authRepository,
    private val audioController: PodcastAudioController = AIPodcastsApplication.instance.audioController,
    private val playbackPositionRepo: PlaybackPositionRepository = AIPodcastsApplication.instance.playbackPositionRepository
) : ViewModel() {

    private val _flags = MutableStateFlow(
        EpisodeDetailInternalFlags(
            savedPositionMs = playbackPositionRepo.getPositionMs(episodeId)
        )
    )

    val uiState: StateFlow<EpisodeDetailUiState> = combine(
        podcastRepo.getPodcastFlow(podcastId).catch { e ->
            _flags.value = _flags.value.copy(errorMessage = e.localizedMessage ?: e.message)
            emit(null)
        },
        episodeRepo.getEpisodeFlow(podcastId, episodeId).catch { e ->
            _flags.value = _flags.value.copy(errorMessage = e.localizedMessage ?: e.message)
            emit(null)
        },
        _flags
    ) { podcast, episode, flags ->
        EpisodeDetailUiState(
            isLoading = false,
            podcast = podcast,
            episode = episode,
            status = episode?.status ?: "generating",
            progress = episode?.progress,
            isPolling = false,
            isRegenerating = flags.isRegenerating,
            errorMessage = flags.errorMessage ?: episode?.error,
            savedPositionMs = flags.savedPositionMs,
            showDeleteConfirm = flags.showDeleteConfirm,
            isDeleting = flags.isDeleting,
            isDeleted = flags.isDeleted,
            showRegenerateConfirm = flags.showRegenerateConfirm,
            regenerateAfterEdit = flags.regenerateAfterEdit,
            showEditDialog = flags.showEditDialog,
            isSavingEdit = flags.isSavingEdit
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EpisodeDetailUiState(isLoading = true)
    )

    // When editing, the voice-design session is the episode id.
    val voiceDesign = VoiceDesignController(viewModelScope, podcastRepo::designVoices)

    override fun onCleared() {
        voiceDesign.release()
    }

    /** Opens the voice picker for [guest]; [onPicked] receives the chosen voice id and the prompt it came from. */
    fun chooseGuestVoice(guest: EpisodeGuest, onPicked: (voiceId: String, prompt: String) -> Unit) {
        voiceDesign.open(
            sessionId = episodeId,
            personName = guest.name,
            prompt = guest.voicePrompt ?: "Name: ${guest.name}\n\n${guest.persona}",
            currentVoiceId = guest.resolvedVoiceId,
            onPicked = onPicked
        )
    }

    fun refreshSavedPosition() {
        _flags.value = _flags.value.copy(
            savedPositionMs = playbackPositionRepo.getPositionMs(episodeId)
        )
    }

    fun playAudio(forceFromBeginning: Boolean = false) {
        val ep = uiState.value.episode ?: return
        val podcast = uiState.value.podcast

        // If this episode is already active in player and we're not forcing restart, simply toggle play
        if (!forceFromBeginning && audioController.currentEpisode.value?.id == ep.id) {
            if (!audioController.isPlaying.value) {
                audioController.togglePlayPause()
            }
            return
        }

        viewModelScope.launch {
            val token = authRepo.getIdToken()
            audioController.playEpisode(
                podcastId = podcast?.id ?: ep.podcastId,
                podcastTitle = podcast?.title ?: "",
                episode = ep,
                idToken = token,
                forceFromBeginning = forceFromBeginning
            )
            refreshSavedPosition()
        }
    }

    fun promptRegenerate() {
        _flags.value = _flags.value.copy(showRegenerateConfirm = true, showEditDialog = false)
    }

    fun dismissRegenerateConfirm() {
        _flags.value = _flags.value.copy(showRegenerateConfirm = false, regenerateAfterEdit = false)
    }

    fun promptEdit() {
        _flags.value = _flags.value.copy(showEditDialog = true)
    }

    fun dismissEdit() {
        _flags.value = _flags.value.copy(showEditDialog = false)
    }

    /** Saves the episode's editable metadata; on success offers to regenerate so the edits take effect. */
    fun saveEdit(title: String, topics: String, productionNotes: String, guests: List<EpisodeGuest>) {
        val current = uiState.value.episode ?: return
        val newTitle = title.trim()
        val newTopics = topics.trim()
        val newNotes = productionNotes.trim()
        val guestsChanged = guests != current.guests
        val changed = newTitle != current.title || newTopics != current.topics ||
            newNotes != (current.productionNotes ?: "") || guestsChanged
        if (!changed) {
            dismissEdit()
            return
        }
        viewModelScope.launch {
            _flags.value = _flags.value.copy(isSavingEdit = true, errorMessage = null)
            try {
                episodeRepo.updateEpisode(
                    podcastId,
                    episodeId,
                    title = newTitle.takeIf { it != current.title },
                    topics = newTopics.takeIf { it != current.topics },
                    productionNotes = newNotes.takeIf { it != (current.productionNotes ?: "") },
                    // The API wants the whole guest list back, with ids so existing guests are kept.
                    guests = guests.takeIf { guestsChanged }
                )
                _flags.value = _flags.value.copy(
                    isSavingEdit = false,
                    showEditDialog = false,
                    showRegenerateConfirm = true,
                    regenerateAfterEdit = true
                )
            } catch (e: Exception) {
                _flags.value = _flags.value.copy(
                    isSavingEdit = false,
                    errorMessage = e.localizedMessage ?: e.message ?: "Failed to save episode"
                )
            }
        }
    }

    fun confirmRegenerate(pId: String = podcastId, epId: String = episodeId) {
        viewModelScope.launch {
            _flags.value = _flags.value.copy(
                showRegenerateConfirm = false,
                regenerateAfterEdit = false,
                isRegenerating = true,
                errorMessage = null
            )
            try {
                val success = episodeRepo.regenerateEpisode(pId, epId)
                _flags.value = _flags.value.copy(
                    isRegenerating = false,
                    errorMessage = if (success) null else "Failed to regenerate episode"
                )
            } catch (e: Exception) {
                _flags.value = _flags.value.copy(
                    isRegenerating = false,
                    errorMessage = e.localizedMessage ?: e.message ?: "Failed to regenerate episode"
                )
            }
        }
    }

    fun clearActionError() {
        _flags.value = _flags.value.copy(errorMessage = null)
    }

    fun promptDelete() {
        _flags.value = _flags.value.copy(showDeleteConfirm = true)
    }

    fun dismissDeleteConfirm() {
        _flags.value = _flags.value.copy(showDeleteConfirm = false)
    }

    fun confirmDelete(pId: String = podcastId, epId: String = episodeId) {
        viewModelScope.launch {
            _flags.value = _flags.value.copy(isDeleting = true)
            try {
                episodeRepo.deleteEpisode(pId, epId)
                _flags.value = _flags.value.copy(
                    isDeleting = false,
                    showDeleteConfirm = false,
                    isDeleted = true
                )
            } catch (e: Exception) {
                _flags.value = _flags.value.copy(
                    isDeleting = false,
                    showDeleteConfirm = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    companion object {
        fun provideFactory(
            podcastId: String,
            episodeId: String,
            podcastRepo: PodcastRepository = AIPodcastsApplication.instance.podcastRepository,
            episodeRepo: EpisodeRepository = AIPodcastsApplication.instance.episodeRepository,
            authRepo: AuthRepository = AIPodcastsApplication.instance.authRepository,
            audioController: PodcastAudioController = AIPodcastsApplication.instance.audioController,
            playbackPositionRepo: PlaybackPositionRepository = AIPodcastsApplication.instance.playbackPositionRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EpisodeDetailViewModel(
                    podcastId,
                    episodeId,
                    podcastRepo,
                    episodeRepo,
                    authRepo,
                    audioController,
                    playbackPositionRepo
                ) as T
            }
        }
    }
}
