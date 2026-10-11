package com.rodrigos01.aipodcasts.wear.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface WearAuthUiState {
    data object SignedOut : WearAuthUiState
    data object Syncing : WearAuthUiState
    data object Authenticated : WearAuthUiState
    data class Error(val message: String) : WearAuthUiState
}

private const val PAIRING_TIMEOUT_MS = 10_000L

class WearAuthViewModel(
    private val syncClient: WearAuthSyncClient,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow<WearAuthUiState>(
        if (auth.currentUser != null) WearAuthUiState.Authenticated else WearAuthUiState.SignedOut
    )
    val uiState: StateFlow<WearAuthUiState> = _uiState.asStateFlow()

    val isAuthenticated: Boolean
        get() = auth.currentUser != null

    /** Asks the phone to relay a sign-in token; see [com.rodrigos01.aipodcasts.wear.ui.screens.AuthGateScreen]. */
    fun requestSync() {
        if (_uiState.value == WearAuthUiState.Syncing) return
        _uiState.value = WearAuthUiState.Syncing
        viewModelScope.launch {
            // Subscribe before sending so a fast reply can't slip past us.
            val result = withTimeoutOrNull(PAIRING_TIMEOUT_MS) {
                val reply = async(start = CoroutineStart.UNDISPATCHED) {
                    WearAuthResultBus.results.first()
                }
                val sent = syncClient.requestPairing()
                if (sent.isFailure) {
                    reply.cancel()
                    return@withTimeoutOrNull PairTokenMessage(
                        error = sent.exceptionOrNull()?.localizedMessage ?: "Could not reach phone"
                    )
                }
                reply.await()
            }
            val error = result?.error
            _uiState.value = when {
                result == null -> WearAuthUiState.Error("Timed out waiting for phone. Make sure it's nearby and signed in.")
                error != null -> WearAuthUiState.Error(error)
                else -> WearAuthUiState.Authenticated
            }
        }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer { WearAuthViewModel(WearAuthSyncClient(context.applicationContext)) }
        }
    }
}
