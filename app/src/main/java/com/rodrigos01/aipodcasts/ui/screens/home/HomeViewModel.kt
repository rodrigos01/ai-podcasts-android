package com.rodrigos01.aipodcasts.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    private val _podcastToDelete = MutableStateFlow<Podcast?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        podcastRepo.getPodcastsFlow().catch { e ->
            _errorMessage.value = e.localizedMessage ?: e.message
            emit(emptyList())
        },
        _podcastToDelete,
        _errorMessage
    ) { podcasts, toDelete, error ->
        HomeUiState(
            isLoading = false,
            podcasts = podcasts,
            podcastToDelete = toDelete,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun promptDeletePodcast(podcast: Podcast) {
        _podcastToDelete.value = podcast
    }

    fun dismissDeleteDialog() {
        _podcastToDelete.value = null
    }

    fun confirmDeletePodcast() {
        val podcast = _podcastToDelete.value ?: return
        viewModelScope.launch {
            try {
                podcastRepo.deletePodcast(podcast.id)
                _podcastToDelete.value = null
            } catch (e: Exception) {
                _podcastToDelete.value = null
                _errorMessage.value = e.localizedMessage ?: e.message
            }
        }
    }
}
