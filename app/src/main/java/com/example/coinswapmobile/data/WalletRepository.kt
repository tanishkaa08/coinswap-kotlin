package com.example.coinswapmobile.data

import android.util.Log
import com.example.coinswapmobile.ui.components.PrivacyLevel
import com.example.coinswapmobile.ui.components.UtxoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
//  STUB MODE — all FFI calls are no-ops until coinswap-kotlin is built.
//  See TakerManager.kt for how to enable the real implementation.
// ─────────────────────────────────────────────────────────────────────────────

data class BalanceInfo(
    val spendable: Long,
    val swapUtxoSats: Long,
    val contractSats: Long,
    val fidelityBondSats: Long,
)

data class TxDisplayItem(
    val txid: String,
    val amountSats: Long,
    val confirmations: Int,
    val label: String,
    val timeAgo: String = "unknown",
    val isReceive: Boolean = false,
)

fun formatRelativeTime(epochSeconds: Long): String {
    val diff = System.currentTimeMillis() / 1000 - epochSeconds
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        else -> "${diff / 86400}d ago"
    }
}

class WalletRepository {

    private companion object {
        const val TAG = "WalletRepository"
    }

    suspend fun getBalances(): Result<BalanceInfo> = withContext(Dispatchers.IO) {
        val taker = TakerManager.rawTaker()
        if (taker == null) {
            return@withContext Result.success(BalanceInfo(0, 0, 0, 0))
        }
        try {
            val balances = taker.javaClass.getMethod("getBalances").invoke(taker)!!
            val get = { name: String ->
                runCatching { balances.javaClass.getMethod(name).invoke(balances) as Long }.getOrDefault(0L)
            }
            Result.success(
                BalanceInfo(
                    spendable        = get("getRegular") + get("getSwap"),
                    swapUtxoSats     = get("getSwap"),
                    contractSats     = get("getContract"),
                    fidelityBondSats = get("getFidelity"),
                )
            )
        } catch (e: Throwable) {
            Log.e(TAG, "getBalances failed", e)
            Result.failure(e)
        }
    }

    /** Returns UTXOs already mapped to the UI display model. */
    suspend fun listUtxos(): Result<List<UtxoItem>> = withContext(Dispatchers.IO) {
        val taker = TakerManager.rawTaker()
        if (taker == null) return@withContext Result.success(emptyList())
        try {
            @Suppress("UNCHECKED_CAST")
            val raw = taker.javaClass.getMethod("listUtxos").invoke(taker) as? List<*>
                ?: return@withContext Result.success(emptyList())

            val items = raw.mapNotNull { u ->
                if (u == null) return@mapNotNull null
                val cls     = u.javaClass
                val txidObj = cls.getMethod("getTxid").invoke(u)
                val txid    = txidObj?.javaClass?.getMethod("toString")?.invoke(txidObj)?.toString()
                    ?: return@mapNotNull null
                val vout    = (cls.getMethod("getVout").invoke(u) as? Number)?.toInt() ?: 0
                val value   = (cls.getMethod("getValue").invoke(u) as? Number)?.toLong() ?: 0L
                val confs   = (cls.getMethod("getConfirmations").invoke(u) as? Number)?.toInt() ?: 0
                val privacy = when {
                    confs == 0 -> PrivacyLevel.LOW
                    confs < 3  -> PrivacyLevel.MED
                    else       -> PrivacyLevel.HIGH
                }
                UtxoItem(
                    address      = "$txid:$vout",
                    amountBtc    = "%.8f".format(value / 100_000_000.0),
                    privacyLevel = privacy,
                )
            }
            Result.success(items)
        } catch (e: Throwable) {
            Log.e(TAG, "listUtxos failed", e)
            Result.failure(e)
        }
    }

    suspend fun getTransactions(count: Int = 20): Result<List<TxDisplayItem>> = withContext(Dispatchers.IO) {
        val taker = TakerManager.rawTaker()
        if (taker == null) return@withContext Result.success(emptyList())
        try {
            @Suppress("UNCHECKED_CAST")
            val raw = taker.javaClass.getMethod("listTransactions").invoke(taker) as? List<*>
                ?: return@withContext Result.success(emptyList())

            val items = raw.take(count).mapNotNull { t ->
                if (t == null) return@mapNotNull null
                val cls     = t.javaClass
                val txidObj = cls.getMethod("getTxid").invoke(t)
                val txid    = txidObj?.javaClass?.getMethod("toString")?.invoke(txidObj)?.toString()
                    ?: return@mapNotNull null
                val amount    = (cls.getMethod("getAmount").invoke(t) as? Number)?.toLong() ?: 0L
                val confs     = (cls.getMethod("getConfirmations").invoke(t) as? Number)?.toInt() ?: 0
                val label     = runCatching { cls.getMethod("getLabel").invoke(t)?.toString() ?: "" }.getOrDefault("")
                val timeEpoch = runCatching { (cls.getMethod("getTime").invoke(t) as? Number)?.toLong() ?: 0L }.getOrDefault(0L)
                val timeAgo   = if (timeEpoch > 0) formatRelativeTime(timeEpoch) else "unconfirmed"
                TxDisplayItem(txid, amount, confs, label, timeAgo, isReceive = amount >= 0)
            }
            Result.success(items)
        } catch (e: Throwable) {
            Log.e(TAG, "getTransactions failed", e)
            Result.failure(e)
        }
    }

    /** feeRate (sat/vB) is accepted but may be ignored by the Taker version. */
    suspend fun sendToAddress(address: String, amountSats: Long, feeRate: Int = 2): Result<String> = withContext(Dispatchers.IO) {
        val taker = TakerManager.rawTaker()
        if (taker == null) return@withContext Result.failure(Exception("Not connected to a wallet"))
        try {
            val txidObj = taker.javaClass
                .getMethod("sendToAddress", String::class.java, Long::class.java)
                .invoke(taker, address, amountSats)
                ?: return@withContext Result.failure(Exception("No txid returned"))
            val txid = txidObj.javaClass.getMethod("toString").invoke(txidObj)?.toString()
                ?: return@withContext Result.failure(Exception("Could not parse txid"))
            Result.success(txid)
        } catch (e: Throwable) {
            Log.e(TAG, "sendToAddress failed", e)
            Result.failure(e)
        }
    }

    suspend fun getNextAddress(): Result<String> = withContext(Dispatchers.IO) {
        val taker = TakerManager.rawTaker()
        if (taker == null) return@withContext Result.failure(Exception("Not connected to a wallet yet"))
        try {
            val addressTypeClass = Class.forName("org.coinswap.AddressType")
            val external = addressTypeClass.getField("EXTERNAL").get(null)
            val address = taker.javaClass
                .getMethod("getNextAddress", addressTypeClass)
                .invoke(taker, external) as? String
                ?: return@withContext Result.failure(Exception("Null address returned"))
            Result.success(address)
        } catch (e: Throwable) {
            Log.e(TAG, "getNextAddress failed", e)
            Result.failure(e)
        }
    }

    suspend fun syncAndSave(): Result<Unit> = withContext(Dispatchers.IO) {
        val taker = TakerManager.rawTaker()
        if (taker == null) return@withContext Result.success(Unit)
        try {
            taker.javaClass.getMethod("syncAndSave").invoke(taker)
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "syncAndSave failed", e)
            Result.failure(e)
        }
    }
}
