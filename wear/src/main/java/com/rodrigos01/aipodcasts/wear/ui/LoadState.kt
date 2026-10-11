package com.rodrigos01.aipodcasts.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Error(val message: String) : LoadState<Nothing>
    data class Success<T>(val value: T) : LoadState<T>
}

/** Collects a Firestore-backed [Flow] as Loading -> Success/Error, restarting when [key] changes. */
@Composable
fun <T> collectLoadState(key: Any?, flow: () -> Flow<T>): State<LoadState<T>> =
    produceState<LoadState<T>>(initialValue = LoadState.Loading, key) {
        value = LoadState.Loading
        flow()
            .catch { value = LoadState.Error(it.localizedMessage ?: "Something went wrong") }
            .collect { value = LoadState.Success(it) }
    }
