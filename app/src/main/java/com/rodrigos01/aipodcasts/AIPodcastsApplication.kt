package com.rodrigos01.aipodcasts

import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.google.firebase.FirebaseApp
import com.rodrigos01.aipodcasts.data.repository.AuthRepository
import com.rodrigos01.aipodcasts.data.repository.EpisodeRepository
import com.rodrigos01.aipodcasts.data.repository.PlaybackPositionRepository
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
import com.rodrigos01.aipodcasts.data.repository.SettingsRepository
import com.rodrigos01.aipodcasts.data.repository.SourceRepository
import com.rodrigos01.aipodcasts.player.PodcastAudioController

@UnstableApi
class AIPodcastsApplication : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var podcastRepository: PodcastRepository
        private set

    lateinit var sourceRepository: SourceRepository
        private set

    lateinit var episodeRepository: EpisodeRepository
        private set

    lateinit var playbackPositionRepository: PlaybackPositionRepository
        private set

    lateinit var audioController: PodcastAudioController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        FirebaseApp.initializeApp(this)

        settingsRepository = SettingsRepository(this)
        authRepository = AuthRepository()
        podcastRepository = PodcastRepository()
        sourceRepository = SourceRepository()
        episodeRepository = EpisodeRepository()
        playbackPositionRepository = PlaybackPositionRepository(this)
        audioController = PodcastAudioController(this, playbackPositionRepository, episodeRepository)
    }

    companion object {
        lateinit var instance: AIPodcastsApplication
            private set
    }
}
