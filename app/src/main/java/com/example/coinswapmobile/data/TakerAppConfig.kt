package com.example.coinswapmobile.data

import org.coinswap.BackendConfig

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
     * SOCKS5 for the Electrum client. Onion servers need it; LAN electrs does not.
     * Maker discovery still uses the in-app Tor SOCKS listener either way.
     */
    val electrumSocks5: String?
        get() = if (electrumUrl.contains(".onion", ignoreCase = true)) {
            "$torSocksHost:$torSocksPort"
        } else {
            null
        }

    companion object {
        const val DEFAULT_ELECTRUM_HOST = "127.0.0.1"
        const val DEFAULT_ELECTRUM_PORT = 50001
        const val DEFAULT_ELECTRUM_URL = "tcp://$DEFAULT_ELECTRUM_HOST:$DEFAULT_ELECTRUM_PORT"
        /** Required by UniFFI Taker.init; ignored when backend is Electrum. */
        const val DUMMY_ZMQ_ADDR = "tcp://127.0.0.1:28332"
        const val DEFAULT_TOR_CONTROL = 9051
        const val DEFAULT_SOCKS_HOST = "127.0.0.1"
        const val DEFAULT_SOCKS_PORT = 9050
        /** Control password for the coinswap-ffi docker Tor (`torrc.generated`). */
        const val DEMO_TOR_PASSWORD = "coinswap"
        const val DEFAULT_WALLET_NAME = "taker-wallet"
        const val DEFAULT_PROTOCOL = "Legacy"

        fun electrumUrlForHost(
            serverHost: String,
            port: Int = DEFAULT_ELECTRUM_PORT,
        ): String {
            val host = serverHost.trim()
            if (host.startsWith("tcp://") || host.startsWith("ssl://")) return host
            return "tcp://$host:$port"
        }

        fun remoteHost(serverHost: String, walletPassword: String = "") = TakerAppConfig(
            electrumUrl = electrumUrlForHost(serverHost),
            walletPassword = walletPassword,
        )
    }
}

/** TCP probe so login fails fast with the real host:port instead of a 4-attempt FFI timeout. */
fun TakerAppConfig.probeElectrum(timeoutMs: Int = 4_000): String? {
    val raw = electrumUrl.removePrefix("tcp://").removePrefix("ssl://")
    val host = raw.substringBeforeLast(':')
    val port = raw.substringAfterLast(':').toIntOrNull()
        ?: return "Invalid Electrum URL $electrumUrl"
    return try {
        java.net.Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
        }
        null
    } catch (e: Exception) {
        val why = e.message ?: e.javaClass.simpleName
        "Cannot reach Electrum at $electrumUrl ($why)"
    }
}

/** Prefer USB adb-reverse localhost, then the configured demo host. */
fun pickReachableElectrumUrl(preferred: String): Pair<String, String?> {
    val candidates = linkedSetOf(
        TakerAppConfig.DEFAULT_ELECTRUM_URL,
        preferred,
    ).filter { it.isNotBlank() }
    val failures = mutableListOf<String>()
    for (url in candidates) {
        val err = TakerAppConfig(electrumUrl = url).probeElectrum(2_500)
        if (err == null) return url to null
        failures += err
    }
    return preferred to failures.joinToString(" | ")
}

fun TakerAppConfig.toBackendConfig(): BackendConfig =
    BackendConfig(
        kind = "electrum",
        url = electrumUrl,
        username = null,
        password = null,
        walletName = null,
        zmqAddr = null,
        socks5 = electrumSocks5,
        timeout = null,
        pollIntervalSecs = null,
        maxRetries = null,
    )
