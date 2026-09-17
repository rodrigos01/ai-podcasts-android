package com.rodrigos01.aipodcasts.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.model.Source
import com.rodrigos01.aipodcasts.data.repository.EpisodeRepository
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
import com.rodrigos01.aipodcasts.data.repository.SourceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PodcastDetailUiState(
    val isLoading: Boolean = false,
    val podcast: Podcast? = null,
    val episodes: List<Episode> = emptyList(),
    val sources: List<Source> = emptyList(),
    val selectedTab: Int = 0, // 0: Episodes, 1: Sources, 2: About Show
    val errorMessage: String? = null
)

class PodcastDetailViewModel(
    private val podcastRepo: PodcastRepository = AIPodcastsApplication.instance.podcastRepository,
    private val episodeRepo: EpisodeRepository = AIPodcastsApplication.instance.episodeRepository,
    private val sourceRepo: SourceRepository = AIPodcastsApplication.instance.sourceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastDetailUiState())
    val uiState: StateFlow<PodcastDetailUiState> = _uiState.asStateFlow()

    fun loadPodcast(podcastId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val podcast = podcastRepo.getPodcast(podcastId)
                val episodes = try { episodeRepo.getEpisodes(podcastId) } catch (e: Exception) { emptyList() }
                val sources = try { sourceRepo.getSources(podcastId) } catch (e: Exception) { emptyList() }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    podcast = podcast,
                    episodes = episodes,
                    sources = sources
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
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
