package com.rodrigos01.aipodcasts

import androidx.media3.common.util.UnstableApi
import com.rodrigos01.aipodcasts.data.firestore.FirestoreMappers
import com.rodrigos01.aipodcasts.player.PodcastAudioController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class PlayerEndAndSeekLogicTest {

    private val fiftyMinMs = 50 * 60 * 1000L

    @Test
    fun streamEndingMidEpisodeIsNotAFinishEvenIfAudioIsComplete() {
        // Connection cut ~6 minutes in, long before the end of a complete 50 minute episode.
        assertFalse(PodcastAudioController.isGenuineEnd(6 * 60 * 1000L, true, fiftyMinMs))
    }

    @Test
    fun streamEndingNearTheEndIsNotAFinishUntilBackendSaysComplete() {
        // Caught up to the live edge: could look like the end, but the audio isn't complete.
        assertFalse(PodcastAudioController.isGenuineEnd(fiftyMinMs, false, 0L))
        assertFalse(PodcastAudioController.isGenuineEnd(fiftyMinMs, false, fiftyMinMs))
    }

    @Test
    fun streamEndingAtTheEndOfACompleteEpisodeIsAFinish() {
        assertTrue(PodcastAudioController.isGenuineEnd(fiftyMinMs, true, fiftyMinMs))
        assertTrue(PodcastAudioController.isGenuineEnd(fiftyMinMs - 2_000L, true, fiftyMinMs))
        assertFalse(PodcastAudioController.isGenuineEnd(fiftyMinMs - 10_000L, true, fiftyMinMs))
    }

    @Test
    fun unknownDurationIsNeverAFinish() {
        assertFalse(PodcastAudioController.isGenuineEnd(1_000L, true, 0L))
    }

    @Test
    fun seekInsideTheCurrentStreamUsesThePlayer() {
        // Stream started at 10:00 and is seekable with 20:00 left.
        assertTrue(PodcastAudioController.canSeekInPlayer(15 * 60_000L, 10 * 60_000L, 20 * 60_000L, true))
    }

    @Test
    fun seekBeforeTheStreamStartNeedsANewStream() {
        assertFalse(PodcastAudioController.canSeekInPlayer(5 * 60_000L, 10 * 60_000L, 20 * 60_000L, true))
    }

    @Test
    fun seekPastWhatThePlayerThinksIsTheEndNeedsANewStream() {
        // ExoPlayer sized a cut-off stream at ~6 minutes; seeking 30 minutes in must not clamp to it.
        assertFalse(PodcastAudioController.canSeekInPlayer(30 * 60_000L, 0L, 6 * 60_000L, true))
        assertFalse(PodcastAudioController.canSeekInPlayer(6 * 60_000L, 0L, 6 * 60_000L, true))
    }

    @Test
    fun unseekableOrUnknownLengthStreamAlwaysNeedsANewStream() {
        assertFalse(PodcastAudioController.canSeekInPlayer(1_000L, 0L, 20 * 60_000L, false))
        assertFalse(PodcastAudioController.canSeekInPlayer(1_000L, 0L, -9223372036854775807L, true))
    }

    @Test
    fun episodeStatusMappingReadsAudioCompletion() {
        val done = FirestoreMappers.mapToEpisodeStatusResponse(
            mapOf("status" to "ready", "audioComplete" to true, "audioDurationSeconds" to 3012.5)
        )
        assertTrue(done.audioComplete)
        assertEquals(3012.5, done.audioDurationSeconds ?: 0.0, 0.001)

        val live = FirestoreMappers.mapToEpisodeStatusResponse(mapOf("status" to "ready"))
        assertFalse(live.audioComplete)
        assertNull(live.audioDurationSeconds)
    }
}
