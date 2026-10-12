package com.rodrigos01.aipodcasts.wear

import com.rodrigos01.aipodcasts.wear.auth.PairTokenMessage
import com.rodrigos01.aipodcasts.sync.SyncedPositionStore
import com.rodrigos01.aipodcasts.wear.data.AudioUrlClient
import com.rodrigos01.aipodcasts.wear.data.WearConfig
import com.rodrigos01.aipodcasts.wear.data.WearEpisode
import com.rodrigos01.aipodcasts.wear.player.StreamLogic
import com.rodrigos01.aipodcasts.wear.ui.screens.formatTimeMs
import com.rodrigos01.aipodcasts.wear.ui.screens.statusLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearLogicTest {

    private fun episode(status: String) = WearEpisode(
        id = "e1", podcastId = "p1", title = "T", description = "D", status = status,
        generatedAudioSeconds = null, audioComplete = false, audioDurationSeconds = null, createdAtMillis = 0
    )

    @Test
    fun canPlay_onlyWhenStreamableOrReady() {
        assertTrue(episode("ready").canPlay)
        assertTrue(episode("Streamable").canPlay)
        assertFalse(episode("generating").canPlay)
        assertFalse(episode("failed").canPlay)
    }

    @Test
    fun statusLabel_hidesBadgeForReadyEpisodes() {
        assertNull(statusLabel(episode("ready")))
        assertEquals("Generating · playable", statusLabel(episode("streamable")))
        assertEquals("Failed", statusLabel(episode("failed")))
        assertEquals("Generating…", statusLabel(episode("generating")))
    }

    @Test
    fun canResume_needsAtLeastThreeSeconds() {
        assertFalse(SyncedPositionStore.canResume(0))
        assertFalse(SyncedPositionStore.canResume(2_999))
        assertTrue(SyncedPositionStore.canResume(3_000))
    }

    @Test
    fun formatTimeMs_usesHoursOnlyWhenNeeded() {
        assertEquals("0:00", formatTimeMs(0))
        assertEquals("2:11", formatTimeMs(131_000))
        assertEquals("1:02:03", formatTimeMs(3_723_000))
        assertEquals("0:00", formatTimeMs(-5))
    }

    @Test
    fun withStartTime_setsReplacesAndRemovesTheOffset() {
        val url = "https://storage.example.com/ep.aac"
        assertEquals(url, WearConfig.withStartTime(url, null))
        assertEquals(url, WearConfig.withStartTime(url, 0.0))
        assertEquals("$url?t=12.5", WearConfig.withStartTime(url, 12.5))
        assertEquals("$url?t=30.0", WearConfig.withStartTime("$url?t=12.5", 30.0))
        assertEquals(url, WearConfig.withStartTime("$url?t=12.5", null))
        assertEquals("$url?sig=abc&t=7.0#frag", WearConfig.withStartTime("$url?sig=abc#frag", 7.0))
    }

    @Test
    fun audioUrlEndpoint_isThePodcastsAudioUrlRoute() {
        assertEquals(
            WearConfig.BASE_URL.trimEnd('/') + "/podcasts/p1/episodes/e1/audio/url",
            WearConfig.audioUrlEndpoint("p1", "e1")
        )
    }

    @Test
    fun parseUrl_readsUrlFromAudioUrlResponse() {
        assertEquals("https://x/y.aac", AudioUrlClient.parseUrl("""{"url":"https://x/y.aac"}"""))
        assertTrue(runCatching { AudioUrlClient.parseUrl("{}") }.isFailure)
    }

    @Test
    fun streamStart_completeEpisodeResumesByRange_neverByQueryParam() {
        val start = StreamLogic.streamStart(754_200, audioComplete = true)
        assertNull(start.timeSeconds)              // plain (signed) URL, no t=
        assertEquals(754_200, start.playerStartMs) // player requests the byte range itself
        assertEquals(0, start.streamOffsetMs)      // so player time == episode time
    }

    @Test
    fun streamStart_generatingEpisodeResumesWithTimeParam() {
        val start = StreamLogic.streamStart(754_200, audioComplete = false)
        assertEquals(754.2, start.timeSeconds!!, 0.0001)
        assertEquals(0, start.playerStartMs)
        assertEquals(754_200, start.streamOffsetMs)
    }

    @Test
    fun streamStart_fromTheBeginningNeedsNoOffsetEitherWay() {
        for (complete in listOf(true, false)) {
            val start = StreamLogic.streamStart(0, complete)
            assertNull(start.timeSeconds)
            assertEquals(0, start.playerStartMs)
            assertEquals(0, start.streamOffsetMs)
        }
    }

    @Test
    fun skipTarget_accumulatesTapsAndClampsToWhatExists() {
        val end = 600_000L
        var target = 100_000L
        repeat(3) { target = StreamLogic.skipTarget(target, 10, end) }
        assertEquals(130_000, target)                                 // three +10s taps
        assertEquals(0, StreamLogic.skipTarget(5_000, -10, end))      // can't go before the start
        assertEquals(end, StreamLogic.skipTarget(595_000, 10, end))   // or past the end
    }

    @Test
    fun isGenuineEnd_requiresCompleteAudioAndPlaybackAtItsEnd() {
        assertTrue(StreamLogic.isGenuineEnd(597_500, audioComplete = true, audioDurationMs = 600_000))
        assertFalse(StreamLogic.isGenuineEnd(300_000, audioComplete = true, audioDurationMs = 600_000))
        assertFalse(StreamLogic.isGenuineEnd(600_000, audioComplete = false, audioDurationMs = 600_000))
        assertFalse(StreamLogic.isGenuineEnd(600_000, audioComplete = true, audioDurationMs = 0))
    }

    @Test
    fun canSeekInPlayer_staysWithinTheCurrentStreamWindow() {
        // Stream started at 60s and is 100s long (seekable): reachable window is [60s, 157s].
        assertTrue(StreamLogic.canSeekInPlayer(90_000, 60_000, 100_000, true))
        assertFalse(StreamLogic.canSeekInPlayer(30_000, 60_000, 100_000, true))
        assertFalse(StreamLogic.canSeekInPlayer(159_000, 60_000, 100_000, true))
        assertFalse(StreamLogic.canSeekInPlayer(90_000, 60_000, 100_000, false))
    }

    @Test
    fun pairTokenMessage_decodesPhoneReply() {
        val ok = PairTokenMessage.decode("""{"customToken":"tok"}""".encodeToByteArray())
        assertEquals("tok", ok.customToken)
        assertNull(ok.error)

        val failed = PairTokenMessage.decode("""{"error":"Not signed in on phone"}""".encodeToByteArray())
        assertNull(failed.customToken)
        assertEquals("Not signed in on phone", failed.error)
    }
}
