package com.rodrigos01.aipodcasts.sync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.SharedFlow

/**
 * Process-wide resume-position store shared by the phone and watch apps: a [PositionLedger] over
 * SharedPreferences whose changes are mirrored to the other device through the Wearable Data
 * Layer (see [PositionSync]). Use [get] rather than constructing it.
 */
class SyncedPositionStore private constructor(context: Context) {

    private val ledger = PositionLedger(
        storage = PrefsStorage(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)),
        publisher = DataLayerPositionPublisher(context)
    )

    val remoteUpdates: SharedFlow<String> get() = ledger.remoteUpdates

    fun getPositionMs(episodeId: String): Long = ledger.getPositionMs(episodeId)

    /** Saves locally; published to the other device on [checkpoint] or at most every 15 seconds. */
    fun savePositionMs(episodeId: String, positionMs: Long, checkpoint: Boolean = false) =
        ledger.savePositionMs(episodeId, positionMs, checkpoint)

    fun clearPosition(episodeId: String) = ledger.clearPosition(episodeId)

    internal fun applyRemote(episodeId: String, positionMs: Long, updatedAt: Long) =
        ledger.applyRemote(episodeId, positionMs, updatedAt)

    private class PrefsStorage(private val prefs: SharedPreferences) : LongStorage {
        override fun get(key: String): Long? = prefs.getLong(key, MISSING).takeIf { it != MISSING }
        override fun put(key: String, value: Long) {
            prefs.edit().putLong(key, value).apply()
        }
        override fun remove(key: String) {
            prefs.edit().remove(key).apply()
        }
    }

    companion object {
        private const val MISSING = Long.MIN_VALUE

        // Same file the phone app has always used, so existing saved positions carry over.
        private const val PREFS_NAME = "ai_podcasts_playback_positions"

        /** Below this much progress, "resume" isn't offered. */
        const val MIN_RESUME_MS = 3_000L

        fun canResume(positionMs: Long): Boolean = positionMs >= MIN_RESUME_MS

        @Volatile
        private var instance: SyncedPositionStore? = null

        fun get(context: Context): SyncedPositionStore = instance ?: synchronized(this) {
            instance ?: SyncedPositionStore(context.applicationContext ?: context).also { instance = it }
        }
    }
}
