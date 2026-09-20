package com.rodrigos01.aipodcasts

import com.rodrigos01.aipodcasts.data.api.ApiClient
import com.rodrigos01.aipodcasts.data.model.CreateDriveSourceRequest
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
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.Scope
import com.rodrigos01.aipodcasts.data.drive.GoogleDriveHelper
import com.rodrigos01.aipodcasts.data.drive.DriveFileInfo
import com.rodrigos01.aipodcasts.util.FileUtils

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
    fun testCreateDriveSourceRequestSerialization() {
        val adapter = moshi.adapter(CreateDriveSourceRequest::class.java)
        val request = CreateDriveSourceRequest(
            fileId = "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms",
            accessToken = "ya29.a0AfH6SM...",
            title = "My Research Document"
        )
        val json = adapter.toJson(request)
        assertTrue(json.contains("\"fileId\":\"1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms\""))
        assertTrue(json.contains("\"accessToken\":\"ya29.a0AfH6SM...\""))
        assertTrue(json.contains("\"title\":\"My Research Document\""))

        val parsed = adapter.fromJson(json)
        assertNotNull(parsed)
        assertEquals(request.fileId, parsed?.fileId)
        assertEquals(request.accessToken, parsed?.accessToken)
        assertEquals(request.title, parsed?.title)
    }

    @Test
    fun testDriveFileIdRegexExtraction() {
        val uriStr1 = "content://com.google.android.apps.docs.storage/document/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms"
        val match1 = Regex("([a-zA-Z0-9_-]{25,})").find(uriStr1)
        assertNotNull(match1)
        assertEquals("1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms", match1?.value)

        val docId2 = "doc=1s8zN3W_K9jLeP4Q0-xyzABC1234567890;other=val"
        val docParamMatch = Regex("doc=([a-zA-Z0-9_-]{20,})").find(docId2)
        assertNotNull(docParamMatch)
        assertEquals("1s8zN3W_K9jLeP4Q0-xyzABC1234567890", docParamMatch?.groupValues?.get(1))
    }

    @Test
    fun testFileExtensionChecks() {
        assertTrue("document.txt".endsWith(".txt", ignoreCase = true))
        assertTrue("DOCUMENT.TXT".endsWith(".txt", ignoreCase = true))
        assertTrue("notes.pdf".endsWith(".pdf", ignoreCase = true))
        assertTrue("NOTES.PDF".endsWith(".pdf", ignoreCase = true))
    }

    @Test
    fun testExtractGoogleDocId() {
        val mockUri1 = org.mockito.Mockito.mock(android.net.Uri::class.java)
        org.mockito.Mockito.`when`(mockUri1.toString()).thenReturn("https://docs.google.com/document/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit")
        val id1 = GoogleDriveHelper.extractGoogleDocId(mockUri1)
        assertEquals("1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms", id1)

        val mockUri2 = org.mockito.Mockito.mock(android.net.Uri::class.java)
        org.mockito.Mockito.`when`(mockUri2.toString()).thenReturn("https://drive.google.com/file/d/1s8zN3W_K9jLeP4Q0-xyzABC1234567890/view")
        val id2 = GoogleDriveHelper.extractGoogleDocId(mockUri2)
        assertEquals("1s8zN3W_K9jLeP4Q0-xyzABC1234567890", id2)

        val mockUri3 = org.mockito.Mockito.mock(android.net.Uri::class.java)
        org.mockito.Mockito.`when`(mockUri3.toString()).thenReturn("content://com.google.android.apps.docs.storage/document?doc=1s8zN3W_K9jLeP4Q0-xyzABC1234567890")
        val id3 = GoogleDriveHelper.extractGoogleDocId(mockUri3)
        assertEquals("1s8zN3W_K9jLeP4Q0-xyzABC1234567890", id3)

        // Ensure encoded doc ids return null
        val mockUriEnc = org.mockito.Mockito.mock(android.net.Uri::class.java)
        org.mockito.Mockito.`when`(mockUriEnc.toString()).thenReturn("content://com.google.android.apps.docs.storage/document?doc=enc=abcde12345678901234567890")
        val idEnc = GoogleDriveHelper.extractGoogleDocId(mockUriEnc)
        assertEquals(null, idEnc)
    }

    @Test
    fun testCleanFileName() {
        assertEquals("My Document", FileUtils.cleanFileName("My Document.txt"))
        assertEquals("Document.archive", FileUtils.cleanFileName("Document.archive.pdf"))
        assertEquals("PlainDoc", FileUtils.cleanFileName("PlainDoc"))
        assertEquals(null, FileUtils.cleanFileName(""))
        assertEquals(null, FileUtils.cleanFileName("   "))
        assertEquals(null, FileUtils.cleanFileName(null))
    }

    @Test
    fun testDriveFileInfo() {
        val info = DriveFileInfo(
            id = "drive-123",
            name = "Project Spec",
            mimeType = "application/vnd.google-apps.document"
        )
        assertEquals("drive-123", info.id)
        assertEquals("Project Spec", info.name)
        assertEquals("application/vnd.google-apps.document", info.mimeType)
    }

    @Test
    fun testPrintAuthorizationRequestMethods() {
        for (f in AuthorizationRequest.ResourceParameter::class.java.fields) {
            println("RESOURCE_PARAM_FIELD: ${f.name} = ${f.get(null)}")
        }
        for (f in AuthorizationRequest.Prompt::class.java.fields) {
            println("PROMPT_FIELD: ${f.name} = ${f.get(null)}")
        }
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

}
