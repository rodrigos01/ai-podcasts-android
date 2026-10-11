package com.rodrigos01.aipodcasts.watch

import com.rodrigos01.aipodcasts.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Mints a Firebase custom token for the signed-in user so the watch can sign itself in.
 *
 * AI Podcasts shares its auth backend with AI Audio Book, so this calls the exact pairing
 * endpoint the audio book app uses (`POST api/auth/pair`, authenticated with the user's Firebase
 * ID token) rather than anything on the podcasts API. It deliberately does not go through
 * `ApiClient`, whose base URL is the podcasts backend (and rewritable from Settings).
 */
class PairingApi(
    private val authRepository: AuthRepository,
    private val baseUrl: String = PAIRING_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    suspend fun pairDevice(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val idToken = authRepository.getIdToken() ?: error("Not signed in on phone")
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + PAIR_PATH)
                .header("Authorization", "Bearer $idToken")
                .post(ByteArray(0).toRequestBody())
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Pairing failed (HTTP ${response.code})")
                parseCustomToken(body)
            }
        }
    }

    companion object {
        /** The AI Audio Book production API; same Firebase project, same auth backend. */
        const val PAIRING_BASE_URL = "https://ai-audio-book-api-883622140264.us-central1.run.app/"
        const val PAIR_PATH = "/api/auth/pair"

        fun parseCustomToken(responseBody: String): String =
            JSONObject(responseBody).optString("customToken").takeIf { it.isNotBlank() }
                ?: error("Pairing response had no token")
    }
}
