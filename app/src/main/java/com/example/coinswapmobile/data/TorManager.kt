package com.example.coinswapmobile.data

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.torproject.jni.TorService
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Starts an in-app Tor daemon and probes SOCKS/control ports.
 *
 * UniFFI [org.coinswap.Taker.init] expects SOCKS `127.0.0.1:9050` and a TCP
 * control port (default 9051) with empty-password AUTHENTICATE. Electrum over
 * `.onion` also uses that SOCKS proxy.
 */
object TorManager {

    private const val TAG = "TorManager"
    private const val BOOTSTRAP_TIMEOUT_MS = 90_000L
    private const val POLL_MS = 1_000L

    data class PortStatus(
        val reachable: Boolean,
        val host: String,
        val port: Int,
        val message: String,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()

    fun startInBackground(context: Context) {
        val app = context.applicationContext
        scope.launch {
            ensureRunning(app)
        }
    }

    /**
     * Start embedded Tor if needed and wait until SOCKS + control are usable.
     * If SOCKS is already open (adb reverse to the PC Tor that hosts the makers),
     * do not start the in-app daemon — it would steal 9050 and drop onion circuits.
     */
    suspend fun ensureRunning(
        context: Context,
        timeoutMs: Long = BOOTSTRAP_TIMEOUT_MS,
    ): PortStatus = startMutex.withLock {
        val socks = checkSocks(timeoutMs = 1_200)
        if (socks.reachable) {
            return@withLock PortStatus(
                true,
                TakerAppConfig.DEFAULT_SOCKS_HOST,
                TakerAppConfig.DEFAULT_SOCKS_PORT,
                "Tor SOCKS ready at ${TakerAppConfig.DEFAULT_SOCKS_HOST}:${TakerAppConfig.DEFAULT_SOCKS_PORT}",
            )
        }

        val app = context.applicationContext
        writeTorrc(app)
        startEmbeddedTor(app)
        waitUntilReady(timeoutMs)
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

    private suspend fun currentReadyStatus(): PortStatus {
        val socks = checkSocks(timeoutMs = 1_200)
        if (!socks.reachable) return socks
        val control = checkControl(timeoutMs = 1_200)
        return if (control.reachable) {
            PortStatus(
                true,
                TakerAppConfig.DEFAULT_SOCKS_HOST,
                TakerAppConfig.DEFAULT_SOCKS_PORT,
                "Tor ready at ${TakerAppConfig.DEFAULT_SOCKS_HOST}:${TakerAppConfig.DEFAULT_SOCKS_PORT}",
            )
        } else {
            control
        }
    }

    private fun writeTorrc(context: Context) {
        val torrc = TorService.getTorrc(context)
        torrc.parentFile?.mkdirs()
        torrc.writeText(
            """
            SOCKSPort ${TakerAppConfig.DEFAULT_SOCKS_HOST}:${TakerAppConfig.DEFAULT_SOCKS_PORT}
            ControlPort ${TakerAppConfig.DEFAULT_SOCKS_HOST}:${TakerAppConfig.DEFAULT_TOR_CONTROL}
            CookieAuthentication 0
            """.trimIndent() + "\n",
        )
    }

    private fun startEmbeddedTor(context: Context) {
        val intent = Intent(context, TorService::class.java).apply {
            action = TorService.ACTION_START
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start TorService", e)
        }
    }

    private suspend fun waitUntilReady(timeoutMs: Long): PortStatus {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = PortStatus(
            false,
            TakerAppConfig.DEFAULT_SOCKS_HOST,
            TakerAppConfig.DEFAULT_SOCKS_PORT,
            "Starting Tor…",
        )
        while (System.currentTimeMillis() < deadline) {
            last = currentReadyStatus()
            if (last.reachable) return last
            delay(POLL_MS)
        }
        return last.copy(
            reachable = false,
            message = "Tor did not become ready in time. ${last.message}",
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

    private suspend fun checkControlAuth(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): PortStatus = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val writer = OutputStreamWriter(socket.getOutputStream())
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val passwords = listOf("", TakerAppConfig.DEMO_TOR_PASSWORD)
                var last = ""
                for (password in passwords) {
                    writer.write("AUTHENTICATE \"$password\"\r\n")
                    writer.flush()
                    last = reader.readLine().orEmpty()
                    if (last.startsWith("250")) break
                }
                if (!last.startsWith("250")) {
                    error("control AUTHENTICATE failed: $last")
                }
            }
            PortStatus(true, host, port, "Tor control reachable at $host:$port")
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
}
