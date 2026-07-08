package com.example.coinswapmobile.data

import android.content.Context
import org.coinswap.RpcConfig

/** Persists connection config: RPC, ZMQ, Tor, and wallet. */
class UserSession(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isLoggedIn: Boolean get() = prefs.getBoolean(KEY_LOGGED_IN, false)

    val config: TakerAppConfig
        get() = TakerAppConfig(
            rpcHost = prefs.getString(KEY_RPC_HOST, TakerAppConfig.DEFAULT_RPC_HOST)
                ?: TakerAppConfig.DEFAULT_RPC_HOST,
            rpcPort = prefs.getInt(KEY_RPC_PORT, TakerAppConfig.DEFAULT_RPC_PORT),
            rpcUsername = prefs.getString(KEY_RPC_USER, TakerAppConfig.DEFAULT_RPC_USER)
                ?: TakerAppConfig.DEFAULT_RPC_USER,
            rpcPassword = prefs.getString(KEY_RPC_PASSWORD, TakerAppConfig.DEFAULT_RPC_PASSWORD)
                ?: TakerAppConfig.DEFAULT_RPC_PASSWORD,
            zmqHost = prefs.getString(KEY_ZMQ_HOST, TakerAppConfig.DEFAULT_ZMQ_HOST)
                ?: TakerAppConfig.DEFAULT_ZMQ_HOST,
            zmqPort = prefs.getInt(KEY_ZMQ_PORT, TakerAppConfig.DEFAULT_ZMQ_PORT),
            torControlPort = prefs.getInt(KEY_TOR_CONTROL, TakerAppConfig.DEFAULT_TOR_CONTROL),
            torSocksHost = prefs.getString(KEY_SOCKS_HOST, TakerAppConfig.DEFAULT_SOCKS_HOST)
                ?: TakerAppConfig.DEFAULT_SOCKS_HOST,
            torSocksPort = prefs.getInt(KEY_SOCKS_PORT, TakerAppConfig.DEFAULT_SOCKS_PORT),
            torAuthPassword = prefs.getString(KEY_TOR_AUTH, "") ?: "",
            walletName = prefs.getString(KEY_WALLET_NAME, TakerAppConfig.DEFAULT_WALLET_NAME)
                ?: TakerAppConfig.DEFAULT_WALLET_NAME,
            walletPassword = prefs.getString(KEY_WALLET_PASSWORD, "") ?: "",
            protocol = prefs.getString(KEY_PROTOCOL, TakerAppConfig.DEFAULT_PROTOCOL)
                ?: TakerAppConfig.DEFAULT_PROTOCOL,
        )

    val walletName: String get() = config.walletName
    val socksHost: String get() = config.torSocksHost
    val socksPort: Int get() = config.torSocksPort

    fun saveConfig(cfg: TakerAppConfig, markLoggedIn: Boolean = true) {
        prefs.edit()
            .putString(KEY_RPC_HOST, cfg.rpcHost.trim())
            .putInt(KEY_RPC_PORT, cfg.rpcPort)
            .putString(KEY_RPC_USER, cfg.rpcUsername)
            .putString(KEY_RPC_PASSWORD, cfg.rpcPassword)
            .putString(KEY_ZMQ_HOST, cfg.zmqHost.trim())
            .putInt(KEY_ZMQ_PORT, cfg.zmqPort)
            .putInt(KEY_TOR_CONTROL, cfg.torControlPort)
            .putString(KEY_SOCKS_HOST, cfg.torSocksHost.trim())
            .putInt(KEY_SOCKS_PORT, cfg.torSocksPort)
            .putString(KEY_TOR_AUTH, cfg.torAuthPassword)
            .putString(KEY_WALLET_NAME, cfg.walletName.trim().ifBlank { TakerAppConfig.DEFAULT_WALLET_NAME })
            .putString(KEY_WALLET_PASSWORD, cfg.walletPassword)
            .putString(KEY_PROTOCOL, cfg.protocol)
            .putBoolean(KEY_LOGGED_IN, markLoggedIn)
            .apply()
    }

    fun toRpcConfig(): RpcConfig {
        val c = config
        return RpcConfig(
            url = c.rpcUrl,
            username = c.rpcUsername,
            password = c.rpcPassword,
            walletName = c.walletName,
        )
    }

    fun clearSession() {
        prefs.edit().putBoolean(KEY_LOGGED_IN, false).apply()
    }

    companion object {
        private const val PREFS_NAME = "coinswap_taker_config"

        private const val KEY_RPC_HOST = "rpc_host"
        private const val KEY_RPC_PORT = "rpc_port"
        private const val KEY_RPC_USER = "rpc_user"
        private const val KEY_RPC_PASSWORD = "rpc_password"
        private const val KEY_ZMQ_HOST = "zmq_host"
        private const val KEY_ZMQ_PORT = "zmq_port"
        private const val KEY_TOR_CONTROL = "tor_control"
        private const val KEY_SOCKS_HOST = "socks_host"
        private const val KEY_SOCKS_PORT = "socks_port"
        private const val KEY_TOR_AUTH = "tor_auth"
        private const val KEY_WALLET_NAME = "wallet_name"
        private const val KEY_WALLET_PASSWORD = "wallet_password"
        private const val KEY_PROTOCOL = "protocol"
        private const val KEY_LOGGED_IN = "logged_in"

        fun isLoggedIn(context: Context): Boolean = UserSession(context).isLoggedIn

        fun clearSession(context: Context) = UserSession(context).clearSession()
    }
}
