package com.rodrigos01.aipodcasts

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.rodrigos01.aipodcasts.data.repository.AuthRepository
import com.rodrigos01.aipodcasts.player.AudioStreamTokenResolver
import com.rodrigos01.aipodcasts.player.PodcastAudioController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.IOException

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

    @Test
    fun testIsHttp401DetectionWithInvalidResponseCodeException() {
        // 401 error code exception
        val dataSpec = DataSpec.Builder().setUri(mock(android.net.Uri::class.java)).build()
        val cause401 = HttpDataSource.InvalidResponseCodeException(
            401,
            "Unauthorized",
            null,
            emptyMap(),
            dataSpec,
            byteArrayOf()
        )
        val error401 = PlaybackException(
            "Playback failed",
            cause401,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )

        assertTrue(PodcastAudioController.isHttp401(error401))

        // 500 server error exception
        val cause500 = HttpDataSource.InvalidResponseCodeException(
            500,
            "Internal Server Error",
            null,
            emptyMap(),
            dataSpec,
            byteArrayOf()
        )
        val error500 = PlaybackException(
            "Playback failed",
            cause500,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )

        assertFalse(PodcastAudioController.isHttp401(error500))

        // Generic IO exception
        val genericError = PlaybackException(
            "Network connection timed out",
            IOException("Timeout"),
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        )

        assertFalse(PodcastAudioController.isHttp401(genericError))

        // Error message containing 401
        val errorWithMessage = PlaybackException(
            "Server returned 401 response",
            null,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )

        assertTrue(PodcastAudioController.isHttp401(errorWithMessage))
    }
}
