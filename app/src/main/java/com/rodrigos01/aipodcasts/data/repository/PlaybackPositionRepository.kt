package com.rodrigos01.aipodcasts.data.repository

import android.content.Context
import com.rodrigos01.aipodcasts.sync.SyncedPositionStore
import kotlinx.coroutines.flow.SharedFlow

/**
 * Per-episode resume position. Backed by the store shared with the Wear OS app, so progress made
 * on either device is picked up on the other (last write wins).
 */
class PlaybackPositionRepository(context: Context) {

    private val store = SyncedPositionStore.get(context)
    private val lastPlayedPrefs = context.applicationContext.getSharedPreferences("last_played", Context.MODE_PRIVATE)

    /** The episode that was loaded in the player when the app last ran, so it can be restored paused. */
    data class LastPlayed(val podcastId: String, val podcastTitle: String, val episodeId: String)

    fun getLastPlayed(): LastPlayed? {
        val podcastId = lastPlayedPrefs.getString(KEY_PODCAST_ID, null)
        val episodeId = lastPlayedPrefs.getString(KEY_EPISODE_ID, null)
        if (podcastId.isNullOrBlank() || episodeId.isNullOrBlank()) return null
        return LastPlayed(podcastId, lastPlayedPrefs.getString(KEY_PODCAST_TITLE, "").orEmpty(), episodeId)
    }

    fun saveLastPlayed(podcastId: String, podcastTitle: String, episodeId: String) {
        lastPlayedPrefs.edit()
            .putString(KEY_PODCAST_ID, podcastId)
            .putString(KEY_PODCAST_TITLE, podcastTitle)
            .putString(KEY_EPISODE_ID, episodeId)
            .apply()
    }

    fun clearLastPlayed() = lastPlayedPrefs.edit().clear().apply()

    /** Emits an episode id when its position was updated from the other device. */
    val remoteUpdates: SharedFlow<String> get() = store.remoteUpdates

    fun getPositionMs(episodeId: String): Long = store.getPositionMs(episodeId)

    fun getPositionSeconds(episodeId: String): Double = getPositionMs(episodeId) / 1000.0

    /** Progress ticks are throttled before syncing; pass [checkpoint] to sync immediately. */
    fun savePositionMs(episodeId: String, positionMs: Long, checkpoint: Boolean = false) =
        store.savePositionMs(episodeId, positionMs, checkpoint)

    fun clearPosition(episodeId: String) = store.clearPosition(episodeId)

    private companion object {
        const val KEY_PODCAST_ID = "podcastId"
        const val KEY_PODCAST_TITLE = "podcastTitle"
        const val KEY_EPISODE_ID = "episodeId"
    }
}
