package com.rodrigos01.aipodcasts.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
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
    private var restartJob: Job? = null
    private var resumeJob: Job? = null
    private var resumeAttempts: Int = 0
    private var resumeBaselineMs: Long = 0L
    private var streamStartOffsetMs: Long = 0L
    private var currentPodcastId: String? = null
    private var currentIdToken: String? = null

    // Public (unauthenticated) audio URL per episode, from GET .../audio/url. It's stable, so one
    // fetch serves the local player and any cast receiver, which can't send a token.
    private val audioUrls = mutableMapOf<String, String>()

    // From the episode's Firestore doc (observeEpisodeAudioProgress): the backend's own word on
    // whether the audio is finished, and its exact length. This - not anything ExoPlayer infers
    // from the stream - decides "live" vs "complete": a live response that gets cut short makes
    // ExoPlayer report a seekable, fully-known "file" of whatever length had arrived.
    private var audioComplete: Boolean = false
    private var audioDurationMs: Long = 0L

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

    // The episode's exact duration once the backend reports the audio complete; 0 while it is
    // still generating (use generatedAudioSeconds for the scrubber bound then). Deliberately not
    // ExoPlayer's own duration, which is relative to where this stream started and is a guess
    // for a live response.
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

                if (playbackState == Player.STATE_ENDED) {
                    val positionMs = streamStartOffsetMs + player.currentPosition.coerceAtLeast(0L)
                    if (isGenuineEnd(positionMs, audioComplete, audioDurationMs)) {
                        _currentEpisode.value?.let { ep ->
                            playbackPositionRepository.clearPosition(ep.id)
                        }
                        _currentPositionMs.value = 0L
                        streamStartOffsetMs = 0L
                    } else {
                        // The response ended but the episode didn't: we caught up to audio that
                        // is still being synthesized, or the connection was closed early (proxy
                        // timeout, network drop). Keep the position and pick the stream back up
                        // from it; never treat it as a finish.
                        _currentPositionMs.value = positionMs
                        resumeAfterEarlyEnd(positionMs)
                    }
                }
            }

            // The service re-opens the stream itself when playback moves between this device and
            // a cast receiver; pick up the new stream's start offset from the item it loaded.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaMetadata?.extras?.let { extras ->
                    if (extras.containsKey(EXTRA_STREAM_OFFSET_MS)) {
                        streamStartOffsetMs = extras.getLong(EXTRA_STREAM_OFFSET_MS)
                    }
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
                    if (pos >= resumeBaselineMs + RESUME_PROGRESS_RESET_MS) resumeAttempts = 0
                }
                delay(500)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
    }

    // Re-opens the stream at [positionMs] after it ended early. Not a retry loop for errors
    // (ExoPlayer retries failed loads itself): this only covers a response that closed cleanly
    // before the episode was over. Waits a moment so a genuine end isn't re-requested before the
    // backend's completion flag reaches us, and gives up after a few attempts that made no
    // progress.
    private fun resumeAfterEarlyEnd(positionMs: Long) {
        val player = mediaController ?: return
        if (!player.playWhenReady) return
        if (_currentEpisode.value == null || currentPodcastId == null) return
        if (resumeAttempts >= MAX_RESUME_ATTEMPTS) return

        resumeAttempts++
        resumeBaselineMs = positionMs
        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(RESUME_DELAY_MS)
            if (isGenuineEnd(positionMs, audioComplete, audioDurationMs)) return@launch
            restartAtPositionMs(positionMs, forcePlay = true)
        }
    }

    private fun observeEpisodeAudioProgress(podcastId: String, episodeId: String) {
        episodeProgressJob?.cancel()
        episodeProgressJob = scope.launch {
            episodeRepository.getEpisodeFlow(podcastId, episodeId).collect { episode ->
                if (episode != null) {
                    _generatedAudioSeconds.value = episode.generatedAudioSeconds
                    audioDurationMs = ((episode.audioDurationSeconds ?: 0.0) * 1000).toLong()
                    audioComplete = episode.audioComplete && audioDurationMs > 0
                    _durationMs.value = if (audioComplete) audioDurationMs else 0L
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

        resumeJob?.cancel()
        resumeAttempts = 0
        resumeBaselineMs = 0L
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
        audioComplete = false
        audioDurationMs = 0L
        _durationMs.value = 0L
        observeEpisodeAudioProgress(podcastId, episode.id)

        val savedPosMs = if (forceFromBeginning) 0L else playbackPositionRepository.getPositionMs(episode.id)
        val shouldResume = savedPosMs >= 3000L

        val startMs = if (shouldResume) savedPosMs else 0L
        _currentPositionMs.value = startMs

        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(podcastTitle)
            .setDisplayTitle(episode.title)
            .build()

        openStream(startMs, metadata, play = true)
    }

    fun togglePlayPause() {
        val player = mediaController ?: return
        if (player.isPlaying) {
            resumeJob?.cancel()
            player.pause()
        } else if (player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE) {
            // A genuine finish leaves the position at 0 (replay from the start); a fatal error or
            // a stream that ended before the episode did (live edge, closed connection) leaves it
            // where we stopped, so re-open the stream there rather than replaying the old item.
            resumeJob?.cancel()
            resumeAttempts = 0
            restartAtPositionMs(_currentPositionMs.value, forcePlay = true)
        } else {
            player.play()
        }
    }

    // The furthest position that exists: the exact duration once complete, otherwise how much has
    // been synthesized so far.
    private fun seekUpperBoundMs(): Long =
        if (audioComplete) audioDurationMs else ((_generatedAudioSeconds.value ?: 0.0) * 1000).toLong()

    fun seekTo(positionMs: Long) {
        val player = mediaController ?: return
        val upperBoundMs = seekUpperBoundMs()
        if (upperBoundMs <= 0L) return
        val targetMs = positionMs.coerceIn(0L, upperBoundMs)

        if (canSeekInPlayer(targetMs, streamStartOffsetMs, player.duration, player.isCurrentMediaItemSeekable)) {
            player.seekTo(targetMs - streamStartOffsetMs)
            _currentPositionMs.value = targetMs
            _currentEpisode.value?.let { ep ->
                playbackPositionRepository.savePositionMs(ep.id, targetMs)
            }
        } else {
            // The current response can't reach the target (it's a live/unseekable stream, or the
            // target is before where this `?t=` stream started or past where it ends), so open a
            // new one starting there.
            restartAtPositionMs(targetMs)
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

        _currentPositionMs.value = positionMs
        playbackPositionRepository.savePositionMs(episode.id, positionMs)

        openStream(positionMs, player.mediaMetadata, shouldPlay)
    }

    // Loads the current episode's stream starting at [positionMs] and (optionally) plays it.
    // The URL is the backend's public audio URL, so the same item works whether the session is
    // currently driving the local player or a cast receiver; the start offset is recorded in the
    // item's extras so the service can re-open the stream at the right place when it switches.
    private fun openStream(positionMs: Long, metadata: MediaMetadata, play: Boolean) {
        val player = mediaController ?: return
        val episode = _currentEpisode.value ?: return
        val podcastId = currentPodcastId ?: return

        restartJob?.cancel()
        restartJob = scope.launch {
            val baseUrl = resolveAudioUrl(podcastId, episode.id)
            val extras = Bundle().apply { putLong(EXTRA_STREAM_OFFSET_MS, positionMs) }
            val mediaItem = MediaItem.Builder()
                .setUri(ApiClient.withStartTime(baseUrl, positionMs / 1000.0))
                .setMimeType(AUDIO_MIME_TYPE)
                .setMediaMetadata(metadata.buildUpon().setExtras(extras).build())
                .build()

            // Switch the offset and the media item together: the progress loop adds the offset
            // to the player's position, so changing the offset before the old item is replaced
            // (e.g. while the URL above is fetched) would briefly report - and persist - a
            // wrong position.
            streamStartOffsetMs = positionMs
            player.setMediaItem(mediaItem)
            player.prepare()
            if (play) player.play()
        }
    }

    // The episode's public audio URL, fetched once per episode. If the lookup fails, fall back to
    // the authenticated stream URL (the token resolver signs it), which plays locally but can't
    // be cast, and don't cache it so the next open tries again.
    private suspend fun resolveAudioUrl(podcastId: String, episodeId: String): String {
        audioUrls[episodeId]?.let { return it }
        return runCatching { episodeRepository.getAudioUrl(podcastId, episodeId) }
            .onSuccess { audioUrls[episodeId] = it }
            .getOrElse { ApiClient.buildAudioStreamUrl(podcastId, episodeId) }
    }

    fun seekRelative(offsetSeconds: Int) {
        if (seekUpperBoundMs() <= 0L) return
        seekTo(_currentPositionMs.value + (offsetSeconds * 1000L))
    }

    /**
     * Stops playback for good: the saved position is kept, the player releases its media item
     * (which closes the stream request) and the mini player goes away.
     */
    fun stop() {
        _currentEpisode.value?.let { ep ->
            playbackPositionRepository.savePositionMs(ep.id, _currentPositionMs.value)
        }
        _currentEpisode.value = null
        _currentPodcastTitle.value = ""
        stopProgressLoop()
        episodeProgressJob?.cancel()
        resumeJob?.cancel()
        restartJob?.cancel()
        resumeAttempts = 0
        resumeBaselineMs = 0L
        streamStartOffsetMs = 0L
        currentPodcastId = null
        audioComplete = false
        audioDurationMs = 0L
        _generatedAudioSeconds.value = null
        _durationMs.value = 0L
        _currentPositionMs.value = 0L
        _isPlaying.value = false
        _isBuffering.value = false
        mediaController?.let { player ->
            player.stop()
            player.clearMediaItems()
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
        episodeProgressJob?.cancel()
        resumeJob?.cancel()
        restartJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    companion object {
        /** MediaItem extras key: where in the episode (ms) the item's stream starts. */
        const val EXTRA_STREAM_OFFSET_MS = "streamStartOffsetMs"

        // What the backend serves (raw ADTS); CastPlayer requires a MIME type on the item.
        const val AUDIO_MIME_TYPE = "audio/aac"

        const val MAX_RESUME_ATTEMPTS = 6
        const val RESUME_DELAY_MS = 2_000L
        // Playback this far past where an early-end resume began counts as "it worked".
        const val RESUME_PROGRESS_RESET_MS = 5_000L

        // How close to the end of the episode a stopped stream has to be to count as a genuine
        // finish. ADTS frame boundaries and the 500ms position tick make "exactly the end" too
        // strict.
        const val END_TOLERANCE_MS = 3_000L

        /**
         * Whether a stream that just ended means the episode really finished. Only when the
         * backend says the audio is complete AND playback reached its end: a response that merely
         * closed (live edge, proxy timeout, dropped connection) is not a finish, however long
         * ExoPlayer thinks the file is.
         */
        fun isGenuineEnd(positionMs: Long, audioComplete: Boolean, audioDurationMs: Long): Boolean =
            audioComplete && audioDurationMs > 0 && positionMs >= audioDurationMs - END_TOLERANCE_MS

        /**
         * Whether [targetMs] (episode time) can be reached by seeking the player's current stream
         * directly. That stream starts at [streamStartOffsetMs] and, if the player can seek it at
         * all, is [playerDurationMs] long; a target outside that window needs a new `?t=` stream.
         * The end is kept [END_TOLERANCE_MS] short so a seek to the very end of a stream that
         * ExoPlayer sized too small (cut-off live response) still restarts instead of ending.
         */
        fun canSeekInPlayer(
            targetMs: Long,
            streamStartOffsetMs: Long,
            playerDurationMs: Long,
            isSeekable: Boolean
        ): Boolean {
            if (!isSeekable || playerDurationMs <= 0) return false
            val relativeMs = targetMs - streamStartOffsetMs
            return relativeMs >= 0 && relativeMs <= playerDurationMs - END_TOLERANCE_MS
        }
    }
}
