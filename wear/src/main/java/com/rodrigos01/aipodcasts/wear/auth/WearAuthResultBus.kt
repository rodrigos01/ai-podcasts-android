package com.rodrigos01.aipodcasts.wear.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Relays the outcome of a pairing request from [WearAuthResultHandler] (which the system may
 * invoke from a service with no UI around) to whoever is waiting on it, i.e. [WearAuthViewModel].
 */
object WearAuthResultBus {
    private val _results = MutableSharedFlow<PairTokenMessage>(extraBufferCapacity = 1)
    val results: SharedFlow<PairTokenMessage> = _results.asSharedFlow()

    fun emit(message: PairTokenMessage) {
        _results.tryEmit(message)
    }
}
