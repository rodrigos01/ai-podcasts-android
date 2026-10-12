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

    @Test
    fun castDurationIsZeroWhileGeneratingAndExactOnceComplete() {
        val f = PodcastAudioController.Companion::completeAudioDurationMs
        // Still generating (a duration alone doesn't count without the complete flag).
        assertEquals(0L, f(false, 0L, false, null))
        assertEquals(0L, f(false, 0L, false, 120.0))
        // Complete per the live listener wins.
        assertEquals(fiftyMinMs, f(true, fiftyMinMs, false, null))
        // Playback just started: the listener hasn't reported, the episode doc already says complete.
        assertEquals(90_500L, f(false, 0L, true, 90.5))
        // Complete flag without a duration can't be sent as a finite stream.
        assertEquals(0L, f(true, 0L, true, null))
    }

    @Test
    fun completeEpisodeResumesByRange_neverByQueryParam() {
        val start = PodcastAudioController.streamStart(754_200, audioComplete = true)
        assertNull(start.timeSeconds)               // plain (signed) URL, no t=
        assertEquals(754_200L, start.playerStartMs) // the player requests the byte range itself
        assertEquals(0L, start.streamOffsetMs)      // so player time == episode time
    }

    @Test
    fun generatingEpisodeResumesWithTimeParam() {
        val start = PodcastAudioController.streamStart(754_200, audioComplete = false)
        assertEquals(754.2, start.timeSeconds!!, 0.0001)
        assertEquals(0L, start.playerStartMs)
        assertEquals(754_200L, start.streamOffsetMs)
    }

    @Test
    fun startingFromTheBeginningNeedsNoOffsetEitherWay() {
        for (complete in listOf(true, false)) {
            val start = PodcastAudioController.streamStart(0, complete)
            assertNull(start.timeSeconds)
            assertEquals(0L, start.playerStartMs)
            assertEquals(0L, start.streamOffsetMs)
        }
    }
}
