package com.rodrigos01.aipodcasts.watch

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "WatchAuthRelay"

/**
 * Phone side of the Wear OS auth-pairing relay: the watch has no login screen, so it asks the
 * phone (over the Wearable Data Layer) for a Firebase custom token for whichever account is
 * signed in here, and signs itself in with it.
 *
 * Lives in an object so it can be driven both by the manifest-declared [WatchAuthRelayService]
 * (best-effort when the app isn't running) and by a live listener registered in
 * [AIPodcastsApplication] while the process is alive.
 */
object WatchAuthRelayHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun handle(context: Context, messageEvent: MessageEvent) {
        if (messageEvent.path != WatchMessagePaths.AUTH_TOKEN_REQUEST) return
        val sourceNodeId = messageEvent.sourceNodeId
        val appContext = context.applicationContext

        scope.launch {
            val authRepository = AIPodcastsApplication.instance.authRepository
            val response = if (authRepository.currentUser == null) {
                PairTokenMessage(error = "Not signed in on phone")
            } else {
                PairingApi(authRepository).pairDevice().fold(
                    onSuccess = { PairTokenMessage(customToken = it) },
                    onFailure = { error ->
                        Log.w(TAG, "pairDevice() failed", error)
                        PairTokenMessage(error = error.localizedMessage ?: "Failed to pair watch")
                    }
                )
            }

            runCatching {
                Wearable.getMessageClient(appContext)
                    .sendMessage(sourceNodeId, WatchMessagePaths.AUTH_TOKEN_RESULT, response.encode())
                    .await()
            }.onFailure { Log.w(TAG, "Failed to send pairing result to watch", it) }
        }
    }
}

class WatchAuthRelayService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        WatchAuthRelayHandler.handle(this, messageEvent)
    }
}
