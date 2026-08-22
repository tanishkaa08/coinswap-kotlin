package com.example.coinswapmobile.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Orbot-only Tor. The UniFFI taker talks to makers via SOCKS `127.0.0.1:9050`
 * (hardcoded in coinswap-ffi). Control port is optional — Orbot often has none.
 *
 * In-app `TorService` was removed: embedded Tor dropped PoF mid-swap
 * (`UnexpectedEof` / SOCKS timeouts) and fought Orbot for port 9050.
 */
object TorManager {

    private const val TAG = "TorManager"
    private const val READY_TIMEOUT_MS = 90_000L
    private const val POLL_MS = 1_500L

    data class PortStatus(
        val reachable: Boolean,
        val host: String,
        val port: Int,
        val message: String,
        val controlPassword: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Probe Orbot SOCKS in the background at app start (does not start Tor). */
    fun startInBackground(context: Context) {
        val app = context.applicationContext
        scope.launch {
            ensureRunning(app, timeoutMs = 5_000L)
        }
    }

    /**
     * Wait until Orbot exposes a usable SOCKS5 listener on 9050.
     * Does not start any in-app Tor daemon.
     */
    suspend fun ensureRunning(
        context: Context,
        timeoutMs: Long = READY_TIMEOUT_MS,
    ): PortStatus {
        @Suppress("UNUSED_PARAMETER")
        context
        val existing = checkExternalOrbotReady()
        if (existing.reachable) return existing
        return waitUntilSocksReady(timeoutMs)
    }

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
    ): PortStatus = checkControlAuth(host, port, timeoutMs)

    /**
     * Control password if Orbot (or another daemon) exposes AUTHENTICATE on 9051.
     * Usually null for Orbot — FFI accepts SOCKS-only when control fails.
     */
    fun probeControlPassword(
        host: String = TakerAppConfig.DEFAULT_SOCKS_HOST,
        port: Int = TakerAppConfig.DEFAULT_TOR_CONTROL,
        timeoutMs: Int = 2_000,
    ): String? {
        val passwords = listOf("", TakerAppConfig.DEMO_TOR_PASSWORD)
        for (password in passwords) {
            try {
                Socket().use { socket ->
                    socket.soTimeout = timeoutMs
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    val writer = OutputStreamWriter(socket.getOutputStream())
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    writer.write("AUTHENTICATE \"$password\"\r\n")
                    writer.flush()
                    val line = reader.readLine().orEmpty()
                    if (line.startsWith("250")) return password
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Orbot manages bootstrap. SOCKS accepting connections is enough for .onion.
     */
    suspend fun waitUntilBootstrapped(
        timeoutMs: Long = READY_TIMEOUT_MS,
        passwordHint: String? = null,
    ): PortStatus {
        @Suppress("UNUSED_PARAMETER")
        passwordHint
        return waitUntilSocksReady(timeoutMs)
    }

    suspend fun ensureBootstrappedForOnions(
        context: Context,
        passwordHint: String? = null,
    ): PortStatus {
        @Suppress("UNUSED_PARAMETER")
        passwordHint
        return ensureRunning(context)
    }

    /** Best-effort NEWNYM when Orbot exposes control; no-op otherwise. */
    suspend fun requestNewNym(passwordHint: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val passwords = buildList {
                if (passwordHint != null) add(passwordHint)
                add("")
                add(TakerAppConfig.DEMO_TOR_PASSWORD)
            }.distinct()
            try {
                val success = withAuthenticatedControl(
                    host = TakerAppConfig.DEFAULT_SOCKS_HOST,
                    port = TakerAppConfig.DEFAULT_TOR_CONTROL,
                    timeoutMs = 4_000,
                    passwords = passwords,
                ) { writer, reader, _ ->
                    writer.write("SIGNAL NEWNYM\r\n")
                    writer.flush()
                    reader.readLine().orEmpty().startsWith("250")
                } == true
                if (success) Log.i(TAG, "SIGNAL NEWNYM accepted")
                success
            } catch (e: Exception) {
                Log.w(TAG, "SIGNAL NEWNYM unavailable (Orbot SOCKS-only): ${e.message}")
                false
            }
        }

    /** Orbot: SOCKS on 9050 is enough; control may be absent. */
    private suspend fun checkExternalOrbotReady(): PortStatus {
        val socks = checkSocks(timeoutMs = 2_000)
        if (!socks.reachable) {
            return socks.copy(
                message = "Orbot SOCKS not on 9050 — open Orbot, start Tor/VPN, " +
                    "enable “Open Proxy on Localhost” (SocksPort 9050), then retry.",
            )
        }
        val control = checkControl(timeoutMs = 1_200)
        return PortStatus(
            true,
            TakerAppConfig.DEFAULT_SOCKS_HOST,
            TakerAppConfig.DEFAULT_SOCKS_PORT,
            if (control.reachable) {
                "Orbot Tor ready (SOCKS + control)"
            } else {
                "Orbot Tor ready (SOCKS)"
            },
            controlPassword = control.controlPassword,
        )
    }

    private suspend fun waitUntilSocksReady(timeoutMs: Long): PortStatus {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = PortStatus(
            false,
            TakerAppConfig.DEFAULT_SOCKS_HOST,
            TakerAppConfig.DEFAULT_SOCKS_PORT,
            "Waiting for Orbot SOCKS on 9050…",
        )
        while (System.currentTimeMillis() < deadline) {
            last = checkExternalOrbotReady()
            if (last.reachable) return last
            val elapsed = timeoutMs - (deadline - System.currentTimeMillis())
            last = last.copy(
                message = "Waiting for Orbot… ${(elapsed / 1000).coerceAtLeast(0)}s / ${timeoutMs / 1000}s",
            )
            delay(POLL_MS)
        }
        return last.copy(
            reachable = false,
            message = "Orbot SOCKS not ready on 9050. Open Orbot, start Tor, enable local SocksPort, then retry.",
        )
    }

    private suspend fun checkSocksHandshake(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): PortStatus = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val out = DataOutputStream(socket.getOutputStream())
                val inp = DataInputStream(socket.getInputStream())
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                val ver = inp.read()
                val method = inp.read()
                if (ver != 0x05 || method == 0xFF || method < 0) {
                    error("SOCKS5 handshake rejected (ver=$ver method=$method)")
                }
            }
            PortStatus(true, host, port, "Orbot SOCKS reachable at $host:$port")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PortStatus(
                false,
                host,
                port,
                "Orbot SOCKS not reachable at $host:$port (${e.message})",
            )
        }
    }

    private suspend fun checkControlAuth(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): PortStatus = withContext(Dispatchers.IO) {
        val passwords = listOf("", TakerAppConfig.DEMO_TOR_PASSWORD)
        try {
            val accepted = withAuthenticatedControl(host, port, timeoutMs, passwords) { _, _, password ->
                password
            }
            if (accepted == null) {
                error("control AUTHENTICATE failed")
            }
            PortStatus(
                true,
                host,
                port,
                "Tor control reachable at $host:$port",
                controlPassword = accepted,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PortStatus(
                false,
                host,
                port,
                "Tor control not reachable at $host:$port (${e.message})",
            )
        }
    }

    private fun <T> withAuthenticatedControl(
        host: String,
        port: Int,
        timeoutMs: Int,
        passwords: List<String>,
        block: (OutputStreamWriter, BufferedReader, String) -> T,
    ): T? {
        for (password in passwords) {
            try {
                Socket().use { socket ->
                    socket.soTimeout = timeoutMs
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    val writer = OutputStreamWriter(socket.getOutputStream())
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    writer.write("AUTHENTICATE \"$password\"\r\n")
                    writer.flush()
                    val line = reader.readLine().orEmpty()
                    if (line.startsWith("250")) {
                        return block(writer, reader, password)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }
}
