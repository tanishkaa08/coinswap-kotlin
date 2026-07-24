package com.example.coinswapmobile.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI observes swap job owned by [SwapForegroundService]. */
object SwapExecutionBus {

    data class Event(
        val swapId: String?,
        val phase: String?,
        val isRunning: Boolean,
        val completed: Boolean = false,
        val failed: Boolean = false,
        val errorMessage: String? = null,
    )

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun emit(event: Event) {
        _active.value = event.isRunning
        _events.tryEmit(event)
    }
}
