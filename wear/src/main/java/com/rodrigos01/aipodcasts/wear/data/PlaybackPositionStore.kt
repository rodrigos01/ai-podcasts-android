package com.rodrigos01.aipodcasts.wear.data

import android.content.Context

/**
 * Per-episode resume position, kept on the watch only (it is not synced with the phone's own
 * saved positions).
 */
class PlaybackPositionStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPositionMs(episodeId: String): Long =
        if (episodeId.isBlank()) 0L else prefs.getLong(KEY_PREFIX + episodeId, 0L)

    fun savePositionMs(episodeId: String, positionMs: Long) {
        if (episodeId.isBlank()) return
        if (positionMs <= 0L) clearPosition(episodeId) else prefs.edit().putLong(KEY_PREFIX + episodeId, positionMs).apply()
    }

    fun clearPosition(episodeId: String) {
        if (episodeId.isBlank()) return
        prefs.edit().remove(KEY_PREFIX + episodeId).apply()
    }

    companion object {
        private const val PREFS_NAME = "ai_podcasts_wear_playback_positions"
        private const val KEY_PREFIX = "pos_"

        /** Below this much progress, "Resume" isn't offered (matches the phone app). */
        const val MIN_RESUME_MS = 3_000L

        fun canResume(positionMs: Long): Boolean = positionMs >= MIN_RESUME_MS
    }
}
