package com.rodrigos01.aipodcasts.ui.screens.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.model.PodcastOption
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PodcastWizardUiState(
    val step: Int = 1, // 1: Prompt Input, 2: Choose & Revise Concept
    val prompt: String = "",
    val sourceMaterial: String = "",
    val isGenerating: Boolean = false,
    val isRevising: Boolean = false,
    val isCreating: Boolean = false,
    val options: List<PodcastOption> = emptyList(),
    val selectedOptionIndex: Int = 0,
    val revisionInstruction: String = "",
    val reviseTargetSelectedOnly: Boolean = true,
    val createdPodcast: Podcast? = null,
    val errorMessage: String? = null
)

class PodcastWizardViewModel(
    private val podcastRepo: PodcastRepository = AIPodcastsApplication.instance.podcastRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastWizardUiState())
    val uiState: StateFlow<PodcastWizardUiState> = _uiState.asStateFlow()

    fun onPromptChanged(prompt: String) {
        _uiState.value = _uiState.value.copy(prompt = prompt, errorMessage = null)
    }

    fun onSourceMaterialChanged(sourceMaterial: String) {
        _uiState.value = _uiState.value.copy(sourceMaterial = sourceMaterial)
    }

    fun onSelectOption(index: Int) {
        _uiState.value = _uiState.value.copy(selectedOptionIndex = index)
    }

    fun onRevisionInstructionChanged(instruction: String) {
        _uiState.value = _uiState.value.copy(revisionInstruction = instruction)
    }

    fun onReviseTargetChanged(selectedOnly: Boolean) {
        _uiState.value = _uiState.value.copy(reviseTargetSelectedOnly = selectedOnly)
    }

    fun generateOptions() {
        val prompt = _uiState.value.prompt.trim()
        if (prompt.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter a prompt to inspire your podcast")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, errorMessage = null)
            try {
                val material = _uiState.value.sourceMaterial.trim().ifBlank { null }
                val options = podcastRepo.generatePodcastOptions(prompt = prompt, sourceMaterial = material)
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    options = options,
                    selectedOptionIndex = 0,
                    step = 2
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    fun applyRevision() {
        val instruction = _uiState.value.revisionInstruction.trim()
        if (instruction.isBlank()) return

        val currentOptions = _uiState.value.options
        if (currentOptions.isEmpty()) return

        val targetIndex = if (_uiState.value.reviseTargetSelectedOnly) {
            _uiState.value.selectedOptionIndex
        } else {
            null
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRevising = true, errorMessage = null)
            try {
                val revised = podcastRepo.revisePodcastOptions(
                    options = currentOptions,
                    targetIndex = targetIndex,
                    instruction = instruction
                )
                _uiState.value = _uiState.value.copy(
                    isRevising = false,
                    options = revised,
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

    fun confirmAndCreatePodcast() {
        val options = _uiState.value.options
        val selectedIdx = _uiState.value.selectedOptionIndex
        if (options.isEmpty() || selectedIdx !in options.indices) return

        val chosen = options[selectedIdx]

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, errorMessage = null)
            try {
                val podcast = podcastRepo.createPodcast(
                    title = chosen.title,
                    description = chosen.description,
                    structure = chosen.structure,
                    hosts = chosen.hosts
                )
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdPodcast = podcast
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }
}
