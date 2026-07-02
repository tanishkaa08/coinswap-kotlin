package com.example.coinswapmobile.data

import android.content.Context

/**
 * Per-user session state stored in SharedPreferences.
 * All screens read Electrum URL / wallet name from here — no hardcoded URLs elsewhere.
 */
class UserSession(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyPrefs(context)
    }

    val isLoggedIn: Boolean get() = prefs.getBoolean(KEY_LOGGED_IN, false)

    /** Electrum server the user connected with (or the default on first launch). */
    val electrumUrl: String
        get() = prefs.getString(KEY_ELECTRUM_URL, DEFAULT_ELECTRUM_URL) ?: DEFAULT_ELECTRUM_URL

    val walletName: String
        get() = prefs.getString(KEY_WALLET_NAME, DEFAULT_WALLET_NAME) ?: DEFAULT_WALLET_NAME

    fun saveSession(electrumUrl: String, walletName: String) {
        prefs.edit()
            .putString(KEY_ELECTRUM_URL, electrumUrl.trim())
            .putString(KEY_WALLET_NAME, walletName.trim().ifBlank { DEFAULT_WALLET_NAME })
            .putBoolean(KEY_LOGGED_IN, true)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, false)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "coinswap_user_session"

        private const val KEY_ELECTRUM_URL = "electrum_url"
        private const val KEY_WALLET_NAME = "wallet_name"
        private const val KEY_LOGGED_IN = "logged_in"

        /** Default public testnet Electrum server — only default in the whole app. */
        const val DEFAULT_ELECTRUM_URL = "ssl://electrum.blockstream.info:60002"
        const val DEFAULT_WALLET_NAME = "taker-wallet"

        fun isLoggedIn(context: Context): Boolean = UserSession(context).isLoggedIn

        fun clearSession(context: Context) = UserSession(context).clearSession()

        private fun migrateLegacyPrefs(context: Context) {
            val legacy = context.applicationContext
                .getSharedPreferences("coinswap_login_prefs", Context.MODE_PRIVATE)
            if (!legacy.contains("backend_url") && !legacy.contains("logged_in")) return
            val newPrefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (newPrefs.contains(KEY_ELECTRUM_URL)) return
            val legacyUrl = legacy.getString("backend_url", null)
            val url = when {
                legacyUrl == null -> DEFAULT_ELECTRUM_URL
                legacyUrl.startsWith("ssl://") || legacyUrl.startsWith("tcp://") -> legacyUrl
                else -> DEFAULT_ELECTRUM_URL
            }
            newPrefs.edit()
                .putString(KEY_ELECTRUM_URL, url)
                .putString(KEY_WALLET_NAME, legacy.getString("wallet_name", DEFAULT_WALLET_NAME))
                .putBoolean(KEY_LOGGED_IN, legacy.getBoolean("logged_in", false))
                .apply()
        }
    }
}
