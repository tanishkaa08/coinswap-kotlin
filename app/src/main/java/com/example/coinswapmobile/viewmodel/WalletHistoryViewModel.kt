package com.example.coinswapmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.FfiEnv
import com.example.coinswapmobile.data.TakerHolder
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.model.NativeCapabilities
import com.example.coinswapmobile.model.TxUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WalletHistoryUiState(
    val isLoading: Boolean = false,
    val transactions: List<TxUiModel> = emptyList(),
    val capabilities: NativeCapabilities? = null,
    val errorMessage: String? = null,
)

class WalletHistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val session = UserSession(app)
    private val repo = CoinswapRepository(appDataDir = FfiEnv.takerDataDir(app))

    private val _state = MutableStateFlow(
        WalletHistoryUiState(capabilities = repo.getCapabilities())
    )
    val uiState = _state.asStateFlow()

    init { load() }

    fun load() {
        if (!session.isLoggedIn) {
            _state.update { it.copy(isLoading = false, transactions = emptyList()) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, capabilities = repo.getCapabilities()) }
            if (!TakerHolder.isInitialized) {
                repo.initTaker(session).onFailure { e ->
                    _state.update { it.copy(isLoading = false, errorMessage = e.message) }
                    return@launch
                }
            }
            repo.listTransactions()
                .onSuccess { txs ->
                    _state.update { it.copy(isLoading = false, transactions = txs) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, transactions = emptyList(), errorMessage = e.message) }
                }
        }
    }
}
