package com.rodrigos01.aipodcasts.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignUpMode: Boolean = false,
    val email: String = "",
    val password: String = "",
    val errorMessage: String? = null,
    val currentUser: FirebaseUser? = null
)

class AuthViewModel(
    private val authRepo: AuthRepository = AIPodcastsApplication.instance.authRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(currentUser = authRepo.currentUser))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepo.authStateFlow.collect { user ->
                _uiState.value = _uiState.value.copy(currentUser = user)
            }
        }
    }

    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            isSignUpMode = !_uiState.value.isSignUpMode,
            errorMessage = null
        )
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                authRepo.signInWithGoogle(idToken)
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    fun setErrorMessage(message: String?) {
        _uiState.value = _uiState.value.copy(errorMessage = message, isLoading = false)
    }

    fun submitEmailAuth() {
        val email = _uiState.value.email.trim()
        val pass = _uiState.value.password.trim()

        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Email and password cannot be empty")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                if (_uiState.value.isSignUpMode) {
                    authRepo.signUpWithEmail(email, pass)
                } else {
                    authRepo.signInWithEmail(email, pass)
                }
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }
}
