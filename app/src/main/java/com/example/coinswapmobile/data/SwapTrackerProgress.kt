package com.example.coinswapmobile.data

import com.upokecenter.cbor.CBORObject
import com.upokecenter.cbor.CBORType
import java.io.File

/**
 * Reads live swap phase from core-lib swap_tracker.cbor
 * (same file the desktop taker-app polls every 2s).
 */
object SwapTrackerProgress {

    data class Snapshot(
        val swapId: String?,
        val phase: String?,
        val failedAtPhase: String?,
        val failureReason: String?,
    )

    private val phaseNames = listOf(
        "MakersDiscovered",
        "Negotiated",
        "FundingCreated",
        "FundsBroadcast",
        "ContractsExchanged",
        "Finalizing",
        "PrivkeysForwarded",
        "Completed",
        "Failed",
    )

    fun read(dataDir: File, preferSwapId: String? = null): Snapshot? {
        val file = File(dataDir, "swap_tracker.cbor")
        if (!file.isFile) return null
        return runCatching {
            val root = CBORObject.DecodeFromBytes(file.readBytes())
            val swaps = root["swaps"] ?: return null
            if (swaps.size() == 0) return null

            fun recordOf(key: CBORObject): Snapshot {
                val rec = swaps[key]
                return Snapshot(
                    swapId = cborToString(key),
                    phase = readPhase(rec?.get("phase")),
                    failedAtPhase = readPhase(rec?.get("failed_at_phase"))
                        ?: readPhase(rec?.get("failedAtPhase")),
                    failureReason = formatFailure(
                        rec?.get("failure_reason") ?: rec?.get("failureReason"),
                    ),
                )
            }

            if (!preferSwapId.isNullOrBlank()) {
                val preferKey = CBORObject.FromObject(preferSwapId)
                if (swaps.ContainsKey(preferKey)) {
                    return recordOf(preferKey)
                }
            }

            var best: Snapshot? = null
            for (key in swaps.getKeys()) {
                val snap = recordOf(key)
                val phase = snap.phase.orEmpty()
                if (phase.isNotBlank() &&
                    !phase.equals("Completed", true) &&
                    !phase.equals("Failed", true)
                ) {
                    return snap
                }
                best = snap
            }
            best
        }.getOrNull()
    }

    fun phaseLabel(phase: String?): String {
        if (phase.isNullOrBlank()) return "Running…"
        return when (phase) {
            "MakersDiscovered" -> "Makers discovered"
            "Negotiated" -> "Negotiated with makers"
            "FundingCreated" -> "Building funding txs…"
            "FundsBroadcast" -> "Waiting for funding confirms…"
            "ContractsExchanged" -> "Exchanging contracts…"
            "Finalizing" -> "Finalizing…"
            "PrivkeysForwarded" -> "Finishing key exchange…"
            "Completed" -> "Completed"
            "Failed" -> "Failed"
            else -> phase
        }
    }

    private fun readPhase(obj: CBORObject?): String? {
        if (obj == null || obj.isNull) return null
        return when (obj.type) {
            CBORType.TextString -> obj.AsString()
            CBORType.Integer -> {
                val idx = obj.AsInt32Value()
                phaseNames.getOrNull(idx) ?: idx.toString()
            }
            else -> runCatching { obj.AsString() }.getOrNull() ?: obj.toString()
        }
    }

    private fun cborToString(obj: CBORObject): String =
        runCatching { obj.AsString() }.getOrElse { obj.toString() }

    private fun formatFailure(obj: CBORObject?): String? {
        if (obj == null || obj.isNull) return null
        return runCatching { obj.AsString() }.getOrElse { obj.toString() }
            .takeIf { it.isNotBlank() && it != "null" }
    }
}
