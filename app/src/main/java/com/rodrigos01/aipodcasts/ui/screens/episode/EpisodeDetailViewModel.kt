package com.rodrigos01.aipodcasts.ui.screens.episode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.EpisodeProgress
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.repository.AuthRepository
import com.rodrigos01.aipodcasts.data.repository.EpisodeRepository
import com.rodrigos01.aipodcasts.data.repository.PlaybackPositionRepository
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
import com.rodrigos01.aipodcasts.player.PodcastAudioController
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
    val isDeleted: Boolean = false
)

private data class EpisodeDetailInternalFlags(
    val isRegenerating: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
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
            isDeleted = flags.isDeleted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EpisodeDetailUiState(isLoading = true)
    )

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

    fun regenerate(pId: String = podcastId, epId: String = episodeId) {
        viewModelScope.launch {
            _flags.value = _flags.value.copy(isRegenerating = true, errorMessage = null)
            try {
                episodeRepo.regenerateEpisode(pId, epId)
                _flags.value = _flags.value.copy(isRegenerating = false)
            } catch (e: Exception) {
                _flags.value = _flags.value.copy(
                    isRegenerating = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
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
