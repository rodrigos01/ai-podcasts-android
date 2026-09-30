package com.rodrigos01.aipodcasts

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import com.rodrigos01.aipodcasts.data.repository.AuthRepository
import com.rodrigos01.aipodcasts.player.AudioStreamTokenResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@UnstableApi
class AudioStreamAuthTest {

    @Test
    fun testAudioStreamTokenResolverInjectsHeader() {
        val mockAuthRepo = mock(AuthRepository::class.java)
        `when`(mockAuthRepo.getIdTokenBlocking(false)).thenReturn("valid-token-123")

        val resolver = AudioStreamTokenResolver(mockAuthRepo)
        val mockUri = mock(android.net.Uri::class.java)
        `when`(mockUri.toString()).thenReturn("https://api.example.com/audio/stream")

        val originalDataSpec = DataSpec.Builder()
            .setUri(mockUri)
            .build()

        val resolvedDataSpec = resolver.resolveDataSpec(originalDataSpec)

        assertEquals("Bearer valid-token-123", resolvedDataSpec.httpRequestHeaders["Authorization"])
    }

    @Test
    fun testAudioStreamTokenResolverPreservesDataSpecWhenTokenNull() {
        val mockAuthRepo = mock(AuthRepository::class.java)
        `when`(mockAuthRepo.getIdTokenBlocking(false)).thenReturn(null)

        val resolver = AudioStreamTokenResolver(mockAuthRepo)
        val mockUri = mock(android.net.Uri::class.java)
        `when`(mockUri.toString()).thenReturn("https://api.example.com/audio/stream")

        val originalDataSpec = DataSpec.Builder()
            .setUri(mockUri)
            .build()

        val resolvedDataSpec = resolver.resolveDataSpec(originalDataSpec)

        assertNull(resolvedDataSpec.httpRequestHeaders["Authorization"])
    }

    @Test
    fun testBuildAudioStreamUrlDoesNotIncludeTokenWhenOmitted() {
        val urlWithTime = com.rodrigos01.aipodcasts.data.api.ApiClient.buildAudioStreamUrl(
            podcastId = "pod-1",
            episodeId = "ep-1",
            timeSeconds = 75.0
        )
        assertTrue(urlWithTime.contains("/podcasts/pod-1/episodes/ep-1/audio/stream?t=75.0"))
        assertFalse(urlWithTime.contains("token="))

        val urlClean = com.rodrigos01.aipodcasts.data.api.ApiClient.buildAudioStreamUrl(
            podcastId = "pod-1",
            episodeId = "ep-1"
        )
        assertTrue(urlClean.endsWith("/podcasts/pod-1/episodes/ep-1/audio/stream"))
        assertFalse(urlClean.contains("token="))
        assertFalse(urlClean.contains("?"))
    }
}
