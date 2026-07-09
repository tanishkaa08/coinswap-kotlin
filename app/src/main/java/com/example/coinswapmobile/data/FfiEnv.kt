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
        val dir = File(context.filesDir, "taker").apply { mkdirs() }
        ensureHome(dir.absolutePath)
        return dir.absolutePath
    }

    fun ensureHome(homeDir: String) {
        try {
            Os.setenv("HOME", homeDir, true)
        } catch (_: Exception) {
            System.setProperty("user.home", homeDir)
        }
    }
}
