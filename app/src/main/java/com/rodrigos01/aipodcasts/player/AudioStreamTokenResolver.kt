package com.rodrigos01.aipodcasts.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.rodrigos01.aipodcasts.data.repository.AuthRepository

/**
 * Resolves each DataSpec before ExoPlayer opens an HTTP connection, dynamically injecting
 * a fresh Firebase Auth ID token into the HTTP Authorization header and keeping any
 * token query parameter in sync.
 */
@UnstableApi
class AudioStreamTokenResolver(
    private val authRepository: AuthRepository
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val token = authRepository.getIdTokenBlocking(forceRefresh = false)
        val builder = dataSpec.buildUpon()

        if (!token.isNullOrBlank()) {
            val headers = dataSpec.httpRequestHeaders.toMutableMap()
            headers["Authorization"] = "Bearer $token"
            builder.setHttpRequestHeaders(headers)
        }

        val uriString = dataSpec.uri.toString()
        if (uriString.contains("token=")) {
            // Strip token parameter completely from the query string
            val cleanedUri = uriString
                .replace(Regex("([?&])token=[^&]*(&|$)")) { matchResult ->
                    val prefix = matchResult.groupValues[1]
                    val suffix = matchResult.groupValues[2]
                    if (prefix == "?" && suffix == "&") "?" else if (suffix == "&") "&" else ""
                }
                .trimEnd('?')
            val parsedUri = runCatching { Uri.parse(cleanedUri) }.getOrNull()
            if (parsedUri != null) {
                builder.setUri(parsedUri)
            }
        }

        return builder.build()
    }
}
