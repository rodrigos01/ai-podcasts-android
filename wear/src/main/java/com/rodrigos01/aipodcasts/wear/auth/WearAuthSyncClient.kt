package com.rodrigos01.aipodcasts.wear.auth

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Watch side of the auth-pairing relay: asks the connected phone to mint a Firebase custom token
 * for its signed-in account. The token arrives asynchronously via [WearAuthResultHandler] ->
 * [WearAuthResultBus], not as a return value, since Data Layer messaging is fire-and-forget.
 */
class WearAuthSyncClient(private val context: Context) {

    /** Fails if no phone node is currently connected/reachable. */
    suspend fun requestPairing(): Result<Unit> = runCatching {
        val appContext = context.applicationContext
        val phoneNode = Wearable.getNodeClient(appContext).connectedNodes.await().firstOrNull()
            ?: error("No paired phone connected")
        Wearable.getMessageClient(appContext)
            .sendMessage(phoneNode.id, WearMessagePaths.AUTH_TOKEN_REQUEST, ByteArray(0))
            .await()
        Unit
    }
}
