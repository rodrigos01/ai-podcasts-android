package com.rodrigos01.aipodcasts.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Minimal key/value storage the ledger needs (backed by SharedPreferences in the app). */
interface LongStorage {
    fun get(key: String): Long?
    fun put(key: String, value: Long)
    fun remove(key: String)
}

/** Sends a position change to the other device. Implementations must not throw. */
fun interface PositionPublisher {
    fun publish(episodeId: String, positionMs: Long, updatedAt: Long, urgent: Boolean)
}

/**
 * Per-episode resume positions, reconciled between devices last-writer-wins: every write is
 * stamped with the time it was made, and a position arriving from the other device only replaces
 * the local one if its stamp is newer. Finishing/clearing an episode is itself a (zero-position)
 * write, so it propagates instead of being undone by an older position from the other device.
 *
 * Progress ticks are saved locally every time but only published every [publishIntervalMs]
 * unless the caller marks the write as a `checkpoint` (pause, seek, switching episodes, ...).
 */
class PositionLedger(
    private val storage: LongStorage,
    private val publisher: PositionPublisher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val publishIntervalMs: Long = PUBLISH_INTERVAL_MS
) {
    private val lastPublishedAt = mutableMapOf<String, Long>()
    private val _remoteUpdates = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Emits the episode id whenever a position was replaced by one from the other device. */
    val remoteUpdates: SharedFlow<String> = _remoteUpdates.asSharedFlow()

    @Synchronized
    fun getPositionMs(episodeId: String): Long =
        if (episodeId.isBlank()) 0L else storage.get(POS_PREFIX + episodeId) ?: 0L

    @Synchronized
    fun savePositionMs(episodeId: String, positionMs: Long, checkpoint: Boolean = false) {
        if (episodeId.isBlank()) return
        if (positionMs <= 0L) {
            clearPosition(episodeId)
            return
        }
        val now = clock()
        storage.put(POS_PREFIX + episodeId, positionMs)
        storage.put(TS_PREFIX + episodeId, now)
        val last = lastPublishedAt[episodeId]
        if (checkpoint || last == null || now - last >= publishIntervalMs) {
            lastPublishedAt[episodeId] = now
            publisher.publish(episodeId, positionMs, now, checkpoint)
        }
    }

    @Synchronized
    fun clearPosition(episodeId: String) {
        if (episodeId.isBlank()) return
        val now = clock()
        storage.remove(POS_PREFIX + episodeId)
        storage.put(TS_PREFIX + episodeId, now)
        lastPublishedAt[episodeId] = now
        publisher.publish(episodeId, 0L, now, true)
    }

    /** Applies a position received from the other device; returns whether it was newer. */
    @Synchronized
    fun applyRemote(episodeId: String, positionMs: Long, updatedAt: Long): Boolean {
        if (episodeId.isBlank()) return false
        if (updatedAt <= (storage.get(TS_PREFIX + episodeId) ?: 0L)) return false
        if (positionMs <= 0L) storage.remove(POS_PREFIX + episodeId) else storage.put(POS_PREFIX + episodeId, positionMs)
        storage.put(TS_PREFIX + episodeId, updatedAt)
        _remoteUpdates.tryEmit(episodeId)
        return true
    }

    companion object {
        const val PUBLISH_INTERVAL_MS = 15_000L
        private const val POS_PREFIX = "pos_"
        private const val TS_PREFIX = "ts_"
    }
}
