package com.rodrigos01.aipodcasts.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.rodrigos01.aipodcasts.data.api.ApiClient
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.repository.EpisodeRepository
import com.rodrigos01.aipodcasts.data.repository.PlaybackPositionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class PodcastAudioController(
    private val context: Context,
    private val playbackPositionRepository: PlaybackPositionRepository = PlaybackPositionRepository(context),
    private val episodeRepository: EpisodeRepository = EpisodeRepository(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var statusPollJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt: Int = 0
    private var streamStartOffsetMs: Long = 0L
    private var currentPodcastId: String? = null
    private var currentIdToken: String? = null

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode: StateFlow<Episode?> = _currentEpisode.asStateFlow()

    private val _currentPodcastTitle = MutableStateFlow("")
    val currentPodcastTitle: StateFlow<String> = _currentPodcastTitle.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // How much of the episode's audio has been synthesized so far, in seconds.
    // Null until the first status poll resolves; keeps updating until the stream itself
    // comes back as a complete file (audio synthesis is on-demand and can lag behind the
    // script/transcript's own "ready" status - see isCurrentStreamFullyGenerated).
    private val _generatedAudioSeconds = MutableStateFlow<Double?>(null)
    val generatedAudioSeconds: StateFlow<Double?> = _generatedAudioSeconds.asStateFlow()

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PodcastPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupPlayerListener()
            } catch (e: Exception) {
                // MediaController init fallback
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        val player = mediaController ?: return
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                val currentEp = _currentEpisode.value
                if (currentEp != null) {
                    playbackPositionRepository.savePositionMs(currentEp.id, _currentPositionMs.value)
                }
                if (playing) {
                    reconnectAttempt = 0
                    startProgressLoop()
                } else {
                    stopProgressLoop()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
                val duration = player.duration
                _durationMs.value = if (duration > 0) duration else 0L

                if (playbackState == Player.STATE_ENDED) {
                    if (isCurrentStreamFullyGenerated()) {
                        // The backend served this as a normal, complete file (Content-Length +
                        // Accept-Ranges) and we played it through to the end: a genuine finish.
                        _currentEpisode.value?.let { ep ->
                            playbackPositionRepository.clearPosition(ep.id)
                        }
                        _currentPositionMs.value = 0L
                        streamStartOffsetMs = 0L
                    } else {
                        // The stream was still chunked/growing (audio synthesis is on-demand and
                        // can lag behind playback on any episode, "ready" script status or not):
                        // we've just caught up to what's been synthesized so far, not reached the
                        // real end. Keep the current position and reconnect to pick up more audio.
                        scheduleReconnect()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // A stalled/closed connection (e.g. the backend took longer than our read
                // timeout to synthesize the next chunk) shouldn't kill playback for good:
                // reconnect at the same position instead.
                scheduleReconnect()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
            }
        })
    }

    // True only once the backend has served the current stream as a complete, known-length
    // file (Content-Length + Accept-Ranges) rather than a still-growing chunked response.
    // This is the one signal that actually reflects audio-generation completeness; the
    // episode's own `status` field tracks script/transcript generation, which finishes
    // before audio synthesis even starts (see docs/backend_docs.md's Audio section).
    private fun isCurrentStreamFullyGenerated(): Boolean {
        val player = mediaController ?: return false
        return player.isCurrentMediaItemSeekable && player.duration > 0
    }

    private fun scheduleReconnect() {
        val player = mediaController ?: return
        // Only auto-reconnect if the user actually wanted playback to continue; if they'd
        // already paused, leave it paused.
        if (!player.playWhenReady) return
        if (_currentEpisode.value == null || currentPodcastId == null) return
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) return

        reconnectJob?.cancel()
        val resumeFromMs = _currentPositionMs.value
        val backoffMs = (3_000L shl reconnectAttempt).coerceAtMost(30_000L)
        reconnectAttempt++
        reconnectJob = scope.launch {
            delay(backoffMs)
            // A Range/`?t=` request resumes from exactly this position, triggering more
            // synthesis on demand if it hasn't happened yet - no need to pre-poll for it.
            restartAtPositionMs(resumeFromMs, forcePlay = true)
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaController?.let { player ->
                    val pos = streamStartOffsetMs + player.currentPosition.coerceAtLeast(0L)
                    _currentPositionMs.value = pos
                    _currentEpisode.value?.let { ep ->
                        if (pos > 0L) {
                            playbackPositionRepository.savePositionMs(ep.id, pos)
                        }
                    }
                    val duration = player.duration
                    _durationMs.value = if (duration > 0) duration else 0L
                }
                delay(500)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
    }

    private fun startStatusPolling(podcastId: String, episodeId: String) {
        statusPollJob?.cancel()
        statusPollJob = scope.launch {
            while (isActive) {
                try {
                    val status = episodeRepository.getEpisodeStatus(podcastId, episodeId)
                    _generatedAudioSeconds.value = status.generatedAudioSeconds
                    // `status` here is the script/transcript pipeline, which finishes before
                    // audio synthesis even starts (it's triggered on-demand by the first stream
                    // request). So don't stop refreshing generatedAudioSeconds just because the
                    // script is "ready" - audio can still be actively catching up well after
                    // that. Only a confirmed failure, or the stream itself coming back complete,
                    // means there's nothing left worth polling for.
                    if (status.status.equals("failed", ignoreCase = true) || isCurrentStreamFullyGenerated()) {
                        break
                    }
                } catch (e: Exception) {
                    // Keep polling despite transient network errors
                }
                delay(3000)
            }
        }
    }

    fun playEpisode(
        podcastId: String,
        podcastTitle: String,
        episode: Episode,
        idToken: String?,
        forceFromBeginning: Boolean = false
    ) {
        val player = mediaController ?: return

        // Persist position of previously playing episode before switching
        _currentEpisode.value?.let { prevEp ->
            if (prevEp.id != episode.id) {
                playbackPositionRepository.savePositionMs(prevEp.id, _currentPositionMs.value)
            }
        }

        reconnectJob?.cancel()
        reconnectAttempt = 0
        _currentEpisode.value = episode
        _currentPodcastTitle.value = podcastTitle
        currentPodcastId = podcastId
        currentIdToken = idToken
        _generatedAudioSeconds.value = null
        startStatusPolling(podcastId, episode.id)

        val savedPosMs = if (forceFromBeginning) 0L else playbackPositionRepository.getPositionMs(episode.id)
        val shouldResume = savedPosMs >= 3000L
        val resumeSeconds = if (shouldResume) savedPosMs / 1000.0 else 0.0

        streamStartOffsetMs = if (shouldResume) savedPosMs else 0L
        _currentPositionMs.value = streamStartOffsetMs

        val streamUrl = ApiClient.buildAudioStreamUrl(
            podcastId = podcastId,
            episodeId = episode.id,
            idToken = idToken,
            timeSeconds = if (resumeSeconds > 0) resumeSeconds else null
        )

        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(podcastTitle)
            .setDisplayTitle(episode.title)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = mediaController ?: return
        if (player.isPlaying) {
            reconnectJob?.cancel()
            player.pause()
        } else if (player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE) {
            // The underlying stream stalled or hit a clean EOF at the live generation edge:
            // a plain play() here would restart the same media item from position 0, so
            // reconnect at the position we actually stopped at instead.
            reconnectJob?.cancel()
            reconnectAttempt = 0
            restartAtPositionMs(_currentPositionMs.value, forcePlay = true)
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaController ?: return
        val duration = player.duration
        if (player.isCurrentMediaItemSeekable && duration > 0) {
            val playerTarget = (positionMs - streamStartOffsetMs).coerceIn(0L, duration)
            player.seekTo(playerTarget)
            _currentPositionMs.value = positionMs
            _currentEpisode.value?.let { ep ->
                playbackPositionRepository.savePositionMs(ep.id, positionMs)
            }
        } else {
            // Duration isn't known yet (still generating): the stream can't be seeked
            // directly, so restart it from the target offset via the `?t=` param instead.
            // Only valid up to how much audio has actually been generated so far.
            val generatedMs = ((_generatedAudioSeconds.value ?: 0.0) * 1000).toLong()
            if (generatedMs <= 0L) return
            restartAtPositionMs(positionMs.coerceIn(0L, generatedMs))
        }
    }

    private fun restartAtPositionMs(positionMs: Long, forcePlay: Boolean? = null) {
        val player = mediaController ?: return
        val episode = _currentEpisode.value ?: return
        val podcastId = currentPodcastId ?: return
        val shouldPlay = forcePlay ?: player.isPlaying

        streamStartOffsetMs = positionMs
        _currentPositionMs.value = positionMs
        playbackPositionRepository.savePositionMs(episode.id, positionMs)

        val streamUrl = ApiClient.buildAudioStreamUrl(
            podcastId = podcastId,
            episodeId = episode.id,
            idToken = currentIdToken,
            timeSeconds = positionMs / 1000.0
        )
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaMetadata(player.mediaMetadata)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        if (shouldPlay) player.play()
    }

    fun seekRelative(offsetSeconds: Int) {
        val player = mediaController ?: return
        val duration = player.duration
        val upperBoundMs = if (player.isCurrentMediaItemSeekable && duration > 0) {
            duration
        } else {
            ((_generatedAudioSeconds.value ?: 0.0) * 1000).toLong()
        }
        if (upperBoundMs <= 0L) return
        val targetMs = (_currentPositionMs.value + (offsetSeconds * 1000L)).coerceIn(0L, upperBoundMs)
        seekTo(targetMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    fun release() {
        _currentEpisode.value?.let { ep ->
            playbackPositionRepository.savePositionMs(ep.id, _currentPositionMs.value)
        }
        stopProgressLoop()
        statusPollJob?.cancel()
        reconnectJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 6
    }
}
