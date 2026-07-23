package com.example.coinswapmobile.data

import org.coinswap.Taker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds the live UniFFI [Taker] after successful init.
 *
 * Callers that touch the native object must run inside [withLeased] so
 * [set]/[clear] defer `close()` until in-flight work drains.
 * [ffiMutex] serializes native calls so concurrent ViewModels do not
 * interleave FFI on the same Taker.
 */
object TakerHolder {
    private val lock = Any()
    private var current: Taker? = null
    private var activeLeases = 0
    /** Retired Takers waiting for [activeLeases] to hit zero. Never closed early. */
    private val pendingCloses = mutableListOf<Taker>()

    /** Serializes [CoinswapRepository.initTaker] across ViewModels. */
    val initMutex = Mutex()

    /** Serializes all UniFFI operations on the live Taker. */
    val ffiMutex = Mutex()

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
            toClose.forEach { closeQuietly(it) }
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
                if (pendingCloses.none { it === taker }) {
                    pendingCloses.add(taker)
                }
                false
            }
        }
        if (closeNow) closeQuietly(taker)
    }

    /** Close without absorbing coroutine cancellation. */
    fun closeQuietly(taker: Taker) {
        try {
            taker.close()
        } catch (_: CancellationException) {
            throw CancellationException("Taker.close cancelled")
        } catch (_: Exception) {
            // Best-effort dispose of UniFFI handle.
        }
    }
}
