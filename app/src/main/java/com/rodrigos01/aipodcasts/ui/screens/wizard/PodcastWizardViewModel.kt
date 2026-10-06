package com.rodrigos01.aipodcasts.ui.screens.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.model.PodcastOption
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
import com.rodrigos01.aipodcasts.ui.voice.VoiceDesignController
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
    // Voice-design session handed out by the wizard; echoed on revise and create.
    val sessionId: String? = null,
    val createdPodcast: Podcast? = null,
    val errorMessage: String? = null
)

class PodcastWizardViewModel(
    private val podcastRepo: PodcastRepository = AIPodcastsApplication.instance.podcastRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastWizardUiState())
    val uiState: StateFlow<PodcastWizardUiState> = _uiState.asStateFlow()

    val voiceDesign = VoiceDesignController(viewModelScope, podcastRepo::designVoices)

    override fun onCleared() {
        voiceDesign.release()
    }

    /** Opens the voice picker for a host of the selected option. */
    fun chooseHostVoice(hostIndex: Int) {
        val state = _uiState.value
        val sessionId = state.sessionId ?: return
        val host = state.options.getOrNull(state.selectedOptionIndex)?.hosts?.getOrNull(hostIndex) ?: return
        voiceDesign.open(
            sessionId = sessionId,
            personName = host.name,
            prompt = host.voicePrompt ?: "Name: ${host.name}\n\n${host.persona}",
            languageCode = state.options.getOrNull(state.selectedOptionIndex)?.languageCode,
            currentVoiceId = host.resolvedVoiceId
        ) { voiceId, prompt ->
            updateSelectedOption { option ->
                option.copy(
                    hosts = option.hosts.toMutableList().also {
                        if (hostIndex in it.indices) it[hostIndex] = it[hostIndex].copy(resolvedVoiceId = voiceId, voicePrompt = prompt)
                    }
                )
            }
        }
    }

    fun onPromptChanged(prompt: String) {
        _uiState.value = _uiState.value.copy(prompt = prompt, errorMessage = null)
    }

    fun onSourceMaterialChanged(sourceMaterial: String) {
        _uiState.value = _uiState.value.copy(sourceMaterial = sourceMaterial)
    }

    fun onSelectOption(index: Int) {
        _uiState.value = _uiState.value.copy(selectedOptionIndex = index)
    }

    /** Applies a manual edit to the selected option; the edited option is what gets revised/created. */
    fun updateSelectedOption(transform: (PodcastOption) -> PodcastOption) {
        val state = _uiState.value
        val index = state.selectedOptionIndex
        if (index !in state.options.indices) return
        _uiState.value = state.copy(
            options = state.options.toMutableList().also { it[index] = transform(it[index]) },
            errorMessage = null
        )
    }

    fun updateHost(hostIndex: Int, name: String, persona: String) {
        updateSelectedOption { option ->
            if (hostIndex !in option.hosts.indices) option
            else option.copy(
                hosts = option.hosts.toMutableList().also {
                    it[hostIndex] = it[hostIndex].copy(name = name, persona = persona)
                }
            )
        }
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
                val result = podcastRepo.generatePodcastOptions(prompt = prompt, sourceMaterial = material)
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    options = result.options,
                    sessionId = result.sessionId,
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

    fun applyRevision(instructionOverride: String? = null) {
        val instruction = (instructionOverride ?: _uiState.value.revisionInstruction).trim()
        if (instruction.isBlank()) return

        val currentOptions = _uiState.value.options
        if (currentOptions.isEmpty()) return

        // A predicted change belongs to the option it was shown on, so it always targets
        // the currently-selected option regardless of the selected/all toggle.
        val targetIndex = if (instructionOverride != null || _uiState.value.reviseTargetSelectedOnly) {
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
                    instruction = instruction,
                    sessionId = _uiState.value.sessionId
                )
                _uiState.value = _uiState.value.copy(
                    isRevising = false,
                    options = revised.options,
                    sessionId = revised.sessionId ?: _uiState.value.sessionId,
                    revisionInstruction = if (instructionOverride != null) _uiState.value.revisionInstruction else ""
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
        if (chosen.title.isBlank() || chosen.description.isBlank() || chosen.structure.isBlank() ||
            chosen.hosts.any { it.name.isBlank() || it.persona.isBlank() }
        ) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Title, description and structure are required, and every host needs a name and persona"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, errorMessage = null)
            try {
                val podcast = podcastRepo.createPodcast(
                    title = chosen.title,
                    description = chosen.description,
                    structure = chosen.structure,
                    hosts = chosen.hosts,
                    sessionId = _uiState.value.sessionId,
                    languageCode = chosen.languageCode
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
