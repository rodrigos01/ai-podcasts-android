package com.rodrigos01.aipodcasts.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import com.google.android.gms.cast.framework.CastContext
import com.rodrigos01.aipodcasts.data.api.ApiClient
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.rodrigos01.aipodcasts.AIPodcastsApplication
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

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
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

        exoPlayer = player
        mediaSession = MediaSession.Builder(this, player).build()
        setUpCast(player)
    }

    // The session drives whichever player is current: the local ExoPlayer, or a CastPlayer while
    // a Chromecast session is connected. The MediaController in the app doesn't know the
    // difference. Cast is optional (no Play Services, no cast devices), so failing to set it up
    // just leaves local playback.
    private fun setUpCast(local: ExoPlayer) {
        val castContext = runCatching { CastContext.getSharedInstance(this) }.getOrNull() ?: return
        val cast = CastPlayer(castContext)
        castPlayer = cast
        cast.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() = switchPlayer(from = local, to = cast)
            override fun onCastSessionUnavailable() = switchPlayer(from = cast, to = local)
        })
        if (cast.isCastSessionAvailable) switchPlayer(from = local, to = cast)
    }

    // Hands the current item over to [to] at the same episode position and stops [from]. The
    // item's stream starts at a `?t=` offset that the controller records in its extras, so the
    // new player's stream is re-opened at the absolute position rather than at 0.
    private fun switchPlayer(from: Player, to: Player) {
        val session = mediaSession ?: return
        if (session.player === to) return

        val item = from.currentMediaItem
        val offsetMs = item?.mediaMetadata?.extras?.getLong(PodcastAudioController.EXTRA_STREAM_OFFSET_MS) ?: 0L
        val positionMs = offsetMs + from.currentPosition.coerceAtLeast(0L)
        val playWhenReady = from.playWhenReady

        from.stop()
        from.clearMediaItems()
        session.player = to

        if (item != null) {
            to.setMediaItem(item.withStreamStart(positionMs))
            to.prepare()
            to.playWhenReady = playWhenReady
        }
    }

    private fun MediaItem.withStreamStart(positionMs: Long): MediaItem {
        val url = localConfiguration?.uri?.toString() ?: return this
        val extras = android.os.Bundle(mediaMetadata.extras ?: android.os.Bundle.EMPTY).apply {
            putLong(PodcastAudioController.EXTRA_STREAM_OFFSET_MS, positionMs)
        }
        return buildUpon()
            .setUri(ApiClient.withStartTime(url, positionMs / 1000.0))
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
        castPlayer?.run {
            setSessionAvailabilityListener(null)
            release()
        }
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
