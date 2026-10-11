package com.rodrigos01.aipodcasts.wear.auth

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Handles the phone's reply to an auth-pairing request: signs the watch in with the Firebase
 * custom token and publishes the outcome on [WearAuthResultBus]. Kept in a standalone object so
 * it is reachable both from the manifest-declared [WearAuthResultListenerService] (best-effort
 * when the app isn't running) and from a live listener registered in
 * [com.rodrigos01.aipodcasts.wear.WearApplication] while the process is alive.
 */
object WearAuthResultHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun handle(messageEvent: MessageEvent) {
        if (messageEvent.path != WearMessagePaths.AUTH_TOKEN_RESULT) return

        val message = runCatching { PairTokenMessage.decode(messageEvent.data) }
            .getOrElse { PairTokenMessage(error = "Malformed response from phone") }

        val customToken = message.customToken
        if (customToken == null) {
            WearAuthResultBus.emit(
                message.copy(error = message.error ?: "Phone returned no sign-in token")
            )
            return
        }

        scope.launch {
            val outcome = runCatching { FirebaseAuth.getInstance().signInWithCustomToken(customToken).await() }
            WearAuthResultBus.emit(
                outcome.fold(
                    onSuccess = { PairTokenMessage(customToken = customToken) },
                    onFailure = { PairTokenMessage(error = it.localizedMessage ?: "Sign in failed") }
                )
            )
        }
    }
}

class WearAuthResultListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        WearAuthResultHandler.handle(messageEvent)
    }
}
