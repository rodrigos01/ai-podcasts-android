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
import com.rodrigos01.aipodcasts.data.repository.AuthRepository
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
    private val authRepository: AuthRepository = AuthRepository(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var episodeProgressJob: Job? = null
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

    // How much of the episode's audio has been synthesized so far, in seconds, as pushed by
    // the episode's Firestore document. Null until the first snapshot arrives.
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

    private fun observeEpisodeAudioProgress(podcastId: String, episodeId: String) {
        episodeProgressJob?.cancel()
        episodeProgressJob = scope.launch {
            episodeRepository.getEpisodeFlow(podcastId, episodeId).collect { episode ->
                if (episode != null) {
                    _generatedAudioSeconds.value = episode.generatedAudioSeconds
                }
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

        _currentEpisode.value = episode
        _currentPodcastTitle.value = podcastTitle
        currentPodcastId = podcastId
        currentIdToken = idToken
        if (idToken.isNullOrBlank()) {
            scope.launch {
                val token = authRepository.getIdToken(forceRefresh = false)
                if (!token.isNullOrBlank()) {
                    currentIdToken = token
                }
            }
        }
        _generatedAudioSeconds.value = null
        observeEpisodeAudioProgress(podcastId, episode.id)

        val savedPosMs = if (forceFromBeginning) 0L else playbackPositionRepository.getPositionMs(episode.id)
        val shouldResume = savedPosMs >= 3000L
        val resumeSeconds = if (shouldResume) savedPosMs / 1000.0 else 0.0

        streamStartOffsetMs = if (shouldResume) savedPosMs else 0L
        _currentPositionMs.value = streamStartOffsetMs

        val streamUrl = ApiClient.buildAudioStreamUrl(
            podcastId = podcastId,
            episodeId = episode.id,
            idToken = null,
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
        } else if (player.playbackState == Player.STATE_ENDED) {
            // The loaded item may have started at a `?t=` offset, so replay via a fresh stream
            // from 0 rather than a plain play() on the old item.
            restartAtPositionMs(0L, forcePlay = true)
        } else if (player.playbackState == Player.STATE_IDLE) {
            // Fatal error left the player idle: re-prepare from the position we stopped at.
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

    private fun restartAtPositionMs(
        positionMs: Long,
        forcePlay: Boolean? = null
    ) {
        val player = mediaController ?: return
        val episode = _currentEpisode.value ?: return
        val podcastId = currentPodcastId ?: return
        val shouldPlay = forcePlay ?: player.isPlaying

        streamStartOffsetMs = positionMs
        _currentPositionMs.value = positionMs
        playbackPositionRepository.savePositionMs(episode.id, positionMs)

        scope.launch {
            val freshToken = authRepository.getIdToken(forceRefresh = false)
            if (!freshToken.isNullOrBlank()) {
                currentIdToken = freshToken
            }

            val streamUrl = ApiClient.buildAudioStreamUrl(
                podcastId = podcastId,
                episodeId = episode.id,
                idToken = null,
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
        episodeProgressJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
