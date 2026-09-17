package com.rodrigos01.aipodcasts.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val podcasts: List<Podcast> = emptyList(),
    val errorMessage: String? = null,
    val podcastToDelete: Podcast? = null
)

class HomeViewModel(
    private val podcastRepo: PodcastRepository = AIPodcastsApplication.instance.podcastRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadPodcasts()
    }

    fun loadPodcasts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val list = podcastRepo.getPodcasts()
                _uiState.value = _uiState.value.copy(isLoading = false, podcasts = list)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    fun promptDeletePodcast(podcast: Podcast) {
        _uiState.value = _uiState.value.copy(podcastToDelete = podcast)
    }

    fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(podcastToDelete = null)
    }

    fun confirmDeletePodcast() {
        val podcast = _uiState.value.podcastToDelete ?: return
        viewModelScope.launch {
            try {
                podcastRepo.deletePodcast(podcast.id)
                _uiState.value = _uiState.value.copy(
                    podcastToDelete = null,
                    podcasts = _uiState.value.podcasts.filter { it.id != podcast.id }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    podcastToDelete = null,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }
}
