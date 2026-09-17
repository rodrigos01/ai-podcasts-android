package com.rodrigos01.aipodcasts.ui.screens.episode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.EpisodeDraft
import com.rodrigos01.aipodcasts.data.model.EpisodeGuest
import com.rodrigos01.aipodcasts.data.model.Host
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.model.Source
import com.rodrigos01.aipodcasts.data.repository.EpisodeRepository
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
import com.rodrigos01.aipodcasts.data.repository.SourceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EpisodeWizardUiState(
    val step: Int = 1, // 1: Sources & Prompt, 2: Draft Review & Speaker Configuration
    val podcast: Podcast? = null,
    val availableSources: List<Source> = emptyList(),
    val selectedSourceIds: Set<String> = emptySet(),
    val steeringPrompt: String = "",
    val isDrafting: Boolean = false,
    val isRevising: Boolean = false,
    val isConfirming: Boolean = false,
    val draft: EpisodeDraft? = null,
    val revisionInstruction: String = "",
    val episodeLength: String = "short", // "short", "medium", "long"
    val selectedHostIds: Set<String> = emptySet(),
    val selectedGuests: List<EpisodeGuest> = emptyList(),
    val confirmedEpisode: Episode? = null,
    val errorMessage: String? = null
)

class EpisodeWizardViewModel(
    private val podcastRepo: PodcastRepository = AIPodcastsApplication.instance.podcastRepository,
    private val sourceRepo: SourceRepository = AIPodcastsApplication.instance.sourceRepository,
    private val episodeRepo: EpisodeRepository = AIPodcastsApplication.instance.episodeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EpisodeWizardUiState())
    val uiState: StateFlow<EpisodeWizardUiState> = _uiState.asStateFlow()

    fun initWizard(podcastId: String) {
        viewModelScope.launch {
            try {
                val podcast = podcastRepo.getPodcast(podcastId)
                val sources = sourceRepo.getSources(podcastId)
                _uiState.value = _uiState.value.copy(
                    podcast = podcast,
                    availableSources = sources,
                    selectedSourceIds = sources.map { it.id }.toSet()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage ?: e.message)
            }
        }
    }

    fun toggleSourceSelection(sourceId: String) {
        val current = _uiState.value.selectedSourceIds.toMutableSet()
        if (current.contains(sourceId)) {
            current.remove(sourceId)
        } else {
            current.add(sourceId)
        }
        _uiState.value = _uiState.value.copy(selectedSourceIds = current)
    }

    fun onSteeringPromptChanged(prompt: String) {
        _uiState.value = _uiState.value.copy(steeringPrompt = prompt)
    }

    fun generateDraft(podcastId: String) {
        val sourceIds = _uiState.value.selectedSourceIds.toList()
        if (sourceIds.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please select at least one source for this episode")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDrafting = true, errorMessage = null)
            try {
                val draft = episodeRepo.generateEpisodeDraft(
                    podcastId = podcastId,
                    sourceIds = sourceIds,
                    prompt = _uiState.value.steeringPrompt.trim().ifBlank { null }
                )

                // Auto-configure default speakers to meet 2-speaker rule
                val hosts = _uiState.value.podcast?.hosts ?: emptyList()
                val selectedHosts = mutableSetOf<String>()
                val selectedGuests = mutableListOf<EpisodeGuest>()

                if (hosts.size >= 2) {
                    selectedHosts.addAll(hosts.take(2).map { it.id })
                } else if (hosts.size == 1) {
                    selectedHosts.add(hosts.first().id)
                    if (draft.guests.isNotEmpty()) {
                        selectedGuests.add(draft.guests.first())
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isDrafting = false,
                    draft = draft,
                    selectedHostIds = selectedHosts,
                    selectedGuests = selectedGuests,
                    step = 2
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDrafting = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    fun onRevisionInstructionChanged(instruction: String) {
        _uiState.value = _uiState.value.copy(revisionInstruction = instruction)
    }

    fun applyRevision(podcastId: String) {
        val currentDraft = _uiState.value.draft ?: return
        val instruction = _uiState.value.revisionInstruction.trim()
        if (instruction.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRevising = true, errorMessage = null)
            try {
                val revised = episodeRepo.reviseEpisodeDraft(podcastId, currentDraft, instruction)
                _uiState.value = _uiState.value.copy(
                    isRevising = false,
                    draft = revised,
                    revisionInstruction = ""
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRevising = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    fun setEpisodeLength(length: String) {
        _uiState.value = _uiState.value.copy(episodeLength = length)
    }

    fun toggleHostSelection(hostId: String) {
        val current = _uiState.value.selectedHostIds.toMutableSet()
        if (current.contains(hostId)) {
            current.remove(hostId)
        } else {
            val totalSpeakers = current.size + _uiState.value.selectedGuests.size
            if (totalSpeakers < 2) {
                current.add(hostId)
            }
        }
        _uiState.value = _uiState.value.copy(selectedHostIds = current)
    }

    fun toggleGuestSelection(guest: EpisodeGuest) {
        val current = _uiState.value.selectedGuests.toMutableList()
        val exists = current.any { it.name == guest.name }
        if (exists) {
            current.removeAll { it.name == guest.name }
        } else {
            val totalSpeakers = _uiState.value.selectedHostIds.size + current.size
            if (totalSpeakers < 2) {
                current.add(guest)
            }
        }
        _uiState.value = _uiState.value.copy(selectedGuests = current)
    }

    fun confirmAndStartGeneration(podcastId: String) {
        val draft = _uiState.value.draft ?: return
        val totalSpeakers = _uiState.value.selectedHostIds.size + _uiState.value.selectedGuests.size
        if (totalSpeakers != 2) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "You must select exactly 2 speakers (2 hosts, or 1 host + 1 guest)"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConfirming = true, errorMessage = null)
            try {
                val created = episodeRepo.createEpisode(
                    podcastId = podcastId,
                    title = draft.title,
                    topics = draft.topics,
                    length = _uiState.value.episodeLength,
                    sourceIds = _uiState.value.selectedSourceIds.toList(),
                    participantHostIds = _uiState.value.selectedHostIds.toList(),
                    guests = _uiState.value.selectedGuests,
                    productionNotes = draft.productionNotes
                )
                _uiState.value = _uiState.value.copy(
                    isConfirming = false,
                    confirmedEpisode = created
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConfirming = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }
}
