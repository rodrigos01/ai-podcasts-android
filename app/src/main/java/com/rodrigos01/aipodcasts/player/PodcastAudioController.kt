package com.rodrigos01.aipodcasts.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.rodrigos01.aipodcasts.data.api.ApiClient
import com.rodrigos01.aipodcasts.data.model.Episode
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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var streamStartOffsetMs: Long = 0L

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
                    _currentEpisode.value?.let { ep ->
                        playbackPositionRepository.clearPosition(ep.id)
                    }
                    _currentPositionMs.value = 0L
                    streamStartOffsetMs = 0L
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
            }
        })
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

        _currentEpisode.value = episode
        _currentPodcastTitle.value = podcastTitle

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
            player.pause()
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
        }
    }

    fun seekRelative(offsetSeconds: Int) {
        val player = mediaController ?: return
        val duration = player.duration
        if (player.isCurrentMediaItemSeekable && duration > 0) {
            val targetMs = (_currentPositionMs.value + (offsetSeconds * 1000L)).coerceIn(0L, duration)
            seekTo(targetMs)
        }
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
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
