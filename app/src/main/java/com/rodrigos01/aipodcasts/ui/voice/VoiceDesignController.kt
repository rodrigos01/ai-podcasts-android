package com.rodrigos01.aipodcasts.ui.voice

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
    val previewPlayer: VoicePreviewPlayer = VoicePreviewPlayer()
) {
    private val _uiState = MutableStateFlow<VoiceDesignUiState?>(null)
    /** Null while the dialog is closed. */
    val uiState: StateFlow<VoiceDesignUiState?> = _uiState.asStateFlow()

    private var sessionId: String = ""
    private var onPicked: (voiceId: String, prompt: String) -> Unit = { _, _ -> }

    /** Opens the dialog for one person. [onPicked] gets the chosen voice and the prompt it was designed from. */
    fun open(
        sessionId: String,
        personName: String,
        prompt: String,
        onPicked: (voiceId: String, prompt: String) -> Unit
    ) {
        this.sessionId = sessionId
        this.onPicked = onPicked
        _uiState.value = VoiceDesignUiState(personName = personName, prompt = prompt)
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
                        selectedVoiceId = null
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
