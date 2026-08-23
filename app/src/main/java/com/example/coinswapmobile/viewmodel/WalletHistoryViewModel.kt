package com.example.coinswapmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.FfiEnv
import com.example.coinswapmobile.data.TakerHolder
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.model.NativeCapabilities
import com.example.coinswapmobile.model.SwapReportUiModel
import com.example.coinswapmobile.model.TxUiModel
import com.example.coinswapmobile.service.SwapExecutionBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class WalletHistoryUiState(
    val isLoading: Boolean = false,
    val isLoadingTxs: Boolean = false,
    val transactions: List<TxUiModel> = emptyList(),
    val swaps: List<SwapReportUiModel> = emptyList(),
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

    private var loadJob: Job? = null

    init { load() }

    fun load() {
        if (!session.isLoggedIn) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isLoadingTxs = false,
                    transactions = emptyList(),
                    swaps = emptyList(),
                )
            }
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val empty = _state.value.transactions.isEmpty() && _state.value.swaps.isEmpty()
            _state.update {
                it.copy(
                    isLoading = empty,
                    isLoadingTxs = true,
                    errorMessage = null,
                    capabilities = repo.getCapabilities(),
                )
            }

            val swapsResult = repo.listSwapReports()
            val swaps = swapsResult.getOrNull().orEmpty()
                .distinctBy { "${it.id}:${it.status}:${it.startTimestamp}" }
            val swapsError = swapsResult.exceptionOrNull()?.message
            _state.update {
                it.copy(
                    isLoading = false,
                    swaps = swaps,
                    errorMessage = swapsError,
                )
            }

            if (SwapExecutionBus.active.value) {
                _state.update {
                    it.copy(
                        isLoadingTxs = false,
                        errorMessage = listOfNotNull(
                            "On-chain history paused while a swap is running",
                            swapsError,
                        ).joinToString(" / "),
                    )
                }
                return@launch
            }

            if (!TakerHolder.isInitialized) {
                repo.initTaker(session).onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoadingTxs = false,
                            errorMessage = listOfNotNull(e.message, swapsError).joinToString(" / "),
                        )
                    }
                    return@launch
                }
            }

            val txsDeferred = async {
                withTimeoutOrNull(TX_LOAD_TIMEOUT_MS) {
                    repo.listTransactions(count = 40)
                }
            }
            val timed = txsDeferred.await()
            when {
                timed == null -> {
                    _state.update {
                        it.copy(
                            isLoadingTxs = false,
                            errorMessage = listOfNotNull(
                                "Wallet transactions timed out",
                                swapsError,
                            ).joinToString(" / "),
                        )
                    }
                }
                timed.isSuccess -> {
                    _state.update {
                        it.copy(
                            isLoadingTxs = false,
                            transactions = timed.getOrNull().orEmpty(),
                            errorMessage = swapsError,
                        )
                    }
                }
                else -> {
                    _state.update {
                        it.copy(
                            isLoadingTxs = false,
                            errorMessage = listOfNotNull(
                                timed.exceptionOrNull()?.message,
                                swapsError,
                            ).joinToString(" / "),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val TX_LOAD_TIMEOUT_MS = 25_000L
    }
}
