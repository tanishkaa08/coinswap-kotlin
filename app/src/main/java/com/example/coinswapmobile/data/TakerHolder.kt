package com.example.coinswapmobile.data

import org.coinswap.Taker
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the live UniFFI [Taker] instance after successful init.
 * Single place that owns the FFI object (no duplicate JNI loaders).
 */
object TakerHolder {
    private val ref = AtomicReference<Taker?>(null)

    fun set(taker: Taker) {
        ref.set(taker)
    }

    fun get(): Taker? = ref.get()

    fun require(): Taker =
        get() ?: error("Taker not initialized. Complete setup and call Taker.init first.")

    fun clear() {
        ref.set(null)
    }

    val isInitialized: Boolean get() = get() != null
}
