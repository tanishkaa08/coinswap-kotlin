package com.example.coinswapmobile.data

import org.coinswap.BackendConfig
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Connection config for Taker.init: Electrum, Tor, and wallet.
 * Persisted locally and mapped to UniFFI [org.coinswap.BackendConfig].
 */
data class TakerAppConfig(
    val electrumUrl: String = DEFAULT_ELECTRUM_URL,
    val torControlPort: Int = DEFAULT_TOR_CONTROL,
    val torSocksHost: String = DEFAULT_SOCKS_HOST,
    val torSocksPort: Int = DEFAULT_SOCKS_PORT,
    val torAuthPassword: String = "",
    val walletName: String = DEFAULT_WALLET_NAME,
    val walletPassword: String = "",
    val protocol: String = DEFAULT_PROTOCOL,
) {
    /**
     * SOCKS5 for Electrum only when the URL is a .onion host.
     * Clearnet Electrum (Neo's signet VPS) must connect directly — routing TLS
     * through Orbot caused `WouldBlock` / unreachable connect failures.
     * Makers still always use Orbot SOCKS via the FFI Tor stack.
     */
    val electrumSocks5: String?
        get() = if (isOnionElectrum(electrumUrl)) "$torSocksHost:$torSocksPort" else null

    val torSocksEndpoint: String
        get() = "$torSocksHost:$torSocksPort"

    companion object {
        const val DEFAULT_ELECTRUM_HOST = "127.0.0.1"
        const val DEFAULT_ELECTRUM_PORT = 50001
        /** USB-tethered regtest electrs. Only used when `-PelectrumUrl` points at localhost. */
        const val DEFAULT_ELECTRUM_URL = "tcp://$DEFAULT_ELECTRUM_HOST:$DEFAULT_ELECTRUM_PORT"
        /**
         * Public signet Electrum over TLS (mentor VPS). The wallet derives its
         * network from the server's genesis hash, so this alone puts the phone
         * on signet with no PC-side bitcoind/electrs/mining loop.
         */
        const val SIGNET_ELECTRUM_URL = "ssl://electrum.citadelfoss.xyz:50002"
        /** Plaintext fallback if TLS is blocked on the phone's network. */
        const val SIGNET_ELECTRUM_TCP_URL = "tcp://electrum.citadelfoss.xyz:50001"
        /** Required by UniFFI Taker.init; ignored when backend is Electrum. */
        const val DUMMY_ZMQ_ADDR = "tcp://127.0.0.1:28332"
        const val DEFAULT_TOR_CONTROL = 9051
        const val DEFAULT_SOCKS_HOST = "127.0.0.1"
        const val DEFAULT_SOCKS_PORT = 9050
        /** Control password for the coinswap-ffi docker Tor (`torrc.generated`). */
        const val DEMO_TOR_PASSWORD = "coinswap"
        const val DEFAULT_WALLET_NAME = "taker-wallet"
        /** Separate file so the old regtest wallet cannot load against signet. */
        const val SIGNET_WALLET_NAME = "taker-signet"
        const val DEFAULT_PROTOCOL = "Legacy"
        const val DEFAULT_ELECTRUM_TIMEOUT_SECS: UByte = 30u
        const val DEFAULT_ELECTRUM_MAX_RETRIES: UByte = 3u

        fun isLoopbackElectrum(url: String): Boolean {
            val host = hostOf(url) ?: return false
            return host == "127.0.0.1" || host == "localhost" || host == "::1"
        }

        fun isOnionElectrum(url: String): Boolean =
            hostOf(url)?.endsWith(".onion") == true

        fun hostOf(url: String): String? {
            val raw = url.trim()
                .removePrefix("tcp://")
                .removePrefix("ssl://")
                .substringBefore('/')
            if (raw.isBlank()) return null
            // host:port:t / host:port:s
            val colonParts = raw.split(':')
            if (colonParts.size >= 3) {
                val proto = colonParts.last().lowercase()
                if (proto == "t" || proto == "s") {
                    return colonParts.dropLast(2).joinToString(":").lowercase()
                }
            }
            return raw.substringBeforeLast(':').lowercase().takeIf { it.isNotBlank() }
        }

        fun walletNameForElectrum(url: String): String =
            if (isLoopbackElectrum(url)) DEFAULT_WALLET_NAME else SIGNET_WALLET_NAME

        /**
         * Accepts `tcp://`/`ssl://` URLs, Electrum `host:port:t`/`host:port:s`,
         * or a bare `host:port`. Port 50002 defaults to TLS.
         */
        fun electrumUrlForHost(
            serverHost: String,
            port: Int = DEFAULT_ELECTRUM_PORT,
        ): String {
            val host = serverHost.trim()
            if (host.startsWith("tcp://") || host.startsWith("ssl://")) return host

            val colonParts = host.split(':')
            if (colonParts.size >= 3) {
                val proto = colonParts.last().lowercase()
                val parsedPort = colonParts[colonParts.size - 2].toIntOrNull()
                if (parsedPort != null && (proto == "t" || proto == "s")) {
                    val name = colonParts.dropLast(2).joinToString(":")
                    val scheme = if (proto == "s") "ssl" else "tcp"
                    return "$scheme://$name:$parsedPort"
                }
            }

            val lastColon = host.lastIndexOf(':')
            if (lastColon > 0) {
                val name = host.substring(0, lastColon)
                val parsedPort = host.substring(lastColon + 1).toIntOrNull()
                if (parsedPort != null) {
                    val scheme = if (parsedPort == 50002) "ssl" else "tcp"
                    return "$scheme://$name:$parsedPort"
                }
            }

            val scheme = if (port == 50002) "ssl" else "tcp"
            return "$scheme://$host:$port"
        }

        fun remoteHost(serverHost: String, walletPassword: String = "") = TakerAppConfig(
            electrumUrl = electrumUrlForHost(serverHost),
            walletName = walletNameForElectrum(electrumUrlForHost(serverHost)),
            walletPassword = walletPassword,
        )
    }
}

/**
 * Reachability probe before Taker.init.
 * Clearnet: direct TCP. Onion (or explicit SOCKS): SOCKS5 CONNECT with hostname
 * so Orbot VPN DNS still works.
 */
fun TakerAppConfig.probeElectrum(timeoutMs: Int = 4_000): String? {
    val raw = electrumUrl.removePrefix("tcp://").removePrefix("ssl://")
    val host = raw.substringBeforeLast(':')
    val port = raw.substringAfterLast(':').toIntOrNull()
        ?: return "Invalid Electrum URL $electrumUrl"
    val socks = electrumSocks5
    return try {
        if (socks != null) {
            val socksHost = socks.substringBeforeLast(':')
            val socksPort = socks.substringAfterLast(':').toIntOrNull()
                ?: return "Invalid SOCKS endpoint $socks"
            socks5ConnectProbe(socksHost, socksPort, host, port, timeoutMs)
        } else {
            directConnectProbe(host, port, timeoutMs)
        }
        null
    } catch (e: Exception) {
        val why = e.message ?: e.javaClass.simpleName
        if (socks != null) {
            "Cannot reach Electrum at $electrumUrl via $socks ($why)"
        } else {
            "Cannot reach Electrum at $electrumUrl ($why)"
        }
    }
}

private fun directConnectProbe(host: String, port: Int, timeoutMs: Int) {
    Socket().use { socket ->
        socket.soTimeout = timeoutMs
        socket.connect(InetSocketAddress(host, port), timeoutMs)
    }
}

/** SOCKS5 CONNECT to [destHost]:[destPort] through a local Tor proxy. */
private fun socks5ConnectProbe(
    socksHost: String,
    socksPort: Int,
    destHost: String,
    destPort: Int,
    timeoutMs: Int,
) {
    Socket().use { socket ->
        socket.soTimeout = timeoutMs
        socket.connect(InetSocketAddress(socksHost, socksPort), timeoutMs)
        val out = DataOutputStream(socket.getOutputStream())
        val inp = DataInputStream(socket.getInputStream())
        out.write(byteArrayOf(0x05, 0x01, 0x00))
        out.flush()
        val ver = inp.read()
        val method = inp.read()
        if (ver != 0x05 || method != 0x00) {
            error("SOCKS5 handshake failed (ver=$ver method=$method)")
        }
        val hostBytes = destHost.toByteArray(Charsets.UTF_8)
        require(hostBytes.size in 1..255) { "bad dest host length" }
        out.writeByte(0x05)
        out.writeByte(0x01) // CONNECT
        out.writeByte(0x00)
        out.writeByte(0x03) // DOMAINNAME
        out.writeByte(hostBytes.size)
        out.write(hostBytes)
        out.writeShort(destPort)
        out.flush()
        val repVer = inp.read()
        val rep = inp.read()
        val _rsv = inp.read()
        val atyp = inp.read()
        when (atyp) {
            0x01 -> repeat(4) { inp.read() }
            0x03 -> repeat(inp.read().coerceAtLeast(0)) { inp.read() }
            0x04 -> repeat(16) { inp.read() }
            else -> error("SOCKS5 bad atyp=$atyp")
        }
        inp.read()
        inp.read() // port
        if (repVer != 0x05 || rep != 0x00) {
            error("SOCKS5 CONNECT rejected (rep=$rep)")
        }
    }
}

/**
 * Probe the preferred Electrum URL, then the public signet plaintext fallback.
 * Localhost is only tried when the caller explicitly asked for a lab endpoint —
 * otherwise a leftover `adb reverse` would silently pin the phone to regtest.
 */
fun pickReachableElectrumUrl(preferred: String): Pair<String, String?> {
    val candidates = linkedSetOf<String>()
    if (preferred.isNotBlank()) candidates += preferred
    if (!TakerAppConfig.isLoopbackElectrum(preferred)) {
        candidates += TakerAppConfig.SIGNET_ELECTRUM_URL
        candidates += TakerAppConfig.SIGNET_ELECTRUM_TCP_URL
    } else {
        candidates += TakerAppConfig.DEFAULT_ELECTRUM_URL
    }
    val failures = mutableListOf<String>()
    for (url in candidates) {
        val cfg = TakerAppConfig(electrumUrl = url)
        val timeoutMs = 20_000
        val err = cfg.probeElectrum(timeoutMs)
        if (err == null) return url to null
        failures += err
    }
    return preferred to failures.joinToString(" | ")
}

fun TakerAppConfig.toBackendConfig(
    socks5: String? = electrumSocks5,
    timeoutSecs: UByte = TakerAppConfig.DEFAULT_ELECTRUM_TIMEOUT_SECS,
    maxRetries: UByte = TakerAppConfig.DEFAULT_ELECTRUM_MAX_RETRIES,
): BackendConfig =
    BackendConfig(
        kind = "electrum",
        url = electrumUrl,
        username = null,
        password = null,
        walletName = null,
        zmqAddr = null,
        // Preserve explicit null (clearnet). Do not coerce to Orbot SOCKS.
        socks5 = socks5,
        timeout = timeoutSecs,
        pollIntervalSecs = null,
        maxRetries = maxRetries,
    )
