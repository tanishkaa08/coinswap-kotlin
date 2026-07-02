package com.example.coinswapmobile.data

import android.util.Log
import com.example.coinswapmobile.screens.SwapMaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
//  STUB MODE — all FFI calls are no-ops until coinswap-kotlin is built.
//  See TakerManager.kt for how to enable the real implementation.
// ─────────────────────────────────────────────────────────────────────────────

class MarketRepository {

    private companion object {
        const val TAG = "MarketRepository"
    }

    suspend fun fetchOffers(): Result<List<SwapMaker>> = withContext(Dispatchers.IO) {
        val taker = TakerManager.rawTaker()
        if (taker == null) return@withContext Result.success(emptyList())
        try {
            @Suppress("UNCHECKED_CAST")
            val raw = taker.javaClass.getMethod("listOffers").invoke(taker) as? List<*>
                ?: return@withContext Result.success(emptyList())

            val makers = raw.mapNotNull { o ->
                if (o == null) return@mapNotNull null
                val cls      = o.javaClass
                val address  = cls.getMethod("getMakerAddress").invoke(o)?.toString()
                    ?: return@mapNotNull null
                val minSats  = (cls.getMethod("getMinSwapAmount").invoke(o) as? Number)?.toLong() ?: 0L
                val maxSats  = (cls.getMethod("getMaxSwapAmount").invoke(o) as? Number)?.toLong() ?: 0L
                val fee      = (cls.getMethod("getFeeRatePct").invoke(o) as? Number)?.toDouble() ?: 0.0
                val liquidity= runCatching { (cls.getMethod("getLiquidity").invoke(o) as? Number)?.toLong() ?: maxSats }.getOrDefault(maxSats)
                val fidelity = runCatching { (cls.getMethod("getFidelityBondValue").invoke(o) as? Number)?.toDouble()?.div(100_000_000.0) ?: 0.0 }.getOrDefault(0.0)
                SwapMaker(
                    id              = address,
                    feeRatePct      = fee,
                    minSats         = minSats,
                    maxSats         = maxSats,
                    liquiditySats   = liquidity,
                    fidelityBondBtc = fidelity,
                    onionAddress    = address,
                    online          = true,
                )
            }
            Result.success(makers)
        } catch (e: Throwable) {
            Log.e(TAG, "fetchOffers failed", e)
            Result.failure(e)
        }
    }

    /** Syncs the offer book from the network; returns Result.success(Unit) on completion. */
    suspend fun syncOfferbookAndWait(): Result<Unit> = withContext(Dispatchers.IO) {
        val taker = TakerManager.rawTaker()
        if (taker == null) return@withContext Result.success(Unit)
        try {
            taker.javaClass.getMethod("syncOfferbook").invoke(taker)
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "syncOfferbookAndWait failed", e)
            Result.failure(e)
        }
    }
}
