package com.rodrigos01.aipodcasts.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.model.Source
import com.rodrigos01.aipodcasts.data.repository.EpisodeRepository
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
import com.rodrigos01.aipodcasts.data.repository.SourceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PodcastDetailUiState(
    val isLoading: Boolean = false,
    val podcast: Podcast? = null,
    val episodes: List<Episode> = emptyList(),
    val sources: List<Source> = emptyList(),
    val selectedTab: Int = 0, // 0: Episodes, 1: Sources, 2: About Show
    val episodeToDelete: Episode? = null,
    val errorMessage: String? = null
)

private data class PodcastDetailInternalState(
    val selectedTab: Int = 0,
    val episodeToDelete: Episode? = null,
    val errorMessage: String? = null
)

class PodcastDetailViewModel(
    val podcastId: String,
    private val podcastRepo: PodcastRepository = AIPodcastsApplication.instance.podcastRepository,
    private val episodeRepo: EpisodeRepository = AIPodcastsApplication.instance.episodeRepository,
    private val sourceRepo: SourceRepository = AIPodcastsApplication.instance.sourceRepository
) : ViewModel() {

    private val _internalState = MutableStateFlow(PodcastDetailInternalState())

    val uiState: StateFlow<PodcastDetailUiState> = combine(
        podcastRepo.getPodcastFlow(podcastId).catch { e ->
            _internalState.value = _internalState.value.copy(errorMessage = e.localizedMessage ?: e.message)
            emit(null)
        },
        episodeRepo.getEpisodesFlow(podcastId).catch { e ->
            _internalState.value = _internalState.value.copy(errorMessage = e.localizedMessage ?: e.message)
            emit(emptyList())
        },
        sourceRepo.getSourcesFlow(podcastId).catch { e ->
            _internalState.value = _internalState.value.copy(errorMessage = e.localizedMessage ?: e.message)
            emit(emptyList())
        },
        _internalState
    ) { podcast, episodes, sources, internal ->
        PodcastDetailUiState(
            isLoading = false,
            podcast = podcast,
            episodes = episodes,
            sources = sources,
            selectedTab = internal.selectedTab,
            episodeToDelete = internal.episodeToDelete,
            errorMessage = internal.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PodcastDetailUiState(isLoading = true)
    )

    fun selectTab(index: Int) {
        _internalState.value = _internalState.value.copy(selectedTab = index)
    }

    fun deleteSource(sourceId: String) {
        viewModelScope.launch {
            try {
                sourceRepo.deleteSource(podcastId, sourceId)
            } catch (e: Exception) {
                _internalState.value = _internalState.value.copy(errorMessage = e.localizedMessage ?: e.message)
            }
        }
    }

    fun deleteSource(podcastId: String, sourceId: String) = deleteSource(sourceId)

    fun promptDeleteEpisode(episode: Episode) {
        _internalState.value = _internalState.value.copy(episodeToDelete = episode)
    }

    fun dismissDeleteEpisodeDialog() {
        _internalState.value = _internalState.value.copy(episodeToDelete = null)
    }

    fun confirmDeleteEpisode() {
        val episode = _internalState.value.episodeToDelete ?: return
        viewModelScope.launch {
            try {
                episodeRepo.deleteEpisode(podcastId, episode.id)
                _internalState.value = _internalState.value.copy(episodeToDelete = null)
            } catch (e: Exception) {
                _internalState.value = _internalState.value.copy(
                    episodeToDelete = null,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    fun confirmDeleteEpisode(podcastId: String) = confirmDeleteEpisode()

    companion object {
        fun provideFactory(
            podcastId: String,
            podcastRepo: PodcastRepository = AIPodcastsApplication.instance.podcastRepository,
            episodeRepo: EpisodeRepository = AIPodcastsApplication.instance.episodeRepository,
            sourceRepo: SourceRepository = AIPodcastsApplication.instance.sourceRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PodcastDetailViewModel(podcastId, podcastRepo, episodeRepo, sourceRepo) as T
            }
        }
    }
}
