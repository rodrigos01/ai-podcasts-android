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

    /** Emits an episode id when its position was updated from the other device. */
    val remoteUpdates: SharedFlow<String> get() = store.remoteUpdates

    fun getPositionMs(episodeId: String): Long = store.getPositionMs(episodeId)

    fun getPositionSeconds(episodeId: String): Double = getPositionMs(episodeId) / 1000.0

    /** Progress ticks are throttled before syncing; pass [checkpoint] to sync immediately. */
    fun savePositionMs(episodeId: String, positionMs: Long, checkpoint: Boolean = false) =
        store.savePositionMs(episodeId, positionMs, checkpoint)

    fun clearPosition(episodeId: String) = store.clearPosition(episodeId)
}
