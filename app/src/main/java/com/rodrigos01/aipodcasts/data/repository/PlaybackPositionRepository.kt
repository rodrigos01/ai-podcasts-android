package com.rodrigos01.aipodcasts.data.repository

import android.content.Context
import android.content.SharedPreferences

class PlaybackPositionRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getPositionMs(episodeId: String): Long {
        if (episodeId.isBlank()) return 0L
        return prefs.getLong(KEY_PREFIX_POS + episodeId, 0L)
    }

    fun getPositionSeconds(episodeId: String): Double {
        val ms = getPositionMs(episodeId)
        return ms / 1000.0
    }

    fun savePositionMs(episodeId: String, positionMs: Long) {
        if (episodeId.isBlank()) return
        if (positionMs <= 0L) {
            clearPosition(episodeId)
        } else {
            prefs.edit().putLong(KEY_PREFIX_POS + episodeId, positionMs).apply()
        }
    }

    fun clearPosition(episodeId: String) {
        if (episodeId.isBlank()) return
        prefs.edit().remove(KEY_PREFIX_POS + episodeId).apply()
    }

    companion object {
        private const val PREFS_NAME = "ai_podcasts_playback_positions"
        private const val KEY_PREFIX_POS = "pos_"
    }
}
