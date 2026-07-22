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
    /** Retired Takers waiting for [activeLeases] to hit zero. Never closed early. */
    private val pendingCloses = mutableListOf<Taker>()

    /** Serializes [CoinswapRepository.initTaker] across ViewModels. */
    val initMutex = Mutex()

    /**
     * Hold a lease for the duration of [block]. [require] is only safe inside this.
     * When the last lease ends, every retired Taker is closed.
     */
    fun <R> withLeased(block: () -> R): R {
        synchronized(lock) { activeLeases++ }
        try {
            return block()
        } finally {
            val toClose = synchronized(lock) {
                activeLeases--
                if (activeLeases == 0) {
                    val retired = pendingCloses.toList()
                    pendingCloses.clear()
                    retired
                } else {
                    emptyList()
                }
            }
            toClose.forEach { runCatching { it.close() } }
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
                // Never close while any lease is active — queue until drain.
                if (pendingCloses.none { it === taker }) {
                    pendingCloses.add(taker)
                }
                false
            }
        }
        if (closeNow) runCatching { taker.close() }
    }
}
