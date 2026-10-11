package com.rodrigos01.aipodcasts.watch

import org.json.JSONObject

/**
 * Wearable Data Layer message paths for the phone<->watch auth-pairing relay: the watch app
 * (`:wear`) sends an empty request on [AUTH_TOKEN_REQUEST] and the phone replies on
 * [AUTH_TOKEN_RESULT] with a [PairTokenMessage]. Mirrored in `:wear`'s `WearMessagePaths` and
 * `PairTokenMessage` - keep them in sync.
 */
object WatchMessagePaths {
    const val AUTH_TOKEN_REQUEST = "/aipodcasts/auth-token-request"
    const val AUTH_TOKEN_RESULT = "/aipodcasts/auth-token-result"
}

data class PairTokenMessage(
    val customToken: String? = null,
    val error: String? = null
) {
    fun encode(): ByteArray = JSONObject().apply {
        customToken?.let { put("customToken", it) }
        error?.let { put("error", it) }
    }.toString().encodeToByteArray()
}
