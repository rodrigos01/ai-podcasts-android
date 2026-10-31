package com.rodrigos01.aipodcasts.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import com.rodrigos01.aipodcasts.data.api.ApiClient
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.MainActivity
import com.rodrigos01.aipodcasts.data.repository.AuthRepository

@UnstableApi
class PodcastPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null

    override fun onCreate() {
        super.onCreate()

        // The backend can take tens of seconds per chunk to synthesize audio while an
        // episode is still generating, so give it plenty of room before the HTTP data
        // source gives up on a stalled read.
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(BUFFERING_TIMEOUT_MS)
            .setReadTimeoutMs(BUFFERING_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)

        val authRepository = runCatching { AIPodcastsApplication.instance.authRepository }.getOrNull() ?: AuthRepository()
        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            httpDataSourceFactory,
            AudioStreamTokenResolver(authRepository)
        )

        // A fully generated episode is a plain AAC (ADTS) file; ADTS has no seek table, so let the
        // player seek it by byte offset (a Range request) - this is how resuming works there.
        val extractorsFactory = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(this, extractorsFactory)
            .setDataSourceFactory(resolvingDataSourceFactory)
            // ExoPlayer retries dropped/stalled loads itself (re-opening through the token
            // resolver, so a fresh token each time); just give it more attempts than the default.
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(MIN_LOAD_RETRIES))

        // Buffer as far ahead as possible so playback is less likely to catch up to the
        // backend's live generation edge in the first place.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val openPlayerIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this, 0, openPlayerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        exoPlayer = player
        // The session drives the CastPlayer, which wraps the local ExoPlayer and plays on
        // whichever output is selected (this phone, a Chromecast, ...). Cast is optional (no Play
        // Services), so if it can't be built the session just uses the ExoPlayer.
        val sessionPlayer = buildCastPlayer(player) ?: player
        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(sessionActivity)
            .build()
    }

    private fun buildCastPlayer(local: ExoPlayer): CastPlayer? = runCatching {
        CastPlayer.Builder(this)
            .setLocalPlayer(local)
            .setRemotePlayer(
                RemoteCastPlayer.Builder(this)
                    .setMediaItemConverter(CastMediaItemConverter())
                    .build()
            )
            .setTransferCallback(::transferState)
            .build()
    }.getOrNull()?.also { castPlayer = it }

    // Hands the current item over when playback moves between this phone and a cast device, at
    // the same episode position. The item's stream starts at a `?t=` offset that the controller
    // records in its extras, so the new player's stream is re-opened at the absolute position
    // rather than at 0. Deliberately left paused on the new device (in either direction): the
    // user presses play when ready, rather than audio suddenly starting on a TV or speaker.
    private fun transferState(from: Player, to: Player) {
        val item = from.currentMediaItem
        val offsetMs = item?.mediaMetadata?.extras?.getLong(PodcastAudioController.EXTRA_STREAM_OFFSET_MS) ?: 0L
        val positionMs = offsetMs + from.currentPosition.coerceAtLeast(0L)

        from.stop()
        from.clearMediaItems()

        if (item != null) {
            // playWhenReady first: a CastPlayer includes it in the receiver load request.
            to.playWhenReady = false
            val start = PodcastAudioController.streamStart(positionMs, audioComplete = item.isAudioComplete())
            to.setMediaItem(item.withStreamStart(start), start.playerStartMs)
            to.prepare()
        }
    }

    // The controller marks a fully generated episode by its exact duration in the item's extras.
    private fun MediaItem.isAudioComplete(): Boolean =
        (mediaMetadata.extras?.getLong(PodcastAudioController.EXTRA_COMPLETE_DURATION_MS) ?: 0L) > 0L

    // Re-targets the item's stream per [PodcastAudioController.streamStart]: `t=` for a live
    // episode, the plain URL (the player starts at the position) for a complete one.
    private fun MediaItem.withStreamStart(start: PodcastAudioController.Companion.StreamStart): MediaItem {
        val url = localConfiguration?.uri?.toString() ?: return this
        val extras = android.os.Bundle(mediaMetadata.extras ?: android.os.Bundle.EMPTY).apply {
            putLong(PodcastAudioController.EXTRA_STREAM_OFFSET_MS, start.streamOffsetMs)
        }
        return buildUpon()
            .setUri(ApiClient.withStartTime(url, start.timeSeconds))
            .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0)) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        castPlayer?.release()
        castPlayer = null
        mediaSession?.run {
            release()
            mediaSession = null
        }
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }

    private companion object {
        const val BUFFERING_TIMEOUT_MS = 120_000
        const val MIN_BUFFER_MS = 60_000
        const val MAX_BUFFER_MS = 600_000
        const val MIN_LOAD_RETRIES = 6
    }
}
