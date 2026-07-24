package com.example.coinswapmobile.data

import com.example.coinswapmobile.model.MakerUiModel
import com.example.coinswapmobile.model.PreparedSwap
import com.example.coinswapmobile.model.SwapReportUiModel
import com.example.coinswapmobile.model.UtxoUiModel
import com.example.coinswapmobile.screens.ReportStatus
import com.example.coinswapmobile.screens.SwapMaker
import com.example.coinswapmobile.screens.SwapReport
import com.example.coinswapmobile.screens.SwapUtxo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MarketRepository(
    private val coinswap: CoinswapRepository,
) {
    suspend fun syncOfferbookAndWait(): Result<Unit> = coinswap.syncOfferbook()

    suspend fun fetchOffers(): Result<List<SwapMaker>> =
        coinswap.listMakers().map { makers -> makers.map { it.toSwapMaker() } }

    private fun MakerUiModel.toSwapMaker() = SwapMaker(
        id = id,
        feeRatePct = feeRatePct,
        minSats = minSats,
        maxSats = maxSats,
        liquiditySats = liquiditySats,
        fidelityBondBtc = fidelityBondBtc,
        onionAddress = onionAddress,
        online = online,
        baseFee = baseFee,
    )
}

class SwapRepository(
    private val coinswap: CoinswapRepository,
) {
    suspend fun listSpendableUtxos(): Result<List<SwapUtxo>> =
        coinswap.getBalance().map { state ->
            state.utxos.map { u ->
                SwapUtxo(
                    txid = u.txid,
                    vout = u.vout,
                    amountSats = u.amountSats,
                    confirmed = (u.confirmations ?: 0) > 0,
                    spendable = u.spendable,
                    spendType = u.spendType,
                    selected = true,
                )
            }
        }

    suspend fun prepareCoinswap(
        amountSats: Long,
        makerCount: Int,
        selectedUtxos: List<SwapUtxo>,
        txCount: Int = 1,
        makerIds: List<String> = emptyList(),
        protocol: String = "Legacy",
        manualSelection: Boolean = false,
    ): Result<PreparedSwap> = withContext(Dispatchers.IO) {
        if (amountSats <= 0) {
            return@withContext Result.failure(IllegalArgumentException("Amount must be positive"))
        }
        if (manualSelection && selectedUtxos.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Select at least one coin for manual coin control"),
            )
        }
        val enriched = selectedUtxos.map { u ->
            UtxoUiModel(
                txid = u.txid,
                vout = u.vout,
                amountSats = u.amountSats,
                confirmations = if (u.confirmed) 1 else 0,
                spendable = u.spendable,
                spendType = u.spendType,
            )
        }
        val unusable = enriched.filter { !it.spendable }
        if (unusable.isNotEmpty()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Selected UTXOs are locked or tied to an active swap contract",
                ),
            )
        }
        coinswap.prepareCoinswap(
            amountSats = amountSats,
            makerCount = makerCount,
            selectedUtxos = enriched,
            txCount = txCount,
            makerIds = makerIds,
            protocol = protocol,
        )
    }

    suspend fun startCoinswap(prepared: PreparedSwap): Result<SwapReportUiModel> =
        coinswap.startCoinswap(prepared)

    suspend fun recoverActiveSwap(swapId: String = ""): Result<String> =
        coinswap.recoverActiveSwap(swapId)

    suspend fun getSwapReports(): Result<List<SwapReport>> =
        coinswap.listSwapReports().map { list -> list.map { it.toScreenReport() } }

    private fun SwapReportUiModel.toScreenReport(): SwapReport {
        val mappedStatus = when (status) {
            SwapReportUiModel.Status.FAILED -> ReportStatus.FAILED
            SwapReportUiModel.Status.RECOVERED -> ReportStatus.FAILED // show under failed until UI has Recovered
            else -> ReportStatus.COMPLETED
        }
        val timeAgo = startTimestamp?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(it * 1000))
        } ?: "-"
        val durationLabel = if (durationSeconds > 0) {
            val mins = TimeUnit.SECONDS.toMinutes(durationSeconds.toLong())
            "${mins}m"
        } else {
            "-"
        }
        return SwapReport(
            id = id,
            timeAgo = timeAgo,
            duration = durationLabel,
            status = mappedStatus,
            hops = hops,
            protocol = protocol,
            amountSats = amountSats,
            makerCount = makerCount,
            totalFeeSats = totalFeeSats,
            outputSats = outputSats,
            errorMessage = errorMessage,
        )
    }
}
