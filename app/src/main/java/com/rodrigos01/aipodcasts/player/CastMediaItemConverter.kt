package com.rodrigos01.aipodcasts.player

import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem

/**
 * Tells the cast receiver what kind of stream an episode is. Media3's default marks everything
 * BUFFERED (a finite file with a known duration), but while an episode is still being generated
 * the backend streams it chunked with no length, which a receiver can't treat as a file. So:
 * - audio complete (the item carries [PodcastAudioController.EXTRA_COMPLETE_DURATION_MS]):
 *   BUFFERED with that exact duration, so the receiver can seek and show the length;
 * - otherwise: LIVE, an open-ended stream with no duration and no seeking (the app re-opens the
 *   stream with `?t=` to seek, as it does for any unseekable stream).
 */
class CastMediaItemConverter(
    private val delegate: MediaItemConverter = DefaultMediaItemConverter()
) : MediaItemConverter {

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val queueItem = delegate.toMediaQueueItem(mediaItem)
        val completeDurationMs =
            mediaItem.mediaMetadata.extras?.getLong(PodcastAudioController.EXTRA_COMPLETE_DURATION_MS) ?: 0L
        queueItem.media?.writer?.apply {
            setStreamType(streamTypeFor(completeDurationMs))
            if (completeDurationMs > 0) setStreamDuration(completeDurationMs)
        }
        return queueItem
    }

    override fun toMediaItem(queueItem: MediaQueueItem): MediaItem = delegate.toMediaItem(queueItem)

    companion object {
        fun streamTypeFor(completeDurationMs: Long): Int =
            if (completeDurationMs > 0) MediaInfo.STREAM_TYPE_BUFFERED else MediaInfo.STREAM_TYPE_LIVE
    }
}
