package com.example.coinswapmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.FfiEnv
import com.example.coinswapmobile.data.SwapRepository
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.model.NativeCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SwapReportsUiState(
    val reports: List<com.example.coinswapmobile.screens.SwapReport> = emptyList(),
    val isLoading: Boolean = false,
    val capabilities: NativeCapabilities? = null,
    val errorMessage: String? = null,
)

class SwapReportsViewModel(app: Application) : AndroidViewModel(app) {

    private val session = UserSession(app)
    private val coinswapRepo = CoinswapRepository(appDataDir = FfiEnv.takerDataDir(app))
    private val swapRepo = SwapRepository(coinswapRepo)

    private val _state = MutableStateFlow(
        SwapReportsUiState(capabilities = coinswapRepo.getCapabilities())
    )
    val uiState = _state.asStateFlow()

    init { load() }

    fun load() {
        if (!session.isLoggedIn) {
            _state.update { it.copy(isLoading = false, reports = emptyList()) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            // Reports are on disk — do not require a live Taker/RPC.
            swapRepo.getSwapReports()
                .onSuccess { reports ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            reports = reports,
                            errorMessage = if (reports.isEmpty()) {
                                "No swap reports yet."
                            } else {
                                null
                            },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, reports = emptyList(), errorMessage = e.message) }
                }
        }
    }
}
