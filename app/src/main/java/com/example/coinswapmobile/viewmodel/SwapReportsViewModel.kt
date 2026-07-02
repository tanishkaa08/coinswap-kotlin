package com.example.coinswapmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.coinswapmobile.data.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SwapReportsUiState(
    val reports: List<com.example.coinswapmobile.screens.SwapReport> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class SwapReportsViewModel(app: Application) : AndroidViewModel(app) {

    private val session = UserSession(app)

    private val _state = MutableStateFlow(SwapReportsUiState())
    val uiState = _state.asStateFlow()

    init { load() }

    fun load() {
        if (!session.isLoggedIn) {
            _state.update { it.copy(isLoading = false, reports = emptyList()) }
            return
        }
        // Swap history is not exposed by the Electrum wallet JNI layer yet.
        _state.update { it.copy(isLoading = false, reports = emptyList(), errorMessage = null) }
    }
}
