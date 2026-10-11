package com.rodrigos01.aipodcasts.wear.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.rodrigos01.aipodcasts.wear.data.PlaybackPositionStore
import com.rodrigos01.aipodcasts.wear.data.WearConfig
import com.rodrigos01.aipodcasts.wear.data.WearEpisode
import com.rodrigos01.aipodcasts.wear.data.WearPodcastRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * App-facing facade over the watch's [WearPlaybackService], the watch counterpart of the phone's
 * `PodcastAudioController` (same streaming contract: the audio is a `?t=<seconds>` stream, so the
 * player's position is offset by [streamStartOffsetMs] to get episode time; whether the episode
 * is finished comes from the episode doc's `audioComplete`, not from ExoPlayer).
 */
@UnstableApi
class WearAudioController(
    context: Context,
    private val positions: PlaybackPositionStore,
    private val repository: WearPodcastRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var pendingStart: (() -> Unit)? = null

    private var progressJob: Job? = null
    private var episodeProgressJob: Job? = null
    private var resumeJob: Job? = null
    private var resumeAttempts = 0
    private var resumeBaselineMs = 0L
    private var streamStartOffsetMs = 0L
    private var currentPodcastId: String? = null

    private var audioComplete = false
    private var audioDurationMs = 0L

    private val _currentEpisode = MutableStateFlow<WearEpisode?>(null)
    val currentEpisode: StateFlow<WearEpisode?> = _currentEpisode.asStateFlow()

    private val _podcastTitle = MutableStateFlow("")
    val podcastTitle: StateFlow<String> = _podcastTitle.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    /** The backend's exact duration once the audio is complete; 0 while it is still generating. */
    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _generatedAudioSeconds = MutableStateFlow<Double?>(null)

    init {
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, WearPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }.onSuccess { controller ->
                mediaController = controller
                setupPlayerListener(controller)
                pendingStart?.invoke()
                pendingStart = null
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener(player: MediaController) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                _currentEpisode.value?.let { positions.savePositionMs(it.id, _positionMs.value) }
                if (playing) startProgressLoop() else progressJob?.cancel()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
                if (playbackState != Player.STATE_ENDED) return

                val positionMs = streamStartOffsetMs + player.currentPosition.coerceAtLeast(0L)
                if (StreamLogic.isGenuineEnd(positionMs, audioComplete, audioDurationMs)) {
                    _currentEpisode.value?.let { positions.clearPosition(it.id) }
                    _positionMs.value = 0L
                    streamStartOffsetMs = 0L
                } else {
                    // The response ended but the episode didn't (caught up to audio still being
                    // synthesized, or the connection closed early): keep the position and pick the
                    // stream back up from it instead of treating it as a finish.
                    _positionMs.value = positionMs
                    resumeAfterEarlyEnd(positionMs)
                }
            }
        })
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaController?.let { player ->
                    val pos = streamStartOffsetMs + player.currentPosition.coerceAtLeast(0L)
                    _positionMs.value = pos
                    _currentEpisode.value?.let { if (pos > 0L) positions.savePositionMs(it.id, pos) }
                    if (pos >= resumeBaselineMs + StreamLogic.RESUME_PROGRESS_RESET_MS) resumeAttempts = 0
                }
                delay(PROGRESS_TICK_MS)
            }
        }
    }

    // Re-opens the stream at [positionMs] after it ended early. Not an error-retry loop (ExoPlayer
    // retries failed loads itself); waits a moment so a genuine end isn't re-requested before the
    // backend's completion flag arrives, and gives up after a few attempts that made no progress.
    private fun resumeAfterEarlyEnd(positionMs: Long) {
        val player = mediaController ?: return
        if (!player.playWhenReady) return
        if (_currentEpisode.value == null || currentPodcastId == null) return
        if (resumeAttempts >= StreamLogic.MAX_RESUME_ATTEMPTS) return

        resumeAttempts++
        resumeBaselineMs = positionMs
        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(StreamLogic.RESUME_DELAY_MS)
            if (StreamLogic.isGenuineEnd(positionMs, audioComplete, audioDurationMs)) return@launch
            restartAtPositionMs(positionMs, forcePlay = true)
        }
    }

    private fun observeEpisodeAudioProgress(podcastId: String, episodeId: String) {
        episodeProgressJob?.cancel()
        episodeProgressJob = scope.launch {
            repository.episodeFlow(podcastId, episodeId).catch { /* keep playing on a dropped listener */ }
                .collect { episode ->
                    if (episode == null) return@collect
                    _generatedAudioSeconds.value = episode.generatedAudioSeconds
                    audioDurationMs = ((episode.audioDurationSeconds ?: 0.0) * 1000).toLong()
                    audioComplete = episode.audioComplete && audioDurationMs > 0
                    _durationMs.value = if (audioComplete) audioDurationMs else 0L
                }
        }
    }

    /**
     * Starts [episode], from its saved position unless [fromBeginning]. If it is already the
     * active episode and not being restarted, just resumes it.
     */
    fun play(podcastId: String, podcastTitle: String, episode: WearEpisode, fromBeginning: Boolean = false) {
        val player = mediaController
        if (player == null) {
            // The session connection is still being established; start as soon as it is.
            pendingStart = { play(podcastId, podcastTitle, episode, fromBeginning) }
            return
        }

        if (!fromBeginning && _currentEpisode.value?.id == episode.id) {
            if (!player.isPlaying) togglePlayPause()
            return
        }

        _currentEpisode.value?.let { previous ->
            if (previous.id != episode.id) positions.savePositionMs(previous.id, _positionMs.value)
        }

        resumeJob?.cancel()
        resumeAttempts = 0
        resumeBaselineMs = 0L
        _currentEpisode.value = episode
        _podcastTitle.value = podcastTitle
        currentPodcastId = podcastId
        _generatedAudioSeconds.value = null
        audioComplete = false
        audioDurationMs = 0L
        _durationMs.value = 0L
        observeEpisodeAudioProgress(podcastId, episode.id)

        val savedMs = if (fromBeginning) 0L else positions.getPositionMs(episode.id)
        val startMs = if (PlaybackPositionStore.canResume(savedMs)) savedMs else 0L
        if (fromBeginning) positions.clearPosition(episode.id)
        streamStartOffsetMs = startMs
        _positionMs.value = startMs

        player.setMediaItem(buildMediaItem(podcastId, podcastTitle, episode.id, episode.title, startMs))
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = mediaController ?: return
        when {
            player.isPlaying -> {
                resumeJob?.cancel()
                player.pause()
            }
            player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE -> {
                // A genuine finish leaves the position at 0 (replay); a fatal error or an early
                // end leaves it where we stopped, so re-open the stream there.
                resumeJob?.cancel()
                resumeAttempts = 0
                restartAtPositionMs(_positionMs.value, forcePlay = true)
            }
            else -> player.play()
        }
    }

    fun seekRelative(offsetSeconds: Int) {
        if (seekUpperBoundMs() <= 0L) return
        seekTo(_positionMs.value + offsetSeconds * 1000L)
    }

    // The furthest position that exists: the exact duration once complete, otherwise how much
    // has been synthesized so far.
    private fun seekUpperBoundMs(): Long =
        if (audioComplete) audioDurationMs else ((_generatedAudioSeconds.value ?: 0.0) * 1000).toLong()

    private fun seekTo(positionMs: Long) {
        val player = mediaController ?: return
        val upperBoundMs = seekUpperBoundMs()
        if (upperBoundMs <= 0L) return
        val targetMs = positionMs.coerceIn(0L, upperBoundMs)

        if (StreamLogic.canSeekInPlayer(targetMs, streamStartOffsetMs, player.duration, player.isCurrentMediaItemSeekable)) {
            player.seekTo(targetMs - streamStartOffsetMs)
            _positionMs.value = targetMs
            _currentEpisode.value?.let { positions.savePositionMs(it.id, targetMs) }
        } else {
            // The current response can't reach the target (live/unseekable, or outside the window
            // this `?t=` stream covers), so open a new one starting there.
            restartAtPositionMs(targetMs)
        }
    }

    private fun restartAtPositionMs(positionMs: Long, forcePlay: Boolean? = null) {
        val player = mediaController ?: return
        val episode = _currentEpisode.value ?: return
        val podcastId = currentPodcastId ?: return
        val shouldPlay = forcePlay ?: player.isPlaying

        _positionMs.value = positionMs
        positions.savePositionMs(episode.id, positionMs)

        // Switch the offset and the media item together: the progress loop adds the offset to the
        // player's position, so changing one without the other would briefly report (and persist)
        // a wrong position.
        streamStartOffsetMs = positionMs
        player.setMediaItem(buildMediaItem(podcastId, _podcastTitle.value, episode.id, episode.title, positionMs))
        player.prepare()
        if (shouldPlay) player.play()
    }

    private fun buildMediaItem(
        podcastId: String,
        podcastTitle: String,
        episodeId: String,
        episodeTitle: String,
        startMs: Long
    ): MediaItem = MediaItem.Builder()
        .setUri(WearConfig.buildAudioStreamUrl(podcastId, episodeId, if (startMs > 0) startMs / 1000.0 else null))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(episodeTitle)
                .setDisplayTitle(episodeTitle)
                .setArtist(podcastTitle)
                .build()
        )
        .build()

    private companion object {
        const val PROGRESS_TICK_MS = 500L
    }
}
