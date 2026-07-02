package com.example.coinswapmobile.data

import android.os.Build
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

/**
 * JNI bridge to the Rust `coinswap_mobile` library (vanilla Electrum wallet).
 *
 * Every function returns a JSON string. Success payloads carry wallet data;
 * failures come back as `{"error": "..."}`. Parsing/enveloping lives in
 * [CoinswapRepository] so callers never touch raw JSON.
 */
object CoinSwapNative {

    private const val TAG = "CoinSwapNative"
    private const val LIB_NAME = "coinswap_mobile"

    enum class LoadState {
        /** [System.loadLibrary] succeeded. */
        LOADED,
        /** APK does not contain libcoinswap_mobile.so for this device ABI. */
        LIBRARY_NOT_FOUND,
        /** .so is present but dlopen failed (e.g. missing libc++_shared.so). */
        LOAD_FAILED,
    }

    private data class LoadInfo(
        val state: LoadState,
        val message: String?,
        val stackTrace: String?,
    )

    private val loadInfo: LoadInfo

    /** True only when [System.loadLibrary] completed without error. */
    val isAvailable: Boolean
        get() = loadInfo.state == LoadState.LOADED

    val loadState: LoadState get() = loadInfo.state

    /** Human-readable failure from [System.loadLibrary], if any. */
    val loadErrorMessage: String? get() = loadInfo.message

    /** Full stack trace from the load failure, if any. */
    val loadErrorStackTrace: String? get() = loadInfo.stackTrace

    /** One-line status for logs / debug UI. */
    val nativeStatus: String

    init {
        loadInfo = loadLibrary()
        nativeStatus = buildStatus(loadInfo)
        if (isAvailable) {
            Log.i(TAG, nativeStatus)
        } else {
            Log.e(TAG, nativeStatus, loadInfo.stackTrace?.let { Exception(it) })
        }
    }

    external fun initElectrumWallet(
        dataDir: String,
        electrumUrl: String,
        walletName: String,
    ): String

    external fun syncWallet(): String

    external fun getBalance(): String

    external fun getNewAddress(): String

    external fun listUtxos(): String

    private fun loadLibrary(): LoadInfo {
        return try {
            runCatching { System.loadLibrary("c++_shared") }
                .onFailure { Log.w(TAG, "libc++_shared preload skipped: ${it.message}") }
            System.loadLibrary(LIB_NAME)
            LoadInfo(LoadState.LOADED, null, null)
        } catch (e: UnsatisfiedLinkError) {
            val message = e.message.orEmpty()
            val state = when {
                message.contains(LIB_NAME, ignoreCase = true) &&
                    (message.contains("not found", ignoreCase = true) ||
                        message.contains("cannot open", ignoreCase = true)) ->
                    LoadState.LIBRARY_NOT_FOUND

                else -> LoadState.LOAD_FAILED
            }
            LoadInfo(state, message.ifBlank { e.toString() }, stackTraceOf(e))
        } catch (t: Throwable) {
            LoadInfo(LoadState.LOAD_FAILED, t.message ?: t.toString(), stackTraceOf(t))
        }
    }

    private fun deviceAbis(): String =
        Build.SUPPORTED_ABIS?.joinToString(", ") ?: Build.CPU_ABI ?: "unknown"

    private fun buildStatus(info: LoadInfo): String = when (info.state) {
        LoadState.LOADED ->
            "Native: lib$LIB_NAME loaded"

        LoadState.LIBRARY_NOT_FOUND -> buildString {
            append("Native: lib$LIB_NAME not found for device ABI (")
            append(deviceAbis())
            append("). ")
            if (deviceAbis().contains("x86")) {
                append("Use an ARM64 emulator image, or build libcoinswap_mobile.so for x86_64. ")
            }
            append("Ensure jniLibs/<abi>/lib$LIB_NAME.so is packaged in the APK.")
        }

        LoadState.LOAD_FAILED -> buildString {
            append("Native: lib$LIB_NAME failed to load")
            info.message?.let { append(" — ").append(it) }
            if (info.message?.contains("libc++_shared", ignoreCase = true) == true) {
                append(
                    ". Bundle libc++_shared.so from the Android NDK into jniLibs " +
                        "(same ABI folders as lib$LIB_NAME.so)."
                )
            }
        }
    }

    private fun stackTraceOf(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
