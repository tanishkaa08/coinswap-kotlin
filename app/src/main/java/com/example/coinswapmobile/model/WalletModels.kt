package com.example.coinswapmobile.model

/** Snapshot of the Electrum-backed wallet, parsed from the native JSON. */
data class WalletState(
    val balanceSats: Long,
    val confirmedSats: Long,
    val unconfirmedSats: Long,
    val backend: String,
    val lastSyncUnix: Long?,
    val blockHeight: Long?,
    val utxos: List<UtxoUiModel>,
)

/** One unspent output owned by the wallet. */
data class UtxoUiModel(
    val txid: String,
    val vout: Int,
    val amountSats: Long,
    val confirmations: Int?,
)
