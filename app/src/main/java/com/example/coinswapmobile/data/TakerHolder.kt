package com.example.coinswapmobile.data

import org.coinswap.Taker
import kotlinx.coroutines.sync.Mutex

/**
 * Holds the live UniFFI [Taker] after successful init.
 *
 * Callers that touch the native object must run inside [withLeased] so
 * [set]/[clear] defer `close()` until in-flight work drains.
 */
object TakerHolder {
    private val lock = Any()
    private var current: Taker? = null
    private var activeLeases = 0
    private var pendingClose: Taker? = null

    /** Serializes [CoinswapRepository.initTaker] across ViewModels. */
    val initMutex = Mutex()

    /**
     * Hold a lease for the duration of [block]. [require] is only safe inside this.
     * When the last lease ends, any retired Taker from [set]/[clear] is closed.
     */
    fun <R> withLeased(block: () -> R): R {
        synchronized(lock) { activeLeases++ }
        try {
            return block()
        } finally {
            val toClose = synchronized(lock) {
                activeLeases--
                if (activeLeases == 0) {
                    val retired = pendingClose
                    pendingClose = null
                    retired
                } else {
                    null
                }
            }
            if (toClose != null) runCatching { toClose.close() }
        }
    }

    fun set(taker: Taker) {
        val previous = synchronized(lock) {
            val prev = current
            current = taker
            if (prev != null && prev !== taker) prev else null
        }
        if (previous != null) retire(previous)
    }

    fun get(): Taker? = synchronized(lock) { current }

    fun require(): Taker =
        get() ?: error("Taker not initialized. Complete setup and call Taker.init first.")

    fun clear() {
        val previous = synchronized(lock) {
            val prev = current
            current = null
            prev
        }
        if (previous != null) retire(previous)
    }

    val isInitialized: Boolean get() = get() != null

    private fun retire(taker: Taker) {
        val closeNow = synchronized(lock) {
            if (activeLeases == 0) {
                true
            } else {
                // Keep only the latest retired instance; close any earlier one immediately.
                val older = pendingClose
                pendingClose = taker
                if (older != null && older !== taker) {
                    runCatching { older.close() }
                }
                false
            }
        }
        if (closeNow) runCatching { taker.close() }
    }
}
