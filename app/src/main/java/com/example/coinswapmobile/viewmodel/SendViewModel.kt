package com.example.coinswapmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.FfiEnv
import com.example.coinswapmobile.data.TakerHolder
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.model.NativeCapabilities
import com.example.coinswapmobile.model.SendResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SendUiState(
    val isSending: Boolean = false,
    val capabilities: NativeCapabilities? = null,
    val lastResult: SendResult? = null,
    val error: String? = null,
)

class SendViewModel(app: Application) : AndroidViewModel(app) {

    private val session = UserSession(app)
    private val repo = CoinswapRepository(appDataDir = FfiEnv.takerDataDir(app))

    private val _state = MutableStateFlow(
        SendUiState(capabilities = repo.getCapabilities())
    )
    val uiState = _state.asStateFlow()

    fun send(address: String, amountSats: Long, feeRateSatPerVb: Long) {
        if (!session.isLoggedIn) return
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, error = null, lastResult = null) }
            if (!TakerHolder.isInitialized) {
                repo.initTaker(session).onFailure { e ->
                    _state.update { it.copy(isSending = false, error = e.message) }
                    return@launch
                }
            }
            repo.sendToAddress(address.trim(), amountSats, feeRateSatPerVb)
                .onSuccess { result ->
                    _state.update { it.copy(isSending = false, lastResult = result) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSending = false, error = e.message) }
                }
        }
    }

    fun clearResult() {
        _state.update { it.copy(lastResult = null, error = null) }
    }
}
