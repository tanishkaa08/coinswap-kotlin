package com.example.coinswapmobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.coinswap.RpcConfig

/** Persists connection config: RPC, ZMQ, Tor, and wallet. Secrets use EncryptedSharedPreferences. */
class UserSession(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secrets: SharedPreferences by lazy { openSecretsPrefs(appContext) }

    val isLoggedIn: Boolean get() = prefs.getBoolean(KEY_LOGGED_IN, false)

    val config: TakerAppConfig
        get() {
            migrateSecretsIfNeeded()
            return TakerAppConfig(
                rpcHost = prefs.getString(KEY_RPC_HOST, TakerAppConfig.DEFAULT_RPC_HOST)
                    ?: TakerAppConfig.DEFAULT_RPC_HOST,
                rpcPort = prefs.getInt(KEY_RPC_PORT, TakerAppConfig.DEFAULT_RPC_PORT),
                rpcUsername = prefs.getString(KEY_RPC_USER, TakerAppConfig.DEFAULT_RPC_USER)
                    ?: TakerAppConfig.DEFAULT_RPC_USER,
                rpcPassword = secrets.getString(KEY_RPC_PASSWORD, TakerAppConfig.DEFAULT_RPC_PASSWORD)
                    ?: TakerAppConfig.DEFAULT_RPC_PASSWORD,
                zmqHost = prefs.getString(KEY_ZMQ_HOST, TakerAppConfig.DEFAULT_ZMQ_HOST)
                    ?: TakerAppConfig.DEFAULT_ZMQ_HOST,
                zmqPort = prefs.getInt(KEY_ZMQ_PORT, TakerAppConfig.DEFAULT_ZMQ_PORT),
                torControlPort = prefs.getInt(KEY_TOR_CONTROL, TakerAppConfig.DEFAULT_TOR_CONTROL),
                torSocksHost = prefs.getString(KEY_SOCKS_HOST, TakerAppConfig.DEFAULT_SOCKS_HOST)
                    ?: TakerAppConfig.DEFAULT_SOCKS_HOST,
                torSocksPort = prefs.getInt(KEY_SOCKS_PORT, TakerAppConfig.DEFAULT_SOCKS_PORT),
                torAuthPassword = secrets.getString(KEY_TOR_AUTH, "") ?: "",
                walletName = prefs.getString(KEY_WALLET_NAME, TakerAppConfig.DEFAULT_WALLET_NAME)
                    ?: TakerAppConfig.DEFAULT_WALLET_NAME,
                walletPassword = secrets.getString(KEY_WALLET_PASSWORD, "") ?: "",
                protocol = prefs.getString(KEY_PROTOCOL, TakerAppConfig.DEFAULT_PROTOCOL)
                    ?: TakerAppConfig.DEFAULT_PROTOCOL,
            )
        }

    val walletName: String get() = config.walletName
    val socksHost: String get() = config.torSocksHost
    val socksPort: Int get() = config.torSocksPort

    fun saveConfig(cfg: TakerAppConfig, markLoggedIn: Boolean = true) {
        migrateSecretsIfNeeded()
        prefs.edit()
            .putString(KEY_RPC_HOST, cfg.rpcHost.trim())
            .putInt(KEY_RPC_PORT, cfg.rpcPort)
            .putString(KEY_RPC_USER, cfg.rpcUsername)
            .putString(KEY_ZMQ_HOST, cfg.zmqHost.trim())
            .putInt(KEY_ZMQ_PORT, cfg.zmqPort)
            .putInt(KEY_TOR_CONTROL, cfg.torControlPort)
            .putString(KEY_SOCKS_HOST, cfg.torSocksHost.trim())
            .putInt(KEY_SOCKS_PORT, cfg.torSocksPort)
            .putString(KEY_WALLET_NAME, cfg.walletName.trim().ifBlank { TakerAppConfig.DEFAULT_WALLET_NAME })
            .putString(KEY_PROTOCOL, cfg.protocol)
            .putBoolean(KEY_LOGGED_IN, markLoggedIn)
            // Strip any legacy plaintext secrets from the unencrypted prefs file.
            .remove(KEY_RPC_PASSWORD)
            .remove(KEY_TOR_AUTH)
            .remove(KEY_WALLET_PASSWORD)
            .apply()
        secrets.edit()
            .putString(KEY_RPC_PASSWORD, cfg.rpcPassword)
            .putString(KEY_TOR_AUTH, cfg.torAuthPassword)
            .putString(KEY_WALLET_PASSWORD, cfg.walletPassword)
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
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, false)
            .remove(KEY_RPC_HOST)
            .remove(KEY_RPC_PORT)
            .remove(KEY_RPC_USER)
            .remove(KEY_RPC_PASSWORD)
            .remove(KEY_ZMQ_HOST)
            .remove(KEY_ZMQ_PORT)
            .remove(KEY_TOR_CONTROL)
            .remove(KEY_SOCKS_HOST)
            .remove(KEY_SOCKS_PORT)
            .remove(KEY_TOR_AUTH)
            .remove(KEY_WALLET_NAME)
            .remove(KEY_WALLET_PASSWORD)
            .remove(KEY_PROTOCOL)
            .apply()
        secrets.edit()
            .remove(KEY_RPC_PASSWORD)
            .remove(KEY_TOR_AUTH)
            .remove(KEY_WALLET_PASSWORD)
            .apply()
    }

    /** One-time move of plaintext secrets from prefs → EncryptedSharedPreferences. */
    private fun migrateSecretsIfNeeded() {
        val legacyRpc = prefs.getString(KEY_RPC_PASSWORD, null)
        val legacyTor = prefs.getString(KEY_TOR_AUTH, null)
        val legacyWallet = prefs.getString(KEY_WALLET_PASSWORD, null)
        if (legacyRpc == null && legacyTor == null && legacyWallet == null) return

        val editor = secrets.edit()
        if (legacyRpc != null && !secrets.contains(KEY_RPC_PASSWORD)) {
            editor.putString(KEY_RPC_PASSWORD, legacyRpc)
        }
        if (legacyTor != null && !secrets.contains(KEY_TOR_AUTH)) {
            editor.putString(KEY_TOR_AUTH, legacyTor)
        }
        if (legacyWallet != null && !secrets.contains(KEY_WALLET_PASSWORD)) {
            editor.putString(KEY_WALLET_PASSWORD, legacyWallet)
        }
        editor.apply()
        prefs.edit()
            .remove(KEY_RPC_PASSWORD)
            .remove(KEY_TOR_AUTH)
            .remove(KEY_WALLET_PASSWORD)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "coinswap_taker_config"
        private const val SECRETS_PREFS_NAME = "coinswap_taker_secrets"

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

        private fun openSecretsPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                SECRETS_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        fun isLoggedIn(context: Context): Boolean = UserSession(context).isLoggedIn

        fun clearSession(context: Context) = UserSession(context).clearSession()
    }
}
