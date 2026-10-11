package com.rodrigos01.aipodcasts.wear

import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.wearable.Wearable
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.rodrigos01.aipodcasts.wear.auth.WearAuthResultHandler
import com.rodrigos01.aipodcasts.wear.data.AudioUrlClient
import com.rodrigos01.aipodcasts.wear.data.WearConfig
import com.rodrigos01.aipodcasts.wear.data.WearPodcastRepository
import com.rodrigos01.aipodcasts.wear.player.WearAudioController
import com.rodrigos01.aipodcasts.sync.PositionSync
import com.rodrigos01.aipodcasts.sync.SyncedPositionStore

/** Hand-wired singletons, same approach as the phone's `AIPodcastsApplication` (no DI framework). */
@UnstableApi
class WearApplication : Application() {

    lateinit var podcastRepository: WearPodcastRepository
        private set

    lateinit var playbackPositions: SyncedPositionStore
        private set

    lateinit var audioController: WearAudioController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // :wear shares :app's applicationId, so the phone's Firebase Android app registration
        // (and google-services.json) covers this module too.
        FirebaseApp.initializeApp(this)

        val firestore = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), WearConfig.FIRESTORE_DATABASE)
        podcastRepository = WearPodcastRepository(firestore)
        playbackPositions = SyncedPositionStore.get(this)
        PositionSync.start(this)
        audioController = WearAudioController(this, playbackPositions, podcastRepository, AudioUrlClient())

        // Alongside the manifest-declared WearAuthResultListenerService: manifest delivery to a
        // non-running app is best-effort, so also listen live while this process is alive.
        Wearable.getMessageClient(this).addListener { WearAuthResultHandler.handle(it) }
    }

    companion object {
        lateinit var instance: WearApplication
            private set
    }
}
