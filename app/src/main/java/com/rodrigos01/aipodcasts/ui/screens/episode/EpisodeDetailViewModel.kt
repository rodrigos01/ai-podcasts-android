package com.rodrigos01.aipodcasts.ui.screens.episode

import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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
    val savedPositionMs: Long = 0L
)

class EpisodeDetailViewModel(
    private val podcastRepo: PodcastRepository = AIPodcastsApplication.instance.podcastRepository,
    private val episodeRepo: EpisodeRepository = AIPodcastsApplication.instance.episodeRepository,
    private val authRepo: AuthRepository = AIPodcastsApplication.instance.authRepository,
    private val audioController: PodcastAudioController = AIPodcastsApplication.instance.audioController,
    private val playbackPositionRepo: PlaybackPositionRepository = AIPodcastsApplication.instance.playbackPositionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EpisodeDetailUiState())
    val uiState: StateFlow<EpisodeDetailUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    fun loadEpisode(podcastId: String, episodeId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val podcast = podcastRepo.getPodcast(podcastId)
                val ep = episodeRepo.getEpisode(podcastId, episodeId)
                val savedPos = playbackPositionRepo.getPositionMs(episodeId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    podcast = podcast,
                    episode = ep,
                    status = ep.status,
                    progress = ep.progress,
                    savedPositionMs = savedPos
                )

                if (ep.status.equals("generating", ignoreCase = true)) {
                    startStatusPolling(podcastId, episodeId)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    private fun startStatusPolling(podcastId: String, episodeId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPolling = true)
            while (isActive) {
                delay(3000)
                try {
                    val statusRes = episodeRepo.getEpisodeStatus(podcastId, episodeId)
                    _uiState.value = _uiState.value.copy(
                        status = statusRes.status,
                        progress = statusRes.progress
                    )

                    if (statusRes.status.equals("ready", ignoreCase = true)) {
                        // Fetch full episode for transcript
                        val fullEp = episodeRepo.getEpisode(podcastId, episodeId)
                        _uiState.value = _uiState.value.copy(
                            episode = fullEp,
                            status = "ready",
                            isPolling = false
                        )
                        break
                    } else if (statusRes.status.equals("failed", ignoreCase = true)) {
                        _uiState.value = _uiState.value.copy(
                            isPolling = false,
                            errorMessage = statusRes.error ?: "Generation failed"
                        )
                        break
                    }
                } catch (e: Exception) {
                    // Continue polling even on intermittent network error
                }
            }
        }
    }

    fun refreshSavedPosition() {
        val epId = _uiState.value.episode?.id ?: return
        _uiState.value = _uiState.value.copy(
            savedPositionMs = playbackPositionRepo.getPositionMs(epId)
        )
    }

    fun playAudio(forceFromBeginning: Boolean = false) {
        val ep = _uiState.value.episode ?: return
        val podcast = _uiState.value.podcast

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

    fun regenerate(podcastId: String, episodeId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRegenerating = true, errorMessage = null)
            try {
                val ep = episodeRepo.regenerateEpisode(podcastId, episodeId)
                _uiState.value = _uiState.value.copy(
                    isRegenerating = false,
                    episode = ep,
                    status = ep.status
                )
                startStatusPolling(podcastId, episodeId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRegenerating = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
