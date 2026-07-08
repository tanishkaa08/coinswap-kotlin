package com.example.coinswapmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Connectivity probes for Tor SOCKS and control ports.
 * FFI Taker.init hardcodes SOCKS 9050 on the Rust side; control port is passed through.
 */
object TorManager {

    data class PortStatus(
        val reachable: Boolean,
        val host: String,
        val port: Int,
        val message: String,
    )

    suspend fun checkSocks(
        host: String = TakerAppConfig.DEFAULT_SOCKS_HOST,
        port: Int = TakerAppConfig.DEFAULT_SOCKS_PORT,
        timeoutMs: Int = 5_000,
    ): PortStatus = checkPort(host, port, "Tor SOCKS", timeoutMs)

    suspend fun checkControl(
        host: String = TakerAppConfig.DEFAULT_SOCKS_HOST,
        port: Int = TakerAppConfig.DEFAULT_TOR_CONTROL,
        timeoutMs: Int = 5_000,
    ): PortStatus = checkPort(host, port, "Tor control", timeoutMs)

    private suspend fun checkPort(
        host: String,
        port: Int,
        label: String,
        timeoutMs: Int,
    ): PortStatus = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            PortStatus(true, host, port, "$label reachable at $host:$port")
        }.getOrElse { e ->
            PortStatus(
                false,
                host,
                port,
                "$label not reachable at $host:$port (${e.message})",
            )
        }
    }
}
