package com.example.coinswapmobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.coinswap.BackendConfig

/** Persists connection config: Electrum, Tor, and wallet. Secrets use EncryptedSharedPreferences. */
class UserSession(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secrets: SharedPreferences by lazy { openSecretsPrefs(appContext) }

    val isLoggedIn: Boolean get() = prefs.getBoolean(KEY_LOGGED_IN, false)

    val config: TakerAppConfig
        get() {
            migrateSecretsIfNeeded()
            return TakerAppConfig(
                electrumUrl = storedElectrumUrl(),
                torControlPort = prefs.getInt(KEY_TOR_CONTROL, TakerAppConfig.DEFAULT_TOR_CONTROL),
                torSocksHost = TakerAppConfig.DEFAULT_SOCKS_HOST,
                torSocksPort = TakerAppConfig.DEFAULT_SOCKS_PORT,
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

    var displayCurrency: DisplayCurrency
        get() = DisplayCurrency.fromStored(prefs.getString(KEY_CURRENCY, DisplayCurrency.SATS.name))
        set(value) {
            prefs.edit().putString(KEY_CURRENCY, value.name).apply()
        }

    fun saveConfig(cfg: TakerAppConfig, markLoggedIn: Boolean = true) {
        migrateSecretsIfNeeded()
        prefs.edit()
            .putString(KEY_ELECTRUM_URL, cfg.electrumUrl.trim())
            .putInt(KEY_TOR_CONTROL, cfg.torControlPort)
            .putString(KEY_SOCKS_HOST, TakerAppConfig.DEFAULT_SOCKS_HOST)
            .putInt(KEY_SOCKS_PORT, TakerAppConfig.DEFAULT_SOCKS_PORT)
            .putString(KEY_WALLET_NAME, cfg.walletName.trim().ifBlank { TakerAppConfig.DEFAULT_WALLET_NAME })
            .putString(KEY_PROTOCOL, cfg.protocol)
            .putBoolean(KEY_LOGGED_IN, markLoggedIn)
            .remove(KEY_RPC_HOST)
            .remove(KEY_RPC_PORT)
            .remove(KEY_RPC_USER)
            .remove(KEY_RPC_PASSWORD)
            .remove(KEY_ZMQ_HOST)
            .remove(KEY_ZMQ_PORT)
            .remove(KEY_TOR_AUTH)
            .remove(KEY_WALLET_PASSWORD)
            .commit()
        secrets.edit()
            .remove(KEY_RPC_PASSWORD)
            .putString(KEY_TOR_AUTH, cfg.torAuthPassword)
            .putString(KEY_WALLET_PASSWORD, cfg.walletPassword)
            .commit()
    }

    fun toBackendConfig(): BackendConfig = config.toBackendConfig()

    fun clearSession() {
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, false)
            .remove(KEY_ELECTRUM_URL)
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

    private fun storedElectrumUrl(): String {
        val saved = prefs.getString(KEY_ELECTRUM_URL, null)?.trim().orEmpty()
        if (saved.isNotEmpty()) return saved
        val legacyHost = prefs.getString(KEY_RPC_HOST, null)?.trim().orEmpty()
        if (legacyHost.isNotEmpty()) {
            return TakerAppConfig.electrumUrlForHost(legacyHost)
        }
        return TakerAppConfig.DEFAULT_ELECTRUM_URL
    }

    /** One-time move of plaintext secrets from prefs → EncryptedSharedPreferences. */
    private fun migrateSecretsIfNeeded() {
        val legacyTor = prefs.getString(KEY_TOR_AUTH, null)
        val legacyWallet = prefs.getString(KEY_WALLET_PASSWORD, null)
        if (legacyTor == null && legacyWallet == null) return

        val editor = secrets.edit()
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

        private const val KEY_ELECTRUM_URL = "electrum_url"
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
        private const val KEY_CURRENCY = "display_currency"
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
