package com.example.coinswapmobile.model

/** Snapshot of wallet balances + UTXOs from UniFFI Taker. */
data class WalletState(
    val balanceSats: Long,
    val confirmedSats: Long,
    val unconfirmedSats: Long,
    val backend: String = "ELECTRUM",
    val network: String? = null,
    val lastSyncUnix: Long? = null,
    val blockHeight: Long? = null,
    val utxos: List<UtxoUiModel>,
    val regularSats: Long = 0,
    val swapSats: Long = 0,
    val contractSats: Long = 0,
    val fidelitySats: Long = 0,
)

data class UtxoUiModel(
    val txid: String,
    val vout: Int,
    val amountSats: Long,
    val confirmations: Int?,
    val spendable: Boolean = true,
    val spendType: String? = null,
    val address: String? = null,
)

/** Capability flags describing what the linked FFI build exposes. */
data class NativeCapabilities(
    val nativeLibraryLoaded: Boolean,
    val walletInit: Boolean = false,
    val walletSync: Boolean = false,
    val receive: Boolean = false,
    val listUtxos: Boolean = false,
    val send: Boolean = false,
    val history: Boolean = false,
    val makerDiscovery: Boolean = false,
    val coinswap: Boolean = false,
    val reports: Boolean = false,
    val recovery: Boolean = false,
    val missingApis: List<String> = emptyList(),
    val backend: String = "ELECTRUM",
)

data class TxUiModel(
    val txid: String,
    val amountSats: Long,
    val confirmed: Boolean,
    val confirmations: Int,
    val timestamp: Long?,
    val direction: String,
    val address: String? = null,
    val category: String? = null,
)

data class SendResult(
    val txid: String,
    val amountSats: Long,
    val feeSats: Long = 0,
)

data class NativeError(
    val message: String,
    val kind: String,
)

data class MakerUiModel(
    val id: String,
    val feeRatePct: Double,
    val minSats: Long,
    val maxSats: Long,
    val liquiditySats: Long,
    val fidelityBondBtc: Double,
    val onionAddress: String,
    val online: Boolean,
    val baseFee: Long = 0,
    val protocol: String? = null,
    val state: String? = null,
)

data class PreparedSwap(
    val swapId: String,
    val sendAmountSats: Long,
    val totalEstimatedFeeSats: Long = 0,
    val estimatedReceiveSats: Long = 0,
    val protocol: String,
    val rawJson: String = "",
)

data class SwapEstimate(
    val sendAmountSats: Long,
    val totalEstimatedFeeSats: Long,
    val estimatedReceiveSats: Long,
    val makerCount: Int,
)

data class SwapStatusUi(
    val swapId: String,
    val phase: String,
    val failedAtPhase: String? = null,
)

data class RecoverableSwap(
    val swapId: String,
    val phase: String,
    val recoverable: Boolean,
)

data class SwapReportUiModel(
    val id: String,
    val status: Status,
    val startTimestamp: Long?,
    val durationSeconds: Double,
    val amountSats: Long,
    val outputSats: Long,
    val totalFeeSats: Long,
    val makerCount: Int,
    val hops: Int,
    val protocol: String,
    val errorMessage: String? = null,
) {
    enum class Status { COMPLETED, FAILED, RECOVERED }
}
