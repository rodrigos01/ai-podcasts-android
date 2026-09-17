package com.rodrigos01.aipodcasts.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.repository.AuthRepository
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
import com.rodrigos01.aipodcasts.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = "",
    val themeMode: String = "system",
    val currentUser: FirebaseUser? = null,
    val isTestingConnection: Boolean = false,
    val connectionTestResult: Boolean? = null,
    val connectionTestError: String? = null
)

class SettingsViewModel(
    private val settingsRepo: SettingsRepository = AIPodcastsApplication.instance.settingsRepository,
    private val authRepo: AuthRepository = AIPodcastsApplication.instance.authRepository,
    private val podcastRepo: PodcastRepository = AIPodcastsApplication.instance.podcastRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(currentUser = authRepo.currentUser))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.baseUrlFlow.collect { url ->
                _uiState.value = _uiState.value.copy(baseUrl = url)
            }
        }
        viewModelScope.launch {
            settingsRepo.themeModeFlow.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }
        viewModelScope.launch {
            authRepo.authStateFlow.collect { user ->
                _uiState.value = _uiState.value.copy(currentUser = user)
            }
        }
    }

    fun onBaseUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(baseUrl = url, connectionTestResult = null, connectionTestError = null)
    }

    fun saveBaseUrl(url: String) {
        viewModelScope.launch {
            settingsRepo.setBaseUrl(url)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsRepo.setThemeMode(mode)
        }
    }

    fun testBackendConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingConnection = true, connectionTestResult = null, connectionTestError = null)
            try {
                settingsRepo.setBaseUrl(_uiState.value.baseUrl)
                val ok = podcastRepo.checkHealth()
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionTestResult = ok
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionTestResult = false,
                    connectionTestError = e.localizedMessage ?: e.message
                )
            }
        }
    }

    fun signOut() {
        authRepo.signOut()
    }
}
