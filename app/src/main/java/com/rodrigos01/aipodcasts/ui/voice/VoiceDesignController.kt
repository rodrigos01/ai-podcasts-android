package com.rodrigos01.aipodcasts.ui.voice

import com.rodrigos01.aipodcasts.data.api.ApiClient
import com.rodrigos01.aipodcasts.data.model.DesignedVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceDesignUiState(
    val personName: String,
    val prompt: String,
    /** The person's existing voice when editing; always offered alongside new candidates, and preselected. */
    val currentVoice: DesignedVoice? = null,
    val candidates: List<DesignedVoice> = emptyList(),
    /** The prompt the current [candidates] were designed from (the prompt field may have changed since). */
    val designedPrompt: String? = null,
    val selectedVoiceId: String? = null,
    val isDesigning: Boolean = false,
    val errorMessage: String? = null
)

/**
 * State and actions behind the "choose a voice" dialog, shared by every screen that offers it.
 * A ViewModel owns one instance and shows [VoiceDesignDialog] while [uiState] is non-null.
 *
 * [designVoices] designs candidates for (sessionId, prompt); the session id is the wizard's
 * `sessionId`, or the podcast/episode id when editing an existing one.
 */
class VoiceDesignController(
    private val scope: CoroutineScope,
    private val designVoices: suspend (sessionId: String, prompt: String) -> List<DesignedVoice>,
    private val previewUrlFor: (voiceId: String) -> String = ApiClient::buildVoicePreviewUrl,
    val previewPlayer: VoicePreviewPlayer = VoicePreviewPlayer()
) {
    private val _uiState = MutableStateFlow<VoiceDesignUiState?>(null)
    /** Null while the dialog is closed. */
    val uiState: StateFlow<VoiceDesignUiState?> = _uiState.asStateFlow()

    private var sessionId: String = ""
    private var onPicked: (voiceId: String, prompt: String) -> Unit = { _, _ -> }

    /**
     * Opens the dialog for one person. If they already have a voice ([currentVoiceId]) it is shown
     * first and preselected, so it can be previewed and kept. [onPicked] is only called when a
     * different voice is chosen, with that voice and the prompt it was designed from.
     */
    fun open(
        sessionId: String,
        personName: String,
        prompt: String,
        currentVoiceId: String? = null,
        onPicked: (voiceId: String, prompt: String) -> Unit
    ) {
        this.sessionId = sessionId
        this.onPicked = onPicked
        val current = currentVoiceId?.let { DesignedVoice(it, previewUrlFor(it)) }
        _uiState.value = VoiceDesignUiState(
            personName = personName,
            prompt = prompt,
            currentVoice = current,
            selectedVoiceId = current?.voiceId
        )
    }

    fun onPromptChanged(prompt: String) = _uiState.update { it?.copy(prompt = prompt) }

    /** Designs a fresh set of candidates from whatever is currently in the prompt field. */
    fun design() {
        val state = _uiState.value ?: return
        val prompt = state.prompt.trim()
        if (prompt.isEmpty() || state.isDesigning) return

        previewPlayer.stop()
        _uiState.update { it?.copy(isDesigning = true, errorMessage = null) }
        scope.launch {
            try {
                val voices = designVoices(sessionId, prompt)
                _uiState.update {
                    it?.copy(
                        isDesigning = false,
                        candidates = voices,
                        designedPrompt = prompt,
                        // The existing voice stays the default so regenerating never silently drops it.
                        selectedVoiceId = it.currentVoice?.voiceId
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it?.copy(isDesigning = false, errorMessage = e.localizedMessage ?: e.message ?: "Voice design failed")
                }
            }
        }
    }

    fun select(voiceId: String) = _uiState.update { it?.copy(selectedVoiceId = voiceId) }

    fun togglePreview(voice: DesignedVoice) = previewPlayer.toggle(voice.voiceId, voice.previewUrl)

    fun confirm() {
        val state = _uiState.value ?: return
        val voiceId = state.selectedVoiceId ?: return
        if (voiceId == state.currentVoice?.voiceId) {
            dismiss() // keeping the current voice changes nothing
            return
        }
        val prompt = state.designedPrompt ?: return
        dismiss()
        onPicked(voiceId, prompt)
    }

    fun dismiss() {
        previewPlayer.stop()
        _uiState.value = null
    }

    fun release() {
        previewPlayer.release()
        _uiState.value = null
    }
}
