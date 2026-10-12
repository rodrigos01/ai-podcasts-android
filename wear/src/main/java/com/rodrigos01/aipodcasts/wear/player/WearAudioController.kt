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
import com.rodrigos01.aipodcasts.sync.SyncedPositionStore
import com.rodrigos01.aipodcasts.wear.data.AudioUrlClient
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
    private val positions: SyncedPositionStore,
    private val repository: WearPodcastRepository,
    private val audioUrlClient: AudioUrlClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var pendingStart: (() -> Unit)? = null

    private var progressJob: Job? = null
    private var episodeProgressJob: Job? = null
    private var resumeJob: Job? = null
    private var openJob: Job? = null
    private var seekJob: Job? = null
    private val audioUrls = mutableMapOf<String, String>()
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

    /**
     * Where a pending skip will land, shown on the player while the user keeps tapping; the seek
     * itself is only performed once they stop (see [seekRelative]). Null when nothing is pending.
     */
    private val _pendingSeekMs = MutableStateFlow<Long?>(null)
    val pendingSeekMs: StateFlow<Long?> = _pendingSeekMs.asStateFlow()

    /** The backend's exact duration once the audio is complete; 0 while it is still generating. */
    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    /** Set when an episode couldn't be opened (e.g. no network); cleared on the next attempt. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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
                _currentEpisode.value?.let { positions.savePositionMs(it.id, _positionMs.value, checkpoint = true) }
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

        // Already loaded and still at the saved position: just resume. If the saved position moved
        // since (progress made on the phone), fall through and reload from there.
        val loadedAtSavedPosition = kotlin.math.abs(positions.getPositionMs(episode.id) - _positionMs.value) < 5_000L
        if (!fromBeginning && _currentEpisode.value?.id == episode.id && loadedAtSavedPosition) {
            if (!player.isPlaying) togglePlayPause()
            return
        }

        _currentEpisode.value?.let { previous ->
            if (previous.id != episode.id) positions.savePositionMs(previous.id, _positionMs.value, checkpoint = true)
        }

        discardPendingSeek()
        resumeJob?.cancel()
        resumeAttempts = 0
        resumeBaselineMs = 0L
        _currentEpisode.value = episode
        _podcastTitle.value = podcastTitle
        currentPodcastId = podcastId
        // Seed completion from the episode we were handed (the live listener below keeps it
        // current): whether it is fully generated decides how the stream is opened.
        _generatedAudioSeconds.value = episode.generatedAudioSeconds
        audioDurationMs = ((episode.audioDurationSeconds ?: 0.0) * 1000).toLong()
        audioComplete = episode.audioComplete && audioDurationMs > 0
        _durationMs.value = if (audioComplete) audioDurationMs else 0L
        observeEpisodeAudioProgress(podcastId, episode.id)

        val savedMs = if (fromBeginning) 0L else positions.getPositionMs(episode.id)
        val startMs = if (SyncedPositionStore.canResume(savedMs)) savedMs else 0L
        if (fromBeginning) positions.clearPosition(episode.id)
        _positionMs.value = startMs

        openStream(startMs, play = true)
    }

    fun togglePlayPause() {
        val player = mediaController ?: return
        // A skip still waiting out its delay is applied first, not lost.
        commitPendingSeek()
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

    /**
     * Skips by [offsetSeconds], but doesn't act on it straight away: each tap moves a pending
     * target (shown via [pendingSeekMs]) and the actual seek - which can mean opening a new
     * stream and buffering - happens once the taps have stopped for
     * [StreamLogic.SEEK_DEBOUNCE_MS], so the user can reach the position they want first.
     */
    fun seekRelative(offsetSeconds: Int) {
        val upperBoundMs = seekUpperBoundMs()
        if (upperBoundMs <= 0L) return
        val base = _pendingSeekMs.value ?: _positionMs.value
        _pendingSeekMs.value = StreamLogic.skipTarget(base, offsetSeconds, upperBoundMs)

        seekJob?.cancel()
        seekJob = scope.launch {
            delay(StreamLogic.SEEK_DEBOUNCE_MS)
            commitPendingSeek()
        }
    }

    private fun commitPendingSeek() {
        seekJob?.cancel()
        val target = _pendingSeekMs.value ?: return
        _pendingSeekMs.value = null
        seekTo(target)
    }

    private fun discardPendingSeek() {
        seekJob?.cancel()
        _pendingSeekMs.value = null
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
            _currentEpisode.value?.let { positions.savePositionMs(it.id, targetMs, checkpoint = true) }
        } else {
            // The current response can't reach the target (live/unseekable, or outside the window
            // this `?t=` stream covers), so open a new one starting there.
            restartAtPositionMs(targetMs)
        }
    }

    private fun restartAtPositionMs(positionMs: Long, forcePlay: Boolean? = null) {
        val player = mediaController ?: return
        val episode = _currentEpisode.value ?: return
        val shouldPlay = forcePlay ?: player.isPlaying

        _positionMs.value = positionMs
        positions.savePositionMs(episode.id, positionMs, checkpoint = true)

        openStream(positionMs, shouldPlay)
    }

    // Loads the current episode's public audio URL (cached per episode) and starts it at
    // [positionMs]. The start offset and the media item are switched together: the progress loop
    // adds the offset to the player's position, so changing one without the other (e.g. while the
    // URL is fetched) would briefly report - and persist - a wrong position.
    private fun openStream(positionMs: Long, play: Boolean) {
        val episode = _currentEpisode.value ?: return
        val podcastId = currentPodcastId ?: return
        val podcastTitle = _podcastTitle.value

        _error.value = null
        openJob?.cancel()
        openJob = scope.launch {
            val baseUrl = audioUrls[episode.id]
                ?: audioUrlClient.getAudioUrl(podcastId, episode.id)
                    .onSuccess { audioUrls[episode.id] = it }
                    .getOrElse {
                        _error.value = it.localizedMessage ?: "Couldn't load episode"
                        return@launch
                    }
            val player = mediaController ?: return@launch

            val start = StreamLogic.streamStart(positionMs, audioComplete)
            streamStartOffsetMs = start.streamOffsetMs
            player.setMediaItem(buildMediaItem(baseUrl, episode.title, podcastTitle, start.timeSeconds), start.playerStartMs)
            player.prepare()
            if (play) player.play()
        }
    }

    private fun buildMediaItem(baseUrl: String, episodeTitle: String, podcastTitle: String, timeSeconds: Double?): MediaItem =
        MediaItem.Builder()
            // A null offset leaves the (signed) URL exactly as the backend returned it.
            .setUri(if (timeSeconds == null) baseUrl else WearConfig.withStartTime(baseUrl, timeSeconds))
            .setMimeType(WearConfig.AUDIO_MIME_TYPE)
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
