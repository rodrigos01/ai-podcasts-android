package com.rodrigos01.aipodcasts.wear.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches an episode's public audio URL (`GET .../audio/url`, the only authenticated call the
 * player makes). The returned URL carries no credentials and is stable, so ExoPlayer streams it
 * directly - no per-request token handling in the playback service.
 */
class AudioUrlClient(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    suspend fun getAudioUrl(podcastId: String, episodeId: String): Result<String> = runCatching {
        val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: error("Not signed in")
        withContext(Dispatchers.IO) {
            val connection = URL(WearConfig.audioUrlEndpoint(podcastId, episodeId)).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Authorization", "Bearer $token")
                if (connection.responseCode !in 200..299) error("Couldn't load episode (HTTP ${connection.responseCode})")
                parseUrl(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        private const val TIMEOUT_MS = 30_000

        fun parseUrl(responseBody: String): String =
            JSONObject(responseBody).optString("url").takeIf { it.isNotBlank() } ?: error("Response had no audio URL")
    }
}
