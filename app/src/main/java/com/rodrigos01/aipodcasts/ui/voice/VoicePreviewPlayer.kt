package com.rodrigos01.aipodcasts.ui.voice

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays one short voice sample at a time with a plain [MediaPlayer]. Deliberately independent of
 * the Media3 playback service: previews have no notification, session or resume position.
 */
class VoicePreviewPlayer {

    enum class Phase { Loading, Playing }

    data class State(val voiceId: String? = null, val phase: Phase = Phase.Loading, val failed: Boolean = false)

    private val _state = MutableStateFlow(State())
    /** The voice currently loading/playing, if any. [State.failed] is set once after a playback error. */
    val state: StateFlow<State> = _state.asStateFlow()

    private var player: MediaPlayer? = null

    /** Starts [url] for [voiceId], or stops it when that voice is already the active one. */
    fun toggle(voiceId: String, url: String) {
        if (_state.value.voiceId == voiceId) {
            stop()
            return
        }
        stop()
        _state.value = State(voiceId = voiceId, phase = Phase.Loading)
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setOnPreparedListener {
                if (player === mp) {
                    it.start()
                    _state.value = State(voiceId = voiceId, phase = Phase.Playing)
                }
            }
            mp.setOnCompletionListener { if (player === mp) stop() }
            mp.setOnErrorListener { _, _, _ ->
                if (player === mp) {
                    stop()
                    _state.value = State(failed = true)
                }
                true
            }
            mp.setDataSource(url)
            mp.prepareAsync()
        } catch (e: Exception) {
            stop()
            _state.value = State(failed = true)
        }
    }

    fun stop() {
        player?.let {
            player = null
            runCatching { it.stop() }
            it.release()
        }
        _state.value = State()
    }

    fun release() = stop()
}
