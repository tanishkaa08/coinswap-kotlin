package com.example.coinswapmobile.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Connectivity probes for Tor SOCKS and control ports.
 *
 * UniFFI [org.coinswap.Taker.init] hardcodes SOCKS `127.0.0.1:9050` on the Rust side;
 * [checkSocks] always probes that fixed endpoint.
 */
object TorManager {

    data class PortStatus(
        val reachable: Boolean,
        val host: String,
        val port: Int,
        val message: String,
    )

    /** Probes the UniFFI SOCKS endpoint (`127.0.0.1:9050`). */
    suspend fun checkSocks(timeoutMs: Int = 5_000): PortStatus = checkSocksHandshake(
        host = TakerAppConfig.DEFAULT_SOCKS_HOST,
        port = TakerAppConfig.DEFAULT_SOCKS_PORT,
        timeoutMs = timeoutMs,
    )

    suspend fun checkControl(
        host: String = TakerAppConfig.DEFAULT_SOCKS_HOST,
        port: Int = TakerAppConfig.DEFAULT_TOR_CONTROL,
        timeoutMs: Int = 5_000,
    ): PortStatus = checkTcp(host, port, "Tor control", timeoutMs)

    private suspend fun checkSocksHandshake(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): PortStatus = withContext(Dispatchers.IO) {
        try {
            // Keep one socket scope for write + read — closing DataOutputStream would
            // close the socket before the SOCKS5 reply can be read.
            Socket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val out = DataOutputStream(socket.getOutputStream())
                val inp = DataInputStream(socket.getInputStream())
                // SOCKS5 greeting: VER=5, NMETHODS=1, METHOD=0 (no auth)
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                val ver = inp.read()
                val method = inp.read()
                if (ver != 0x05 || method == 0xFF || method < 0) {
                    error("SOCKS5 handshake rejected (ver=$ver method=$method)")
                }
            }
            PortStatus(true, host, port, "Tor SOCKS reachable at $host:$port")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PortStatus(
                false,
                host,
                port,
                "Tor SOCKS not reachable at $host:$port (${e.message})",
            )
        }
    }

    private suspend fun checkTcp(
        host: String,
        port: Int,
        label: String,
        timeoutMs: Int,
    ): PortStatus = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            PortStatus(true, host, port, "$label reachable at $host:$port")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PortStatus(
                false,
                host,
                port,
                "$label not reachable at $host:$port (${e.message})",
            )
        }
    }
}
