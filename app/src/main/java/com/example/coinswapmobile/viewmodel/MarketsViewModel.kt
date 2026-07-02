package com.example.coinswapmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.coinswapmobile.data.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MarketsUiState(
    val makers: List<com.example.coinswapmobile.screens.SwapMaker> = emptyList(),
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
)

class MarketsViewModel(app: Application) : AndroidViewModel(app) {

    private val session = UserSession(app)

    private val _state = MutableStateFlow(MarketsUiState())
    val uiState = _state.asStateFlow()

    /** Maker discovery is not wired in this Electrum-only build. */
    fun syncMarketplace() {
        if (!session.isLoggedIn) return
        _state.update {
            it.copy(
                isSyncing = false,
                makers = emptyList(),
                errorMessage = "Maker marketplace sync is not available in this build yet.",
            )
        }
    }
}
