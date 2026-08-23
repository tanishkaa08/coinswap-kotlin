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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.coinswap.AddressType
import org.coinswap.MakerOfferCandidate
import org.coinswap.OutPoint
import org.coinswap.RpcConfig
import org.coinswap.SwapParams
import org.coinswap.SwapReport
import org.coinswap.Taker
import org.coinswap.Txid
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
        backend = "ELECTRUM",
        missingApis = emptyList(),
    )

    suspend fun initTaker(
        session: UserSession,
        forceReconnect: Boolean = false,
        config: TakerAppConfig = session.config,
        syncAfterInit: Boolean = true,
        electrumSocks5: String? = config.electrumSocks5,
        electrumTimeoutSecs: UByte = TakerAppConfig.DEFAULT_ELECTRUM_TIMEOUT_SECS,
    ): Result<WalletState> =
        TakerHolder.initMutex.withLock {
            if (TakerHolder.isInitialized && !forceReconnect) {
                return@withLock callFfi("getBalances") {
                    snapshotWallet(TakerHolder.require())
                }
            }
            callFfi("Taker.init") {
                FfiEnv.ensureHome(appDataDir)
                val cfg = config
                require(cfg.torControlPort in 1..65535) {
                    "Tor control port must be 1..65535 (got ${cfg.torControlPort})"
                }
                // Clearnet Electrum → null SOCKS. Onion Electrum → Orbot.
                // Never force clearnet TLS through Tor (WouldBlock / unreachable).
                val socks = electrumSocks5 ?: cfg.electrumSocks5
                val liveTorPassword = TorManager.probeControlPassword()
                    ?: cfg.torAuthPassword
                TakerHolder.clear()
                setupLogging(dataDir = appDataDir, level = "info", toStdout = false)
                val taker = Taker.init(
                    dataDir = appDataDir,
                    walletFileName = cfg.walletName,
                    rpcConfig = null,
                    controlPort = cfg.torControlPort.toUShort(),
                    torAuthPassword = liveTorPassword.ifBlank { null },
                    zmqAddr = TakerAppConfig.DUMMY_ZMQ_ADDR,
                    password = cfg.walletPassword.ifBlank { null },
                    nostrRelays = null,
                    backendConfig = cfg.toBackendConfig(
                        socks5 = socks,
                        timeoutSecs = electrumTimeoutSecs.coerceAtLeast(
                            TakerAppConfig.DEFAULT_ELECTRUM_TIMEOUT_SECS,
                        ),
                        maxRetries = TakerAppConfig.DEFAULT_ELECTRUM_MAX_RETRIES,
                    ),
                )
                try {
                    if (syncAfterInit) {
                        try {
                            taker.lockUnspendableUtxos()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        }
                        taker.syncAndSave()
                    }
                    val state = try {
                        snapshotWallet(taker)
                    } catch (_: Exception) {
                        WalletState(
                            balanceSats = 0,
                            confirmedSats = 0,
                            unconfirmedSats = 0,
                            backend = "ELECTRUM",
                            utxos = emptyList(),
                        )
                    }
                    TakerHolder.set(taker)
                    state
                } catch (e: CancellationException) {
                    TakerHolder.closeQuietly(taker)
                    throw e
                } catch (e: Exception) {
                    TakerHolder.closeQuietly(taker)
                    throw e
                } catch (e: Error) {
                    TakerHolder.closeQuietly(taker)
                    throw e
                }
            }
        }

    suspend fun restoreWallet(
        session: UserSession,
        backupPath: String,
    ): Result<Unit> = callFfi("restoreWalletGuiApp") {
        restoreWalletGuiApp(
            dataDir = appDataDir,
            walletFileName = session.walletName,
            // Electrum FFI restore still takes RpcConfig; wallet file restore does not use Core.
            rpcConfig = RpcConfig(
                url = "127.0.0.1:18442",
                username = "user",
                password = "password",
                walletName = session.walletName,
            ),
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
                .addr
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
        val feeSats = (feeRateSatPerVb * 225L).coerceAtLeast(0)
        SendResult(txid = txid.value, amountSats = amountSats, feeSats = feeSats)
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
                        address = tx.detail.address?.addr,
                        category = tx.detail.category,
                    )
                }
        }

    /**
     * Poll makers over Tor. Does **not** delete offerbook.json — wiping the cache
     * forced a cold re-discovery every tap and made Marketplace look broken.
     * Pass [forceClearCache] only when makers are stuck stale.
     *
     * Uses [callFfiLong] so History/balance reads are not blocked for the whole poll.
     */
    suspend fun syncOfferbook(forceClearCache: Boolean = false): Result<Unit> =
        callFfiLong("syncOfferbookAndWait") {
            if (forceClearCache) clearOfferbookCache()
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
        return listMakers().mapCatching { makers ->
            fun eligible(m: MakerUiModel): Boolean =
                m.online &&
                    amountSats >= m.minSats &&
                    (m.maxSats <= 0 || amountSats <= m.maxSats) &&
                    (m.liquiditySats <= 0 || amountSats <= m.liquiditySats)

            val selected = if (makerIds.isEmpty()) {
                makers.filter(::eligible).take(1)
            } else {
                makers.filter { m ->
                    (m.id in makerIds || m.onionAddress in makerIds) && eligible(m)
                }
            }
            if (selected.isEmpty()) {
                error("No eligible online makers for this amount")
            }
            val fee = selected.sumOf { m ->
                m.baseFee + ((amountSats * m.feeRatePct) / 100.0).toLong()
            }
            SwapEstimate(
                sendAmountSats = amountSats,
                totalEstimatedFeeSats = fee,
                estimatedReceiveSats = (amountSats - fee).coerceAtLeast(0),
                makerCount = selected.size,
            )
        }
    }

    suspend fun prepareCoinswap(
        amountSats: Long,
        makerCount: Int,
        selectedUtxos: List<UtxoUiModel>,
        makerIds: List<String>,
        txCount: Int = 1,
        protocol: String = "Legacy",
    ): Result<PreparedSwap> = callFfiLong("prepareCoinswap") {
        val outpoints = selectedUtxos.takeIf { it.isNotEmpty() }?.map { u ->
            OutPoint(txid = Txid(value = u.txid), vout = u.vout.toUInt())
        }
        val params = SwapParams(
            protocol = protocol,
            sendAmount = amountSats.toULong(),
            makerCount = makerCount.toUInt(),
            txCount = txCount.coerceAtLeast(1).toUInt(),
            requiredConfirms = 1u, // maker also requires ≥1; send PoF after confirm so Tor isn't held open for the wait
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
        callFfiLong("startCoinswap") {
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

    /**
     * Swap history: local swap_reports JSON files plus Rust wallet swap_report.json
     * (includes Failed swaps that UniFFI surfaces as errors).
     */
    suspend fun listSwapReports(): Result<List<SwapReportUiModel>> = withContext(Dispatchers.IO) {
        Result.success(loadAllSwapReports())
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
                    status.contains("incomplete", true)
                // Only surface genuine recovery cases — not active "pending" swaps.
                if (failed) {
                    found += RecoverableSwap(
                        swapId = id.ifBlank { "active" },
                        phase = status.ifBlank { "unknown" },
                        recoverable = true,
                    )
                }
            }
        }
        // Only surface real failure markers — do not invent a recovery entry.
        Result.success(found.distinctBy { it.swapId })
    }

    suspend fun recoverActiveSwap(swapId: String = ""): Result<String> =
        callFfiLong("recoverActiveSwap") {
            TakerHolder.require().recoverActiveSwap()
            // Fresh failure: force Recovery once. User may then dismiss to use spendable.
            File(appDataDir, "recovery_use_spendable").delete()
            runCatching {
                File(appDataDir, "recovery_in_progress").writeText(
                    "${System.currentTimeMillis()}\n$swapId",
                )
            }
            swapId.ifBlank { "recovery loop started" }
        }

    fun clearRecoveryMarker() {
        File(appDataDir, "recovery_in_progress").delete()
    }

    suspend fun backupWallet(destinationPath: String, password: String? = null): Result<Unit> =
        callFfi("backup") {
            TakerHolder.require().backup(destinationPath, password)
        }

    suspend fun mempoolFees(): Result<Triple<Double, Double, Double>> = callFfi("fetchMempoolFees") {
        val fees = fetchMempoolFees()
        Triple(fees.economy, fees.standard, fees.fastest)
    }

    fun defaultElectrumHint(): String = TakerAppConfig.SIGNET_ELECTRUM_URL

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
            backend = "ELECTRUM",
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
            if (!isSafeSwapId(report.swapId)) return@runCatching
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

    private fun loadAllSwapReports(): List<SwapReportUiModel> {
        // Keep Failed + Recovered for the same swap_id as separate rows.
        val byKey = LinkedHashMap<String, SwapReportUiModel>()
        fun put(report: SwapReportUiModel) {
            byKey["${report.id}:${report.status}"] = report
        }
        loadLocalSwapReportFiles().forEach(::put)
        loadRustWalletSwapReports().forEach(::put)
        return byKey.values.sortedByDescending { it.startTimestamp ?: 0L }
    }

    private fun loadSwapReportsFromDisk(): List<SwapReportUiModel> = loadAllSwapReports()

    private fun loadLocalSwapReportFiles(): List<SwapReportUiModel> {
        val dir = File(appDataDir, "swap_reports")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { file ->
                parseReportJsonObject(
                    JSONObject(file.readText()),
                    file.nameWithoutExtension,
                    forceRecovered = false,
                )
            }
            .orEmpty()
    }

    /** Core lib writes taker + recovery arrays under wallets/. */
    private fun loadRustWalletSwapReports(): List<SwapReportUiModel> {
        val walletsDir = File(appDataDir, "wallets")
        if (!walletsDir.isDirectory) return emptyList()
        return walletsDir.listFiles { f ->
            f.isFile && f.name.endsWith("_swap_report.json")
        }.orEmpty().flatMap { file ->
            runCatching {
                val root = JSONObject(file.readText())
                val out = mutableListOf<SwapReportUiModel>()
                root.optJSONArray("taker")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        parseReportJsonObject(obj, "unknown", forceRecovered = false)?.let { out += it }
                    }
                }
                root.optJSONArray("recovery")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        parseReportJsonObject(obj, "unknown", forceRecovered = true)?.let { out += it }
                    }
                }
                out
            }.getOrDefault(emptyList())
        }
    }

    private fun parseReportJsonObject(
        json: JSONObject,
        fallbackId: String,
        forceRecovered: Boolean,
    ): SwapReportUiModel? =
        runCatching {
            val statusRaw = json.optString("status", "COMPLETED")
            val status = when {
                forceRecovered || statusRaw.contains("recover", true) ->
                    SwapReportUiModel.Status.RECOVERED
                statusRaw.contains("fail", true) -> SwapReportUiModel.Status.FAILED
                else -> SwapReportUiModel.Status.COMPLETED
            }
            val makers = json.optInt(
                "makers_count",
                json.optInt("maker_count", json.optInt("makerCount", 1)),
            ).coerceAtLeast(1)
            SwapReportUiModel(
                id = json.optString("swap_id", json.optString("swapId", fallbackId)),
                status = status,
                startTimestamp = json.optLong("start_timestamp", json.optLong("startTimestamp"))
                    .takeIf { it > 0 },
                durationSeconds = json.optDouble(
                    "swap_duration_seconds",
                    json.optDouble(
                        "recovery_duration_seconds",
                        json.optDouble("durationSeconds", 0.0),
                    ),
                ),
                amountSats = json.optLong(
                    "outgoing_amount",
                    json.optLong("amountSats", json.optLong("incoming_amount")),
                ),
                outputSats = json.optLong("incoming_amount", json.optLong("outputSats")),
                totalFeeSats = kotlin.math.abs(
                    json.optLong("totalFeeSats", json.optLong("fee_paid")),
                ),
                makerCount = makers,
                hops = json.optInt("hops", makers),
                protocol = json.optString("protocol", "LEGACY").ifBlank { "LEGACY" },
                errorMessage = json.optString("error_message", json.optString("errorMessage"))
                    .takeIf { it.isNotBlank() }
                    ?.let { simplifySwapError(it) },
            )
        }.getOrNull()

    private fun simplifySwapError(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("fill whole buffer") || lower.contains("unexpectedeof") ->
                "Maker connection dropped"
            lower.contains("tor") && lower.contains("connect") ->
                "Tor connection failed"
            lower.contains("timeout") ->
                "Timed out waiting for maker"
            else -> raw
                .substringAfter("message: \"", raw)
                .substringBefore("\"", raw)
                .take(80)
        }
    }

    private suspend fun <T> callFfi(operation: String, block: () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                // Serialize + lease so concurrent ViewModels cannot interleave UniFFI
                // or close the Taker under an in-flight call.
                TakerHolder.ffiMutex.withLock {
                    Result.success(TakerHolder.withLeased(block))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(IllegalStateException(humanizeFfiError(operation, e), e))
            }
        }

    /**
     * Long Tor / network polls and coinswap execution. Uses [TakerHolder.longOpMutex]
     * so long ops don't interleave with each other, without holding [ffiMutex] for
     * the entire duration (which would freeze Home/History).
     */
    private suspend fun <T> callFfiLong(operation: String, block: () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                TakerHolder.longOpMutex.withLock {
                    Result.success(TakerHolder.withLeased(block))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(IllegalStateException(humanizeFfiError(operation, e), e))
            }
        }

    private fun humanizeFfiError(operation: String, e: Throwable): String {
        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
        val lower = detail.lowercase()
        return when {
            lower.contains("security(decryption)") ||
                lower.contains("security(desc") && lower.contains("decrypt") ||
                (lower.contains("security") && lower.contains("decryption")) ->
                "$operation failed: wrong passcode (wallet is encrypted)."
            lower.contains("passwordrequired") ||
                (lower.contains("security") && lower.contains("password")) ->
                "$operation failed: this wallet needs its passcode."
            else -> "$operation failed: $detail"
        }
    }

    companion object {
        /**
         * Rust `prepare_coinswap` requires `send_amount + 10_000` sats in the wallet
         * so coin selection has room for mining fees. USE MAX must keep this reserve.
         */
        const val SWAP_PREPARE_RESERVE_SATS = 10_000L
        const val MIN_SWAP_SATS = 100_000L

        fun maxSwappableSats(poolSats: Long): Long =
            (poolSats - SWAP_PREPARE_RESERVE_SATS).coerceAtLeast(0L)

        fun poolCanFundSwap(poolSats: Long, amountSats: Long): Boolean =
            amountSats > 0L && poolSats >= amountSats + SWAP_PREPARE_RESERVE_SATS

        fun makerFitsAmount(
            online: Boolean,
            minSats: Long,
            maxSats: Long,
            liquiditySats: Long,
            amountSats: Long,
        ): Boolean {
            if (!online || amountSats <= 0L) return false
            if (amountSats < minSats) return false
            if (maxSats > 0L && amountSats > maxSats) return false
            if (liquiditySats > 0L && amountSats > liquiditySats) return false
            return true
        }

        /**
         * Largest amount ≤ [walletCap] that at least [needed] online makers can take.
         * USE MAX must respect this or Swap will show "not enough eligible makers"
         * even when Markets shows several online.
         */
        fun maxAmountFittingMakerCount(
            walletCap: Long,
            needed: Int,
            makers: List<Triple<Boolean, Long, Long>>, // online, minSats, effectiveMax (0 = unlimited)
        ): Long {
            if (needed <= 0 || walletCap < MIN_SWAP_SATS) return 0L
            val online = makers.filter { it.first }
            if (online.size < needed) return 0L

            fun fits(minSats: Long, maxSats: Long, amount: Long): Boolean {
                if (amount < minSats) return false
                if (maxSats > 0L && amount > maxSats) return false
                return true
            }

            var lo = MIN_SWAP_SATS
            var hi = walletCap
            var best = 0L
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val count = online.count { (_, minSats, maxSats) -> fits(minSats, maxSats, mid) }
                if (count >= needed) {
                    best = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            return best
        }

        fun effectiveMakerMax(maxSats: Long, liquiditySats: Long): Long {
            var cap = Long.MAX_VALUE
            if (maxSats > 0L) cap = minOf(cap, maxSats)
            if (liquiditySats > 0L) cap = minOf(cap, liquiditySats)
            return if (cap == Long.MAX_VALUE) 0L else cap
        }

        /** Reject path traversal / separators before using swapId as a filename. */
        fun isSafeSwapId(swapId: String): Boolean {
            if (swapId.isBlank()) return false
            if (swapId.contains('/') || swapId.contains('\\')) return false
            if (swapId.contains("..")) return false
            return true
        }
    }
}

class NativeCallException(val nativeError: NativeError) : Exception(nativeError.message)
