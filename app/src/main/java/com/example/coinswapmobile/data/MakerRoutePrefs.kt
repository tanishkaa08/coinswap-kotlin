package com.example.coinswapmobile.data

import android.content.Context

/**
 * Maker routing for swaps.
 *
 * [HARD_EXCLUDE] — never use (any hop / spare). PoF UnexpectedEof after funding.
 * Soft demotes — prefer other makers first, but still allow if needed so the
 * Swap screen does not show "0 online" when Markets has live makers.
 */
object MakerRoutePrefs {
    private const val PREFS = "maker_route"
    private const val KEY_DEMOTED = "demoted_onions"

    /**
     * Never route through these (any hop / spare).
     * Confirmed: drops Tor mid–ProofOfFunding after funding confirms (UnexpectedEof).
     * Keep this set small — 2-hop swaps need ≥2 other online makers.
     */
    val HARD_EXCLUDE: Set<String> = setOf(
        "fu2r75in2vvza3qg5utxywpmltnzj3bm6lpebg64nm7ugbkd7xmn5nad.onion",
    )

    /** Soft-prefer away from these (still allowed as last-resort spares). */
    val SEED_SOFT_DEMOTE: Set<String> = setOf(
        "4zqrwdgpvsug7ktsbqroxduhpysqamqcijgtc33ytah2ikocvze4wqad.onion",
        "66xvzrpceg5nvof5oucpxscni5mtebsfa277j4nxuaee6uvkluapixyd.onion",
    )

    @Deprecated("Use HARD_EXCLUDE", ReplaceWith("HARD_EXCLUDE"))
    val HARD_EXCLUDE_FIRST_HOP: Set<String> = HARD_EXCLUDE

    fun demotedOnions(context: Context): Set<String> =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_DEMOTED, emptySet())
            ?.toSet()
            .orEmpty()

    fun softDemotedOnions(context: Context): Set<String> =
        demotedOnions(context) - HARD_EXCLUDE

    fun demote(context: Context, onionOrAddress: String) {
        val key = normalize(onionOrAddress) ?: return
        if (key in HARD_EXCLUDE) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = demotedOnions(context).toMutableSet().apply { add(key) }
        prefs.edit().putStringSet(KEY_DEMOTED, next).apply()
    }

    fun ensureHardExcludes(context: Context) {
        // Strip hard-excludes from the soft set, and seed known flaky makers as soft demotes.
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val softOnly = (demotedOnions(context) + SEED_SOFT_DEMOTE) - HARD_EXCLUDE
        prefs.edit().putStringSet(KEY_DEMOTED, softOnly).apply()
    }

    /** Wipe soft demotes (keeps hard excludes via [isHardExcluded]). */
    fun clearSoftDemotes(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_DEMOTED, emptySet())
            .apply()
    }

    fun clear(context: Context) {
        clearSoftDemotes(context)
        ensureHardExcludes(context)
    }

    fun normalize(raw: String): String? {
        val s = raw.trim().lowercase()
        if (s.isEmpty()) return null
        return s.removePrefix("http://").removePrefix("https://").substringBefore("/")
            .substringBefore(":")
            .takeIf { it.endsWith(".onion") }
    }

    fun isHardExcluded(onion: String): Boolean {
        val key = normalize(onion) ?: return true
        return key in HARD_EXCLUDE
    }

    /** True only for hard excludes — soft demotes must not hide Markets makers. */
    fun isExcluded(context: Context, onion: String): Boolean =
        isHardExcluded(onion)

    @Deprecated("Use isExcluded", ReplaceWith("isExcluded(context, onion)"))
    fun isExcludedFromFirstHop(context: Context, onion: String): Boolean =
        isExcluded(context, onion)

    /**
     * Preferred makers for UniFFI (hops + spares).
     * Hard-excluded never appear. Soft-demoted sort after fresh makers but
     * still fill the list so swaps are possible when the good pool is thin.
     */
    fun orderPreferred(
        context: Context,
        onions: List<String>,
        needed: Int,
        fidelityByOnion: Map<String, Double> = emptyMap(),
    ): List<String> {
        val soft = softDemotedOnions(context)
        val unique = onions.mapNotNull { normalize(it) }.distinct()
            .filter { it !in HARD_EXCLUDE }
        val good = unique
            .filter { it !in soft }
            .sortedByDescending { fidelityByOnion[it] ?: 0.0 }
        val demoted = unique
            .filter { it in soft }
            .sortedByDescending { fidelityByOnion[it] ?: 0.0 }
        return (good + demoted).take(needed.coerceAtLeast(0))
    }
}
