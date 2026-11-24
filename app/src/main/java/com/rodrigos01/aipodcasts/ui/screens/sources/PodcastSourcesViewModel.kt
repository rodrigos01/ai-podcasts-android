package com.rodrigos01.aipodcasts.ui.screens.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.api.userMessage
import com.rodrigos01.aipodcasts.data.model.Source
import com.rodrigos01.aipodcasts.data.repository.SourceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PodcastSourcesUiState(
    val isLoading: Boolean = true,
    val sources: List<Source> = emptyList(),
    val errorMessage: String? = null
)

/** Lists a show's sources and lets the user delete them (sources are added in the episode wizard). */
class PodcastSourcesViewModel(
    private val podcastId: String,
    private val sourceRepo: SourceRepository = AIPodcastsApplication.instance.sourceRepository
) : ViewModel() {

    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PodcastSourcesUiState> = combine(
        sourceRepo.getSourcesFlow(podcastId).catch { e ->
            errorMessage.value = e.userMessage()
            emit(emptyList())
        },
        errorMessage
    ) { sources, error ->
        PodcastSourcesUiState(isLoading = false, sources = sources, errorMessage = error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PodcastSourcesUiState()
    )

    fun deleteSource(sourceId: String) {
        viewModelScope.launch {
            try {
                sourceRepo.deleteSource(podcastId, sourceId)
            } catch (e: Exception) {
                errorMessage.value = e.userMessage()
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }

    companion object {
        fun provideFactory(podcastId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PodcastSourcesViewModel(podcastId) as T
            }
    }
}
