package com.rodrigos01.aipodcasts.wear.auth

import org.json.JSONObject

/**
 * Wearable Data Layer message paths for the phone<->watch auth-pairing relay: the watch sends an
 * empty request on [AUTH_TOKEN_REQUEST] and the phone replies on [AUTH_TOKEN_RESULT] with a
 * [PairTokenMessage]. Mirrors `com.rodrigos01.aipodcasts.wear.WearMessagePaths` in :app - keep
 * the two in sync.
 */
object WearMessagePaths {
    const val AUTH_TOKEN_REQUEST = "/aipodcasts/auth-token-request"
    const val AUTH_TOKEN_RESULT = "/aipodcasts/auth-token-result"
}

/** Mirrors `PairTokenMessage` in :app - keep the two in sync. */
data class PairTokenMessage(
    val customToken: String? = null,
    val error: String? = null
) {
    companion object {
        fun decode(bytes: ByteArray): PairTokenMessage {
            val json = JSONObject(bytes.decodeToString())
            return PairTokenMessage(
                customToken = json.optString("customToken").takeIf { it.isNotBlank() },
                error = json.optString("error").takeIf { it.isNotBlank() }
            )
        }
    }
}
