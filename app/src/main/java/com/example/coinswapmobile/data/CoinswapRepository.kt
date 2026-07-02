package com.example.coinswapmobile.data

import com.example.coinswapmobile.model.UtxoUiModel
import com.example.coinswapmobile.model.WalletState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * Repository over the Rust Electrum wallet. Wraps every native call in a
 * coroutine on [Dispatchers.IO], parses the JSON, and returns [Result].
 * The rest of the app (ViewModel/UI) never sees JNI or JSON directly.
 */
class CoinswapRepository(
    private val appDataDir: String,
) {

    /** Latest native load / call status for debug surfaces. */
    val nativeStatus: String
        get() = CoinSwapNative.nativeStatus

    suspend fun initElectrum(electrumUrl: String, walletName: String): Result<WalletState> =
        callForState { CoinSwapNative.initElectrumWallet(appDataDir, electrumUrl, walletName) }

    suspend fun syncWallet(): Result<WalletState> =
        callForState { CoinSwapNative.syncWallet() }

    suspend fun getBalance(): Result<WalletState> =
        callForState { CoinSwapNative.getBalance() }

    suspend fun listUtxos(): Result<List<UtxoUiModel>> =
        callForState { CoinSwapNative.listUtxos() }.map { it.utxos }

    suspend fun getNewAddress(): Result<String> =
        callNative("getNewAddress") {
            val json = JSONObject(CoinSwapNative.getNewAddress())
            json.errorOrNull()?.let { throw IllegalStateException(it) }
            json.getString("address")
        }

    // ── internals ───────────────────────────────────────────────────────────

    private suspend fun callForState(block: () -> String): Result<WalletState> =
        callNative("walletState") {
            val json = JSONObject(block())
            json.errorOrNull()?.let { throw IllegalStateException(it) }
            json.toWalletState()
        }

    private suspend fun <T> callNative(
        operation: String,
        block: () -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        if (!CoinSwapNative.isAvailable) {
            return@withContext Result.failure(loadFailure())
        }
        runCatching { block() }.mapFailure { mapNativeFailure(operation, it) }
    }

    private fun loadFailure(): IllegalStateException = when (CoinSwapNative.loadState) {
        CoinSwapNative.LoadState.LIBRARY_NOT_FOUND ->
            IllegalStateException(
                "Native wallet library not found for this device ABI. " +
                    "Expected libcoinswap_mobile.so in the APK jniLibs folder. " +
                    "(${CoinSwapNative.loadErrorMessage})"
            )

        CoinSwapNative.LoadState.LOAD_FAILED ->
            IllegalStateException(
                "Native wallet library failed to load: " +
                    (CoinSwapNative.loadErrorMessage ?: "unknown error")
            )

        CoinSwapNative.LoadState.LOADED ->
            IllegalStateException("Native wallet is not available.")
    }

    private fun mapNativeFailure(operation: String, error: Throwable): Throwable {
        val detail = error.message?.takeIf { it.isNotBlank() } ?: error.toString()
        return when (error) {
            is UnsatisfiedLinkError ->
                IllegalStateException(
                    "JNI method not found for $operation: $detail. " +
                        "Kotlin package/class/method names must match the Rust " +
                        "Java_com_example_coinswapmobile_data_CoinSwapNative_* exports.",
                    error,
                )

            is JSONException ->
                IllegalStateException("Native $operation returned invalid JSON: $detail", error)

            is IllegalStateException -> error

            else ->
                IllegalStateException("Native $operation failed: $detail", error)
        }
    }

    private fun JSONObject.errorOrNull(): String? =
        if (has("error") && !isNull("error")) getString("error") else null

    private fun JSONObject.toWalletState(): WalletState {
        val utxoArray = optJSONArray("utxos")
        val utxos = buildList {
            if (utxoArray != null) {
                for (i in 0 until utxoArray.length()) {
                    val u = utxoArray.getJSONObject(i)
                    add(
                        UtxoUiModel(
                            txid = u.optString("txid"),
                            vout = u.optInt("vout"),
                            amountSats = u.optLong("amountSats"),
                            confirmations = if (u.has("confirmations")) u.optInt("confirmations") else null,
                        )
                    )
                }
            }
        }
        return WalletState(
            balanceSats = optLong("balanceSats"),
            confirmedSats = optLong("confirmedSats"),
            unconfirmedSats = optLong("unconfirmedSats"),
            backend = optString("backend", "ELECTRUM"),
            lastSyncUnix = if (has("lastSyncUnix")) optLong("lastSyncUnix") else null,
            blockHeight = if (has("blockHeight")) optLong("blockHeight") else null,
            utxos = utxos,
        )
    }
}

private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
    fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(transform(it)) })
