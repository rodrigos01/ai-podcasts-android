package com.rodrigos01.aipodcasts.wear.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

/**
 * Runs before every HTTP request ExoPlayer makes (including its own retries) and attaches a
 * fresh Firebase ID token, so a long episode never hits a mid-stream 401 from an expired token.
 */
@UnstableApi
class AudioStreamTokenResolver(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val token = runCatching {
            auth.currentUser?.getIdToken(false)?.let { Tasks.await(it, 10, TimeUnit.SECONDS).token }
        }.getOrNull()
        if (token.isNullOrBlank()) return dataSpec

        val headers = dataSpec.httpRequestHeaders.toMutableMap()
        headers["Authorization"] = "Bearer $token"
        return dataSpec.buildUpon().setHttpRequestHeaders(headers).build()
    }
}
