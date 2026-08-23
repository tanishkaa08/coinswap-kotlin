package com.example.coinswapmobile.data

import android.content.Context

/**
 * Maker routing helpers. Does **not** ban or demote makers — connection drops
 * are expected on Tor; the marketplace pool stays fully usable.
 */
object MakerRoutePrefs {
    private const val PREFS = "maker_route"

    /** Wipe any legacy demote / hard-exclude prefs from older builds. */
    fun clearAllBans(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun normalize(raw: String): String? {
        val s = raw.trim().lowercase()
        if (s.isEmpty()) return null
        return s.removePrefix("http://").removePrefix("https://").substringBefore("/")
            .substringBefore(":")
            .takeIf { it.endsWith(".onion") }
    }

    /** Always false — makers are never deactivated by the app. */
    @Suppress("UNUSED_PARAMETER")
    fun isHardExcluded(context: Context, onion: String): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun isHardExcluded(onion: String): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun isExcluded(context: Context, onion: String): Boolean = false

    /**
     * Preferred makers for UniFFI (hops + spares), highest fidelity first.
     * No bans / demotes — every candidate onion may be used.
     */
    @Suppress("UNUSED_PARAMETER")
    fun orderPreferred(
        context: Context,
        onions: List<String>,
        needed: Int,
        fidelityByOnion: Map<String, Double> = emptyMap(),
        includeSoftDemoted: Boolean = true,
    ): List<String> {
        return onions.mapNotNull { normalize(it) }.distinct()
            .sortedByDescending { fidelityByOnion[it] ?: 0.0 }
            .take(needed.coerceAtLeast(0))
    }
}
