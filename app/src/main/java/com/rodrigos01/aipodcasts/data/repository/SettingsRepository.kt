package com.rodrigos01.aipodcasts.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rodrigos01.aipodcasts.data.api.ApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_podcasts_settings")

class SettingsRepository(private val context: Context) {

    private val KEY_BASE_URL = stringPreferencesKey("backend_base_url")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

    init {
        // Apply the persisted base URL immediately so requests made before the
        // Settings screen is ever opened use the user's saved choice, not the
        // hardcoded default.
        runBlocking {
            val saved = context.dataStore.data.first()[KEY_BASE_URL]
            if (!saved.isNullOrBlank()) {
                ApiClient.currentBaseUrl = saved
            }
        }
    }

    val baseUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        val saved = preferences[KEY_BASE_URL]
        val url = if (saved.isNullOrBlank()) {
            ApiClient.DEFAULT_BASE_URL
        } else {
            saved
        }
        ApiClient.currentBaseUrl = url
        url
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_THEME_MODE] ?: "system" // "system", "light", "dark"
    }

    suspend fun setBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        ApiClient.currentBaseUrl = normalized
        context.dataStore.edit { preferences ->
            preferences[KEY_BASE_URL] = normalized
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode
        }
    }
}
