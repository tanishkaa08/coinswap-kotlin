package com.example.coinswapmobile.data

import android.content.Context
import android.system.Os
import java.io.File

/**
 * Rust coinswap uses `dirs::home_dir()` internally (e.g. [get_taker_dir] in TakerConfig::new).
 * Android has no HOME by default, which causes a Rust panic at login. Set HOME before any FFI call.
 */
object FfiEnv {

    fun takerDataDir(context: Context): String {
        val dir = File(context.filesDir, "taker")
        if (!dir.exists() && !dir.mkdirs()) {
            error("Failed to create taker data directory: ${dir.absolutePath}")
        }
        if (!dir.isDirectory) {
            error("Taker data path exists but is not a directory: ${dir.absolutePath}")
        }
        ensureHome(dir.absolutePath)
        return dir.absolutePath
    }

    /** True when `{dataDir}/wallets/{walletName}` already exists (returning user). */
    fun walletExists(dataDir: String, walletName: String): Boolean {
        if (walletName.isBlank()) return false
        val file = File(File(dataDir, "wallets"), walletName)
        return file.isFile || file.isDirectory
    }

    fun ensureHome(homeDir: String) {
        try {
            Os.setenv("HOME", homeDir, true)
        } catch (_: Exception) {
            System.setProperty("user.home", homeDir)
        }
    }
}
