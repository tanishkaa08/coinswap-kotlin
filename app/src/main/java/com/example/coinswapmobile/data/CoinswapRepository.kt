package com.example.coinswapmobile.data

import com.example.coinswapmobile.model.MakerUiModel
import com.example.coinswapmobile.model.NativeCapabilities
import com.example.coinswapmobile.model.NativeError
import com.example.coinswapmobile.model.PreparedSwap
import com.example.coinswapmobile.model.RecoverableSwap
import com.example.coinswapmobile.model.SendResult
import com.example.coinswapmobile.model.SwapEstimate
import com.example.coinswapmobile.model.SwapReportUiModel
import com.example.coinswapmobile.model.SwapStatusUi
import com.example.coinswapmobile.model.TxUiModel
import com.example.coinswapmobile.model.UtxoUiModel
import com.example.coinswapmobile.model.WalletState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.coinswap.AddressType
import org.coinswap.MakerOfferCandidate
import org.coinswap.OutPoint
import org.coinswap.SwapParams
import org.coinswap.SwapReport
import org.coinswap.Taker
import org.coinswap.Txid
import org.coinswap.createDefaultRpcConfig
import org.coinswap.fetchMempoolFees
import org.coinswap.restoreWalletGuiApp
import org.coinswap.setupLogging
import org.json.JSONObject
import java.io.File

/**
 * Thin adapter over generated UniFFI [Taker].
 * Maps UI models to FFI; wallet and swap logic stay in Rust.
 */
class CoinswapRepository(
    private val appDataDir: String,
) {

    val libraryLoadStatus: String
        get() = if (TakerHolder.isInitialized) {
            "Taker ready (UniFFI / libcoinswap_ffi)"
        } else {
            "Taker not initialized"
        }

    val deviceAbis: String
        get() = android.os.Build.SUPPORTED_ABIS?.joinToString(", ")
            ?: android.os.Build.CPU_ABI
            ?: "unknown"

    fun getCapabilities(): NativeCapabilities = NativeCapabilities(
        nativeLibraryLoaded = true,
        walletInit = true,
        walletSync = true,
        receive = true,
        listUtxos = true,
        send = true,
        history = true,
        makerDiscovery = true,
        coinswap = true,
        reports = true,
        recovery = true,
        backend = "BITCOIN_CORE_RPC",
        missingApis = emptyList(),
    )

    suspend fun initTaker(session: UserSession): Result<WalletState> = callFfi("Taker.init") {
        FfiEnv.ensureHome(appDataDir)
        val cfg = session.config
        setupLogging(appDataDir)
        val taker = Taker.init(
            dataDir = appDataDir,
            walletFileName = cfg.walletName,
            rpcConfig = session.toRpcConfig(),
            controlPort = cfg.torControlPort.toUShort(),
            torAuthPassword = cfg.torAuthPassword.ifBlank { null },
            zmqAddr = cfg.zmqAddr,
            password = cfg.walletPassword.ifBlank { null },
        )
        TakerHolder.set(taker)
        runCatching { taker.lockUnspendableUtxos() }
        taker.syncAndSave()
        snapshotWallet(taker)
    }

    suspend fun restoreWallet(
        session: UserSession,
        backupPath: String,
    ): Result<Unit> = callFfi("restoreWalletGuiApp") {
        restoreWalletGuiApp(
            dataDir = appDataDir,
            walletFileName = session.walletName,
            rpcConfig = session.toRpcConfig(),
            backupFilePath = backupPath,
            password = session.config.walletPassword.ifBlank { null },
        )
    }

    suspend fun syncWallet(): Result<WalletState> = callFfi("syncAndSave") {
        val taker = TakerHolder.require()
        taker.syncAndSave()
        snapshotWallet(taker)
    }

    suspend fun getBalance(): Result<WalletState> = callFfi("getBalances") {
        snapshotWallet(TakerHolder.require())
    }

    suspend fun listUtxos(): Result<List<UtxoUiModel>> =
        getBalance().map { it.utxos }

    suspend fun getNewAddress(addrType: String = "P2WPKH"): Result<String> =
        callFfi("getNextExternalAddress") {
            TakerHolder.require()
                .getNextExternalAddress(AddressType(addrType = addrType))
                .address
        }

    suspend fun sendToAddress(
        address: String,
        amountSats: Long,
        feeRateSatPerVb: Long,
        outpoints: List<UtxoUiModel> = emptyList(),
    ): Result<SendResult> = callFfi("sendToAddress") {
        val selected = outpoints.takeIf { it.isNotEmpty() }?.map { u ->
            OutPoint(txid = Txid(value = u.txid), vout = u.vout.toUInt())
        }
        val txid = TakerHolder.require().sendToAddress(
            address = address,
            amount = amountSats,
            feeRate = feeRateSatPerVb.toDouble(),
            manuallySelectedOutpoints = selected,
        )
        SendResult(txid = txid.value, amountSats = amountSats)
    }

    suspend fun listTransactions(count: Int = 50, skip: Int = 0): Result<List<TxUiModel>> =
        callFfi("getTransactions") {
            TakerHolder.require()
                .getTransactions(count = count.toUInt(), skip = skip.toUInt())
                .map { tx ->
                    val amount = tx.detail.amount.sats
                    TxUiModel(
                        txid = tx.info.txid.value,
                        amountSats = amount,
                        confirmed = tx.info.confirmations > 0,
                        confirmations = tx.info.confirmations,
                        timestamp = tx.info.time.takeIf { it > 0 },
                        direction = when {
                            amount > 0 -> "incoming"
                            amount < 0 -> "outgoing"
                            else -> tx.detail.category.lowercase().ifBlank { "unknown" }
                        },
                        address = tx.detail.address?.address,
                        category = tx.detail.category,
                    )
                }
        }

    suspend fun syncOfferbook(): Result<Unit> = callFfi("syncOfferbookAndWait") {
        clearOfferbookCache()
        TakerHolder.require().syncOfferbookAndWait()
    }

    /** Drop cached maker states so a fresh Tor poll runs (fixes stuck offline makers). */
    fun clearOfferbookCache() {
        File(appDataDir, "offerbook.json").delete()
    }

    suspend fun listMakers(): Result<List<MakerUiModel>> = callFfi("fetchOffers") {
        TakerHolder.require().fetchOffers().makers.map { it.toMakerUiModel() }
    }

    /** Fee estimate from live offerbook (no separate UniFFI estimate API). */
    suspend fun estimateSwap(
        amountSats: Long,
        selectedUtxos: List<UtxoUiModel>,
        makerIds: List<String>,
    ): Result<SwapEstimate> {
        @Suppress("UNUSED_PARAMETER")
        selectedUtxos
        return listMakers().map { makers ->
            val selected = if (makerIds.isEmpty()) {
                makers.filter { it.online }.take(1)
            } else {
                makers.filter { it.id in makerIds || it.onionAddress in makerIds }
            }
            val fee = selected.sumOf { m ->
                m.baseFee + ((amountSats * m.feeRatePct) / 100.0).toLong()
            }
            SwapEstimate(
                sendAmountSats = amountSats,
                totalEstimatedFeeSats = fee,
                estimatedReceiveSats = (amountSats - fee).coerceAtLeast(0),
                makerCount = selected.size.coerceAtLeast(1),
            )
        }
    }

    suspend fun prepareCoinswap(
        amountSats: Long,
        makerCount: Int,
        feeRateSatPerVb: Long,
        selectedUtxos: List<UtxoUiModel>,
        makerIds: List<String>,
        txCount: Int = 1,
        protocol: String = "Legacy",
    ): Result<PreparedSwap> = callFfi("prepareCoinswap") {
        @Suppress("UNUSED_VARIABLE")
        val ignoredFeeHint = feeRateSatPerVb
        val outpoints = selectedUtxos.takeIf { it.isNotEmpty() }?.map { u ->
            OutPoint(txid = Txid(value = u.txid), vout = u.vout.toUInt())
        }
        val params = SwapParams(
            protocol = protocol,
            sendAmount = amountSats.toULong(),
            makerCount = makerCount.toUInt(),
            txCount = txCount.coerceAtLeast(1).toUInt(),
            requiredConfirms = null,
            manuallySelectedOutpoints = outpoints,
            preferredMakers = makerIds.takeIf { it.isNotEmpty() },
        )
        val swapId = TakerHolder.require().prepareCoinswap(params)
        PreparedSwap(
            swapId = swapId,
            sendAmountSats = amountSats,
            protocol = protocol,
        )
    }

    suspend fun startCoinswap(prepared: PreparedSwap): Result<SwapReportUiModel> =
        callFfi("startCoinswap") {
            val report = TakerHolder.require().startCoinswap(prepared.swapId)
            val ui = report.toUiModel(protocol = prepared.protocol)
            persistSwapReport(report, protocol = prepared.protocol)
            ui
        }

    suspend fun getSwapStatus(swapId: String): Result<SwapStatusUi> = withContext(Dispatchers.IO) {
        val fromDisk = loadSwapReportsFromDisk().firstOrNull { it.id == swapId }
        Result.success(
            SwapStatusUi(
                swapId = swapId,
                phase = fromDisk?.status?.name ?: if (TakerHolder.isInitialized) "active" else "unknown",
            )
        )
    }

    /** Reads reports written by Rust under `swap_reports/` (same layout as desktop). */
    suspend fun listSwapReports(): Result<List<SwapReportUiModel>> = withContext(Dispatchers.IO) {
        Result.success(loadSwapReportsFromDisk())
    }

    /**
     * UniFFI has no list-recoverable API. Check on-disk swap state, then
     * [recoverActiveSwap].
     */
    suspend fun detectRecoverableSwaps(): Result<List<RecoverableSwap>> = withContext(Dispatchers.IO) {
        val found = mutableListOf<RecoverableSwap>()
        listOf("swap_state.json", "swap.json").forEach { name ->
            val f = File(appDataDir, name)
            if (!f.isFile) return@forEach
            runCatching {
                val json = JSONObject(f.readText())
                val status = json.optString("status", json.optString("phase", "unknown"))
                val id = json.optString("swap_id", json.optString("swapId", name))
                val failed = status.contains("fail", true) ||
                    status.contains("recover", true) ||
                    status.contains("incomplete", true) ||
                    status.contains("pending", true)
                if (failed || id.isNotBlank()) {
                    found += RecoverableSwap(
                        swapId = id.ifBlank { "active" },
                        phase = status.ifBlank { "unknown" },
                        recoverable = true,
                    )
                }
            }
        }
        // Always allow one recovery attempt entry when taker is up (desktop "Force Recovery")
        if (found.isEmpty() && TakerHolder.isInitialized) {
            found += RecoverableSwap(
                swapId = "active",
                phase = "recover_active_swap",
                recoverable = true,
            )
        }
        Result.success(found.distinctBy { it.swapId })
    }

    suspend fun recoverActiveSwap(swapId: String = ""): Result<String> =
        callFfi("recoverActiveSwap") {
            TakerHolder.require().recoverActiveSwap()
            // Clear local failure markers after successful recovery
            listOf("swap.json", "swap_state.json").forEach { name ->
                runCatching { File(appDataDir, name).delete() }
            }
            swapId.ifBlank { "recovery complete" }
        }

    suspend fun backupWallet(destinationPath: String, password: String? = null): Result<Unit> =
        callFfi("backup") {
            TakerHolder.require().backup(destinationPath, password)
        }

    suspend fun mempoolFees(): Result<Triple<Double, Double, Double>> = callFfi("fetchMempoolFees") {
        val fees = fetchMempoolFees()
        Triple(fees.economy, fees.standard, fees.fastest)
    }

    suspend fun defaultRpcHint(): Result<String> = callFfi("createDefaultRpcConfig") {
        val d = createDefaultRpcConfig()
        "${d.url} (${d.walletName})"
    }

    fun clearTaker() {
        TakerHolder.clear()
    }

    private fun snapshotWallet(taker: Taker): WalletState {
        val balances = taker.getBalances()
        val utxos = taker.listAllUtxoSpendInfo().map { info ->
            val e = info.listUnspentResultEntry
            UtxoUiModel(
                txid = e.txid.value,
                vout = e.vout.toInt(),
                amountSats = e.amount.sats,
                confirmations = e.confirmations.toInt(),
                spendable = e.spendable,
                spendType = info.utxoSpendInfo.spendType,
                address = e.address,
            )
        }
        return WalletState(
            balanceSats = balances.spendable,
            confirmedSats = balances.spendable,
            unconfirmedSats = 0,
            backend = "BITCOIN_CORE_RPC",
            utxos = utxos,
            regularSats = balances.regular,
            swapSats = balances.swap,
            contractSats = balances.contract,
            fidelitySats = balances.fidelity,
        )
    }

    private fun MakerOfferCandidate.toMakerUiModel(): MakerUiModel {
        val addr = address.address
        val o = offer
        val stateType = state.stateType
        val bondBtc = o?.fidelity?.bond?.amount?.sats?.let { it / 100_000_000.0 } ?: 0.0
        return MakerUiModel(
            id = addr,
            feeRatePct = o?.amountRelativeFeePct ?: 0.0,
            minSats = o?.minSize ?: 0,
            maxSats = o?.maxSize ?: 0,
            liquiditySats = o?.maxSize ?: 0,
            fidelityBondBtc = bondBtc,
            onionAddress = addr,
            online = stateType.equals("Good", ignoreCase = true),
            baseFee = o?.baseFee ?: 0,
            protocol = protocol?.protocolType,
            state = stateType,
        )
    }

    private fun SwapReport.toUiModel(protocol: String): SwapReportUiModel {
        val report = this
        val mapped = when {
            report.status.contains("fail", ignoreCase = true) -> SwapReportUiModel.Status.FAILED
            report.status.contains("recover", ignoreCase = true) -> SwapReportUiModel.Status.RECOVERED
            else -> SwapReportUiModel.Status.COMPLETED
        }
        val makers = (report.makersCount?.toInt() ?: report.makerFeeInfo.size).coerceAtLeast(1)
        return SwapReportUiModel(
            id = report.swapId,
            status = mapped,
            startTimestamp = report.startTimestamp.takeIf { it > 0 },
            durationSeconds = report.swapDurationSeconds,
            amountSats = report.outgoingAmount,
            outputSats = report.incomingAmount,
            totalFeeSats = kotlin.math.abs(report.feePaid),
            makerCount = makers,
            hops = makers,
            protocol = protocol.uppercase(),
            errorMessage = report.errorMessage,
        )
    }

    private fun persistSwapReport(report: SwapReport, protocol: String) {
        runCatching {
            val dir = File(appDataDir, "swap_reports").apply { mkdirs() }
            val file = File(dir, "${report.swapId}.json")
            val makers = (report.makersCount?.toInt() ?: report.makerFeeInfo.size).coerceAtLeast(1)
            val json = JSONObject()
                .put("swap_id", report.swapId)
                .put("swapId", report.swapId)
                .put("status", report.status)
                .put("start_timestamp", report.startTimestamp)
                .put("startTimestamp", report.startTimestamp)
                .put("swap_duration_seconds", report.swapDurationSeconds)
                .put("durationSeconds", report.swapDurationSeconds)
                .put("outgoing_amount", report.outgoingAmount)
                .put("amountSats", report.outgoingAmount)
                .put("incoming_amount", report.incomingAmount)
                .put("outputSats", report.incomingAmount)
                .put("fee_paid", report.feePaid)
                .put("totalFeeSats", kotlin.math.abs(report.feePaid))
                .put("maker_count", makers)
                .put("makerCount", makers)
                .put("hops", makers)
                .put("error_message", report.errorMessage)
                .put("errorMessage", report.errorMessage)
                .put("protocol", protocol.uppercase())
            file.writeText(json.toString())
        }
    }

    private fun loadSwapReportsFromDisk(): List<SwapReportUiModel> {
        val dir = File(appDataDir, "swap_reports")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { file ->
                runCatching {
                    val json = JSONObject(file.readText())
                    val statusRaw = json.optString("status", "COMPLETED")
                    val status = when {
                        statusRaw.contains("fail", true) -> SwapReportUiModel.Status.FAILED
                        statusRaw.contains("recover", true) -> SwapReportUiModel.Status.RECOVERED
                        else -> SwapReportUiModel.Status.COMPLETED
                    }
                    SwapReportUiModel(
                        id = json.optString("swap_id", json.optString("swapId", file.nameWithoutExtension)),
                        status = status,
                        startTimestamp = json.optLong("start_timestamp", json.optLong("startTimestamp")).takeIf { it > 0 },
                        durationSeconds = json.optDouble("swap_duration_seconds", json.optDouble("durationSeconds", 0.0)),
                        amountSats = json.optLong("outgoing_amount", json.optLong("amountSats")),
                        outputSats = json.optLong("incoming_amount", json.optLong("outputSats")),
                        totalFeeSats = kotlin.math.abs(
                            json.optLong("totalFeeSats", json.optLong("fee_paid"))
                        ),
                        makerCount = json.optInt("maker_count", json.optInt("makerCount", 1)),
                        hops = json.optInt("hops", json.optInt("makerCount", 1)),
                        protocol = json.optString("protocol", "LEGACY"),
                        errorMessage = json.optString("error_message", json.optString("errorMessage"))
                            .takeIf { it.isNotBlank() },
                    )
                }.getOrNull()
            }
            ?.sortedByDescending { it.startTimestamp ?: 0L }
            .orEmpty()
    }

    private suspend fun <T> callFfi(operation: String, block: () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching { block() }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { e ->
                    val detail = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
                    Result.failure(IllegalStateException("$operation failed: $detail", e))
                },
            )
        }
}

class NativeCallException(val nativeError: NativeError) : Exception(nativeError.message)
