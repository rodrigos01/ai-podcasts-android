package com.rodrigos01.aipodcasts.ui.screens.edit

import com.rodrigos01.aipodcasts.data.api.userMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.model.Host
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
import com.rodrigos01.aipodcasts.ui.voice.VoiceDesignController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PodcastEditUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val title: String = "",
    val description: String = "",
    val structure: String = "",
    val hosts: List<Host> = emptyList(),
    /** Voice ids picked in this screen, so those hosts can be flagged as having a new voice. */
    val pickedVoiceIds: Set<String> = emptySet(),
    val validationError: Boolean = false,
    val noHostsError: Boolean = false,
    val errorMessage: String? = null
)

class PodcastEditViewModel(
    private val podcastId: String,
    private val podcastRepo: PodcastRepository = AIPodcastsApplication.instance.podcastRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastEditUiState())
    val uiState: StateFlow<PodcastEditUiState> = _uiState.asStateFlow()

    private var original: Podcast? = null

    // The design session when editing is the podcast id.
    val voiceDesign = VoiceDesignController(viewModelScope, podcastRepo::designVoices)

    override fun onCleared() {
        voiceDesign.release()
    }

    fun chooseHostVoice(index: Int) {
        val host = _uiState.value.hosts.getOrNull(index) ?: return
        voiceDesign.open(
            sessionId = podcastId,
            personName = host.name.ifBlank { "host" },
            prompt = host.voicePrompt ?: "Name: ${host.name}\n\n${host.persona}",
            languageCode = original?.languageCode,
            currentVoiceId = host.resolvedVoiceId
        ) { voiceId, prompt ->
            _uiState.update { state ->
                // Match by id rather than position: hosts may have been added/removed meanwhile.
                val i = state.hosts.indexOfFirst { it.id == host.id && it.name == host.name }
                if (i < 0) state else state.copy(
                    hosts = state.hosts.toMutableList().also {
                        it[i] = it[i].copy(resolvedVoiceId = voiceId, voicePrompt = prompt)
                    },
                    pickedVoiceIds = state.pickedVoiceIds + voiceId
                )
            }
        }
    }

    init {
        viewModelScope.launch {
            try {
                val podcast = podcastRepo.getPodcast(podcastId)
                original = podcast
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        title = podcast.title,
                        description = podcast.description,
                        structure = podcast.structure,
                        hosts = podcast.hosts
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.userMessage())
                }
            }
        }
    }

    fun onTitleChanged(value: String) = _uiState.update { it.copy(title = value, validationError = false) }
    fun onDescriptionChanged(value: String) = _uiState.update { it.copy(description = value, validationError = false) }
    fun onStructureChanged(value: String) = _uiState.update { it.copy(structure = value, validationError = false) }

    fun onHostChanged(index: Int, host: Host) = _uiState.update {
        it.copy(
            hosts = it.hosts.toMutableList().also { list -> list[index] = host },
            validationError = false
        )
    }

    /** New hosts get a blank id, which the repository omits so the server assigns one. */
    fun addHost() = _uiState.update {
        it.copy(hosts = it.hosts + Host(name = "", voice = "", persona = ""), noHostsError = false)
    }

    fun removeHost(index: Int) = _uiState.update {
        it.copy(hosts = it.hosts.filterIndexed { i, _ -> i != index })
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun save() {
        val state = _uiState.value
        val before = original ?: return
        if (state.isSaving) return
        if (state.hosts.isEmpty()) {
            _uiState.update { it.copy(noHostsError = true) }
            return
        }
        val hosts = state.hosts.map {
            it.copy(name = it.name.trim(), voice = it.voice.trim(), persona = it.persona.trim())
        }
        val title = state.title.trim()
        val description = state.description.trim()
        val structure = state.structure.trim()
        if (title.isEmpty() || description.isEmpty() || structure.isEmpty() ||
            hosts.any { it.name.isEmpty() || it.voice.isEmpty() || it.persona.isEmpty() }
        ) {
            _uiState.update { it.copy(validationError = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                // Only send what changed; the server treats omitted fields as untouched.
                podcastRepo.updatePodcast(
                    id = podcastId,
                    title = title.takeIf { it != before.title },
                    description = description.takeIf { it != before.description },
                    structure = structure.takeIf { it != before.structure },
                    hosts = hosts.takeIf { it != before.hosts }
                )
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = e.userMessage())
                }
            }
        }
    }

    companion object {
        fun provideFactory(podcastId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PodcastEditViewModel(podcastId) as T
            }
    }
}
