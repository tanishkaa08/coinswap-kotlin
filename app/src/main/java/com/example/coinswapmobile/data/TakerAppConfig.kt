package com.example.coinswapmobile.data

/**
 * Connection config for Taker.init: RPC, ZMQ, Tor, and wallet.
 * Persisted locally and mapped to UniFFI RpcConfig.
 */
data class TakerAppConfig(
    val rpcHost: String = DEFAULT_RPC_HOST,
    val rpcPort: Int = DEFAULT_RPC_PORT,
    val rpcUsername: String = DEFAULT_RPC_USER,
    val rpcPassword: String = DEFAULT_RPC_PASSWORD,
    val zmqHost: String = DEFAULT_ZMQ_HOST,
    val zmqPort: Int = DEFAULT_ZMQ_PORT,
    val torControlPort: Int = DEFAULT_TOR_CONTROL,
    val torSocksHost: String = DEFAULT_SOCKS_HOST,
    val torSocksPort: Int = DEFAULT_SOCKS_PORT,
    val torAuthPassword: String = "",
    val walletName: String = DEFAULT_WALLET_NAME,
    val walletPassword: String = "",
    val protocol: String = DEFAULT_PROTOCOL,
) {
    val rpcUrl: String get() = "http://$rpcHost:$rpcPort"
    val zmqAddr: String get() = "tcp://$zmqHost:$zmqPort"

    companion object {
        const val DEFAULT_RPC_HOST = "127.0.0.1"
        const val DEFAULT_RPC_PORT = 18442
        const val DEFAULT_RPC_USER = "user"
        const val DEFAULT_RPC_PASSWORD = "password"
        const val DEFAULT_ZMQ_HOST = "127.0.0.1"
        const val DEFAULT_ZMQ_PORT = 28332
        const val DEFAULT_TOR_CONTROL = 9051
        const val DEFAULT_SOCKS_HOST = "127.0.0.1"
        const val DEFAULT_SOCKS_PORT = 9050
        const val DEFAULT_WALLET_NAME = "taker-wallet"
        const val DEFAULT_PROTOCOL = "Legacy"

        fun remoteHost(serverHost: String, walletPassword: String = "") = TakerAppConfig(
            rpcHost = serverHost.trim(),
            zmqHost = serverHost.trim(),
            walletPassword = walletPassword,
        )
    }
}
