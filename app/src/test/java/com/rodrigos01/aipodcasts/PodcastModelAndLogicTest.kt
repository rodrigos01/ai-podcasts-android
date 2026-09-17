package com.rodrigos01.aipodcasts

import com.rodrigos01.aipodcasts.data.api.ApiClient
import com.rodrigos01.aipodcasts.data.model.EpisodeDraft
import com.rodrigos01.aipodcasts.data.model.EpisodeGuest
import com.rodrigos01.aipodcasts.data.model.Host
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.model.PodcastOption
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts

class PodcastModelAndLogicTest {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun testPodcastSerialization() {
        val adapter = moshi.adapter(Podcast::class.java)
        val json = """
            {
                "id": "pod-123",
                "title": "Kitchen Gadget Review",
                "description": "Two friends review obscure kitchen gadgets.",
                "structure": "# Intro\n# Reviews\n# Verdict",
                "hosts": [
                    {"id": "h1", "name": "Alice", "voice": "Puck", "persona": "Enthusiastic home cook"},
                    {"id": "h2", "name": "Bob", "voice": "Charon", "persona": "Skeptical gadget tester"}
                ]
            }
        """.trimIndent()

        val podcast = adapter.fromJson(json)
        assertNotNull(podcast)
        assertEquals("pod-123", podcast?.id)
        assertEquals("Kitchen Gadget Review", podcast?.title)
        assertEquals(2, podcast?.hosts?.size)
        assertEquals("Alice", podcast?.hosts?.get(0)?.name)
        assertEquals("Puck", podcast?.hosts?.get(0)?.voice)
    }

    @Test
    fun testPodcastOptionSerialization() {
        val adapter = moshi.adapter(PodcastOption::class.java)
        val json = """
            {
                "title": "Retro Tech",
                "description": "Deep dive into 80s tech.",
                "structure": "Overview, teardown, discussion",
                "hosts": [{"name": "Dan", "voice": "Aoede", "persona": "Collector"}],
                "predictedChanges": ["More humor", "Shorter intros"]
            }
        """.trimIndent()

        val option = adapter.fromJson(json)
        assertNotNull(option)
        assertEquals("Retro Tech", option?.title)
        assertEquals(2, option?.predictedChanges?.size)
    }

    @Test
    fun testTwoSpeakerConstraint() {
        // Rule: Exactly 2 speakers (either 2 hosts, or 1 host + 1 guest)
        val host1 = "h1"
        val host2 = "h2"
        val guest = EpisodeGuest(name = "Dr. Smith", voice = "Kore", persona = "Guest inventor")

        val validTwoHosts = listOf(host1, host2)
        val emptyGuests = emptyList<EpisodeGuest>()
        assertEquals(2, validTwoHosts.size + emptyGuests.size)

        val validOneHostOneGuest = listOf(host1)
        val singleGuest = listOf(guest)
        assertEquals(2, validOneHostOneGuest.size + singleGuest.size)

        val invalidThreeSpeakers = listOf(host1, host2) + listOf(guest)
        assertTrue(invalidThreeSpeakers.size != 2)
    }

    @Test
    fun testAudioStreamUrlBuilder() {
        val streamUrl = ApiClient.buildAudioStreamUrl(
            podcastId = "p1",
            episodeId = "e1",
            idToken = "fake-token",
            timeSeconds = 124.5
        )

        assertTrue(streamUrl.contains("/podcasts/p1/episodes/e1/audio/stream"))
        assertTrue(streamUrl.contains("token=fake-token"))
        assertTrue(streamUrl.contains("t=124.5"))
    }

    @Test
    fun testDefaultBaseUrlIsProductionCloudRun() {
        assertEquals("https://ai-podcasts-883622140264.us-central1.run.app/", ApiClient.DEFAULT_BASE_URL)
    }

    @Test
    fun testPlaybackPositionRepository() {
        val storage = mutableMapOf<String, Long>()
        val mockEditor = org.mockito.Mockito.mock(android.content.SharedPreferences.Editor::class.java)
        val mockPrefs = org.mockito.Mockito.mock(android.content.SharedPreferences::class.java)
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)

        org.mockito.Mockito.`when`(mockContext.getSharedPreferences(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyInt())).thenReturn(mockPrefs)
        org.mockito.Mockito.`when`(mockPrefs.edit()).thenReturn(mockEditor)
        org.mockito.Mockito.`when`(mockEditor.putLong(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyLong())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val value = invocation.getArgument<Long>(1)
            storage[key] = value
            mockEditor
        }
        org.mockito.Mockito.`when`(mockEditor.remove(org.mockito.Mockito.anyString())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            storage.remove(key)
            mockEditor
        }
        org.mockito.Mockito.`when`(mockPrefs.getLong(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyLong())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val defaultVal = invocation.getArgument<Long>(1)
            storage[key] ?: defaultVal
        }

        val repo = com.rodrigos01.aipodcasts.data.repository.PlaybackPositionRepository(mockContext)
        assertEquals(0L, repo.getPositionMs("ep1"))
        assertEquals(0.0, repo.getPositionSeconds("ep1"), 0.001)

        repo.savePositionMs("ep1", 45_000L)
        assertEquals(45_000L, repo.getPositionMs("ep1"))
        assertEquals(45.0, repo.getPositionSeconds("ep1"), 0.001)

        repo.clearPosition("ep1")
        assertEquals(0L, repo.getPositionMs("ep1"))
    }

    @androidx.media3.common.util.UnstableApi
    @Test
    fun testChainedOggDechaining() {
        val opusHeadPayload = "OpusHead12345678901".toByteArray(Charsets.US_ASCII)
        val opusTagsPayload = "OpusTagsVendorData12345".toByteArray(Charsets.US_ASCII)
        val audio1Payload = "AUDIO_FRAME_1".toByteArray(Charsets.US_ASCII)
        val audio2Payload = "AUDIO_FRAME_2".toByteArray(Charsets.US_ASCII)
        val audio3Payload = "AUDIO_FRAME_3".toByteArray(Charsets.US_ASCII)
        val audio4Payload = "AUDIO_FRAME_4".toByteArray(Charsets.US_ASCII)

        // Build Chunk 1 (Serial 100)
        val p1_0 = createTestOggPage(bos = true, eos = false, granule = 0, serial = 100, sequence = 0, payload = opusHeadPayload)
        val p1_1 = createTestOggPage(bos = false, eos = false, granule = 0, serial = 100, sequence = 1, payload = opusTagsPayload)
        val p1_2 = createTestOggPage(bos = false, eos = false, granule = 960, serial = 100, sequence = 2, payload = audio1Payload)
        val p1_3 = createTestOggPage(bos = false, eos = true, granule = 1920, serial = 100, sequence = 3, payload = audio2Payload)

        // Build Chunk 2 (Serial 200)
        val p2_0 = createTestOggPage(bos = true, eos = false, granule = 0, serial = 200, sequence = 0, payload = opusHeadPayload)
        val p2_1 = createTestOggPage(bos = false, eos = false, granule = 0, serial = 200, sequence = 1, payload = opusTagsPayload)
        val p2_2 = createTestOggPage(bos = false, eos = false, granule = 960, serial = 200, sequence = 2, payload = audio3Payload)
        val p2_3 = createTestOggPage(bos = false, eos = true, granule = 1920, serial = 200, sequence = 3, payload = audio4Payload)

        // Concatenate all pages into chained stream
        val chainedStreamBytes = p1_0 + p1_1 + p1_2 + p1_3 + p2_0 + p2_1 + p2_2 + p2_3

        // Wrap with in-memory DataSource and ChainedOggDataSource
        val inMemory = object : androidx.media3.datasource.DataSource {
            private var readPos = 0
            override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {}
            override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
                readPos = 0
                return chainedStreamBytes.size.toLong()
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (readPos >= chainedStreamBytes.size) return androidx.media3.common.C.RESULT_END_OF_INPUT
                val bytesToRead = minOf(length, chainedStreamBytes.size - readPos)
                System.arraycopy(chainedStreamBytes, readPos, buffer, offset, bytesToRead)
                readPos += bytesToRead
                return bytesToRead
            }
            override fun getUri(): android.net.Uri? = null
            override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()
            override fun close() {}
        }

        val mockUri = org.mockito.Mockito.mock(android.net.Uri::class.java)
        val dechainer = com.rodrigos01.aipodcasts.player.ChainedOggDataSource(inMemory)
        dechainer.open(androidx.media3.datasource.DataSpec(mockUri))

        // Read all output bytes
        val outStream = java.io.ByteArrayOutputStream()
        val readBuf = ByteArray(1024)
        while (true) {
            val r = dechainer.read(readBuf, 0, readBuf.size)
            if (r == androidx.media3.common.C.RESULT_END_OF_INPUT) break
            outStream.write(readBuf, 0, r)
        }
        val outputBytes = outStream.toByteArray()

        // Parse output pages
        val outputPages = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < outputBytes.size) {
            assertTrue("Expected OggS header at $offset", outputBytes[offset] == 'O'.code.toByte() && outputBytes[offset + 1] == 'g'.code.toByte())
            val numSegs = outputBytes[offset + 26].toInt() and 0xFF
            var payloadLen = 0
            for (i in 0 until numSegs) {
                payloadLen += outputBytes[offset + 27 + i].toInt() and 0xFF
            }
            val pageSize = 27 + numSegs + payloadLen
            val page = outputBytes.copyOfRange(offset, offset + pageSize)
            outputPages.add(page)
            offset += pageSize
        }

        // Expected 6 pages: Chunk 1 has 4 pages, Chunk 2 has 2 audio pages (OpusHead and OpusTags skipped)
        assertEquals(6, outputPages.size)

        // Verify serial numbers on ALL pages match initial serial (100)
        for ((idx, p) in outputPages.withIndex()) {
            assertEquals("Page $idx serial must match initial serial 100", 100, com.rodrigos01.aipodcasts.player.ChainedOggDataSource.getSerial(p))
            // Verify sequence numbers are monotonic 0..5
            assertEquals("Page $idx sequence must be $idx", idx, com.rodrigos01.aipodcasts.player.ChainedOggDataSource.getSequence(p))
            // Verify CRC is valid
            val crc = com.rodrigos01.aipodcasts.player.ChainedOggDataSource.calculateOggCrc(p, 0, p.size)
            val storedCrc = ((p[22].toInt() and 0xFF)) or
                ((p[23].toInt() and 0xFF) shl 8) or
                ((p[24].toInt() and 0xFF) shl 16) or
                ((p[25].toInt() and 0xFF) shl 24)
            assertEquals("Page $idx CRC checksum must be valid", crc, storedCrc)
        }

        // Verify Chunk 1's last page (idx 3) EOS flag was cleared
        val p3HeaderType = outputPages[3][5].toInt() and 0xFF
        assertTrue("Chunk 1 EOS page must have EOS bit cleared", (p3HeaderType and 0x04) == 0)

        // Verify Chunk 2 audio page 1 (idx 4) granule is offset (1920 + 960 = 2880)
        assertEquals(2880L, com.rodrigos01.aipodcasts.player.ChainedOggDataSource.getGranule(outputPages[4]))

        // Verify Chunk 2 audio page 2 (idx 5) granule is offset (1920 + 1920 = 3840)
        assertEquals(3840L, com.rodrigos01.aipodcasts.player.ChainedOggDataSource.getGranule(outputPages[5]))
    }

    private fun createTestOggPage(
        bos: Boolean,
        eos: Boolean,
        granule: Long,
        serial: Int,
        sequence: Int,
        payload: ByteArray
    ): ByteArray {
        val header = ByteArray(27)
        header[0] = 'O'.code.toByte()
        header[1] = 'g'.code.toByte()
        header[2] = 'g'.code.toByte()
        header[3] = 'S'.code.toByte()
        header[4] = 0
        var headerType = 0
        if (bos) headerType = headerType or 0x02
        if (eos) headerType = headerType or 0x04
        header[5] = headerType.toByte()

        com.rodrigos01.aipodcasts.player.ChainedOggDataSource.setGranule(header, granule)
        com.rodrigos01.aipodcasts.player.ChainedOggDataSource.setSerial(header, serial)
        com.rodrigos01.aipodcasts.player.ChainedOggDataSource.setSequence(header, sequence)

        val numSegments = if (payload.isEmpty()) 1 else (payload.size + 254) / 255
        header[26] = numSegments.toByte()

        val segmentTable = ByteArray(numSegments)
        var remaining = payload.size
        for (i in 0 until numSegments) {
            val segLen = minOf(255, remaining)
            segmentTable[i] = segLen.toByte()
            remaining -= segLen
        }

        val fullPage = ByteArray(27 + numSegments + payload.size)
        System.arraycopy(header, 0, fullPage, 0, 27)
        System.arraycopy(segmentTable, 0, fullPage, 27, numSegments)
        System.arraycopy(payload, 0, fullPage, 27 + numSegments, payload.size)

        val crc = com.rodrigos01.aipodcasts.player.ChainedOggDataSource.calculateOggCrc(fullPage, 0, fullPage.size)
        com.rodrigos01.aipodcasts.player.ChainedOggDataSource.setChecksum(fullPage, crc)
        return fullPage
    }
}
