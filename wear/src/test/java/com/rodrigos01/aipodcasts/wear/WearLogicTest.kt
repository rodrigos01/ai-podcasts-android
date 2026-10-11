package com.rodrigos01.aipodcasts.wear

import com.rodrigos01.aipodcasts.wear.auth.PairTokenMessage
import com.rodrigos01.aipodcasts.wear.data.PlaybackPositionStore
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
        assertFalse(PlaybackPositionStore.canResume(0))
        assertFalse(PlaybackPositionStore.canResume(2_999))
        assertTrue(PlaybackPositionStore.canResume(3_000))
    }

    @Test
    fun formatTimeMs_usesHoursOnlyWhenNeeded() {
        assertEquals("0:00", formatTimeMs(0))
        assertEquals("2:11", formatTimeMs(131_000))
        assertEquals("1:02:03", formatTimeMs(3_723_000))
        assertEquals("0:00", formatTimeMs(-5))
    }

    @Test
    fun streamUrl_addsOffsetOnlyWhenResuming() {
        val base = WearConfig.BASE_URL.trimEnd('/') + "/podcasts/p1/episodes/e1/audio/stream"
        assertEquals(base, WearConfig.buildAudioStreamUrl("p1", "e1"))
        assertEquals(base, WearConfig.buildAudioStreamUrl("p1", "e1", 0.0))
        assertEquals("$base?t=12.5", WearConfig.buildAudioStreamUrl("p1", "e1", 12.5))
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
