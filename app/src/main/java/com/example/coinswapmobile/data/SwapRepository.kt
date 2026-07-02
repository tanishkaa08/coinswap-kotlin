package com.example.coinswapmobile.data

import android.util.Log
import com.example.coinswapmobile.screens.ReportStatus
import com.example.coinswapmobile.screens.SwapReport
import com.example.coinswapmobile.screens.SwapUtxo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
//  STUB MODE — all FFI calls are no-ops until coinswap-kotlin is built.
//  See TakerManager.kt for how to enable the real implementation.
// ─────────────────────────────────────────────────────────────────────────────

private data class PendingSwap(
    val amountSats: Long,
    val makerCount: Int,
    val feeRateSatPerVb: Int,
    val utxos: List<SwapUtxo>,
)

class SwapRepository {

    private companion object {
        const val TAG = "SwapRepository"
    }

    private val pendingSwaps = mutableMapOf<String, PendingSwap>()

    suspend fun listSpendableUtxos(): Result<List<SwapUtxo>> = withContext(Dispatchers.IO) {
        val taker = TakerManager.rawTaker()
        if (taker == null) return@withContext Result.success(emptyList())
        try {
            @Suppress("UNCHECKED_CAST")
            val raw = taker.javaClass.getMethod("listSpendableUtxos").invoke(taker) as? List<*>
                ?: return@withContext Result.success(emptyList())

            val items = raw.mapNotNull { u ->
                if (u == null) return@mapNotNull null
                val cls     = u.javaClass
                val txidObj = cls.getMethod("getTxid").invoke(u)
                val txid    = txidObj?.javaClass?.getMethod("toString")?.invoke(txidObj)?.toString()
                    ?: return@mapNotNull null
                val value   = (cls.getMethod("getValue").invoke(u) as? Number)?.toLong() ?: 0L
                val confs   = (cls.getMethod("getConfirmations").invoke(u) as? Number)?.toInt() ?: 0
                SwapUtxo(txid = txid, amountSats = value, confirmed = confs > 0, selected = true)
            }
            Result.success(items)
        } catch (e: Throwable) {
            Log.e(TAG, "listSpendableUtxos failed", e)
            Result.failure(e)
        }
    }

    /**
     * Validates parameters and reserves a local swap ID.
     * The actual FFI call happens in [startCoinswap].
     */
    suspend fun prepareCoinswap(
        amountSats: Long,
        makerCount: Int,
        feeRateSatPerVb: Int,
        selectedUtxos: List<SwapUtxo>,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!TakerManager.isInitialized) {
            return@withContext Result.failure(
                Exception("Not connected — build coinswap-kotlin and provide a Bitcoin Core node first")
            )
        }
        if (amountSats <= 0) return@withContext Result.failure(Exception("Amount must be positive"))
        if (selectedUtxos.isEmpty()) return@withContext Result.failure(Exception("Select at least one UTXO"))

        val swapId = UUID.randomUUID().toString().replace("-", "").take(10)
        pendingSwaps[swapId] = PendingSwap(amountSats, makerCount, feeRateSatPerVb, selectedUtxos)
        Result.success(swapId)
    }

    /**
     * Executes the swap prepared by [prepareCoinswap].
     * Blocking: may run for minutes — call from a coroutine on IO dispatcher.
     */
    suspend fun startCoinswap(swapId: String): Result<SwapReport> = withContext(Dispatchers.IO) {
        val pending = pendingSwaps.remove(swapId)
            ?: return@withContext Result.failure(Exception("Unknown swap ID: $swapId"))

        val taker = TakerManager.rawTaker()
            ?: return@withContext Result.failure(Exception("Taker not initialized"))

        val startMs = System.currentTimeMillis()
        try {
            val outPointClass   = Class.forName("org.coinswap.OutPoint")
            val txidClass       = Class.forName("org.coinswap.Txid")
            val swapParamsClass = Class.forName("org.coinswap.SwapParams")

            val outPoints = pending.utxos.map { u ->
                val txidObj = txidClass.getDeclaredConstructor(String::class.java).newInstance(u.txid)
                outPointClass.getDeclaredConstructor(txidClass, Int::class.javaPrimitiveType!!)
                    .newInstance(txidObj, 0)
            }

            val params = swapParamsClass
                .getDeclaredConstructor(List::class.java, Long::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
                .newInstance(outPoints, pending.amountSats, pending.makerCount, 3)

            val reportObj = taker.javaClass.getMethod("doCoinswap", swapParamsClass)
                .invoke(taker, params)
                ?: return@withContext Result.failure(Exception("Swap returned no report"))

            val elapsedMs   = System.currentTimeMillis() - startMs
            val elapsedMins = elapsedMs / 60_000
            val durationStr = "${elapsedMins / 60}h ${elapsedMins % 60}m"

            val cls       = reportObj.javaClass
            val fee       = runCatching { (cls.getMethod("getFeePaid").invoke(reportObj) as? Number)?.toLong() ?: 0L }.getOrDefault(0L)
            val statusStr = runCatching { cls.getMethod("getStatus").invoke(reportObj)?.toString() ?: "completed" }.getOrDefault("completed")
            val status    = if (statusStr.contains("fail", ignoreCase = true)) ReportStatus.FAILED else ReportStatus.COMPLETED

            Result.success(
                SwapReport(
                    id           = swapId,
                    timeAgo      = "just now",
                    duration     = durationStr,
                    status       = status,
                    hops         = pending.makerCount * 2,
                    protocol     = "TAPROOT",
                    amountSats   = pending.amountSats,
                    makerCount   = pending.makerCount,
                    totalFeeSats = fee,
                    outputSats   = pending.amountSats,
                )
            )
        } catch (e: Throwable) {
            Log.e(TAG, "startCoinswap failed", e)
            Result.failure(e)
        }
    }

    suspend fun recoverActiveSwap(): Result<String> = withContext(Dispatchers.IO) {
        val taker = TakerManager.rawTaker()
            ?: return@withContext Result.failure(Exception("Not connected"))
        try {
            val result = taker.javaClass.getMethod("recoverActiveSwap").invoke(taker)?.toString() ?: "recovered"
            Result.success(result)
        } catch (e: Throwable) {
            Log.e(TAG, "recoverActiveSwap failed", e)
            Result.failure(e)
        }
    }

    suspend fun getSwapReports(): Result<List<SwapReport>> = withContext(Dispatchers.IO) {
        val taker = TakerManager.rawTaker()
        if (taker == null) return@withContext Result.success(emptyList())
        try {
            @Suppress("UNCHECKED_CAST")
            val raw = taker.javaClass.getMethod("listSwapReports").invoke(taker) as? List<*>
                ?: return@withContext Result.success(emptyList())

            val reports = raw.mapNotNull { r ->
                if (r == null) return@mapNotNull null
                val cls       = r.javaClass
                val id        = runCatching { cls.getMethod("getSwapId").invoke(r)?.toString() ?: "" }.getOrDefault("")
                val amount    = runCatching { (cls.getMethod("getAmount").invoke(r) as? Number)?.toLong() ?: 0L }.getOrDefault(0L)
                val fee       = runCatching { (cls.getMethod("getFeePaid").invoke(r) as? Number)?.toLong() ?: 0L }.getOrDefault(0L)
                val statusStr = runCatching { cls.getMethod("getStatus").invoke(r)?.toString() ?: "" }.getOrDefault("")
                val status    = if (statusStr.contains("fail", ignoreCase = true)) ReportStatus.FAILED else ReportStatus.COMPLETED
                @Suppress("UNCHECKED_CAST")
                val makerCount = runCatching { (cls.getMethod("getMakerAddresses").invoke(r) as? List<*>)?.size ?: 1 }.getOrDefault(1)
                SwapReport(
                    id           = id,
                    timeAgo      = "unknown",
                    duration     = "unknown",
                    status       = status,
                    hops         = makerCount * 2,
                    protocol     = "TAPROOT",
                    amountSats   = amount,
                    makerCount   = makerCount,
                    totalFeeSats = fee,
                    outputSats   = amount,
                )
            }
            Result.success(reports)
        } catch (e: Throwable) {
            Log.e(TAG, "getSwapReports failed", e)
            Result.failure(e)
        }
    }
}
