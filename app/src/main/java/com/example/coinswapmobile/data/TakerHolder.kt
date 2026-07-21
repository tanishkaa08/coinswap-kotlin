package com.example.coinswapmobile.data

import org.coinswap.Taker
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.sync.Mutex

/**
 * Holds the live UniFFI [Taker] instance after successful init.
 * Single place that owns the FFI object (no duplicate JNI loaders).
 */
object TakerHolder {
    private val ref = AtomicReference<Taker?>(null)

    /** Serializes [CoinswapRepository.initTaker] across ViewModels. */
    val initMutex = Mutex()

    fun set(taker: Taker) {
        val previous = ref.getAndSet(taker)
        if (previous != null && previous !== taker) {
            runCatching { previous.close() }
        }
    }

    fun get(): Taker? = ref.get()

    fun require(): Taker =
        get() ?: error("Taker not initialized. Complete setup and call Taker.init first.")

    fun clear() {
        val previous = ref.getAndSet(null)
        if (previous != null) {
            runCatching { previous.close() }
        }
    }

    val isInitialized: Boolean get() = get() != null
}
