package com.example.coinswapmobile.data

enum class DisplayCurrency {
    SATS,
    BTC,
    INR,
    ;

    val label: String
        get() = when (this) {
            SATS -> "sats"
            BTC -> "BTC"
            INR -> "INR"
        }

    companion object {
        fun fromStored(value: String?): DisplayCurrency =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SATS
    }
}

/** Display-only INR rate. 1 BTC = ₹80,00,000. */
private const val INR_PER_BTC = 8_000_000.0

fun formatAmount(sats: Long, currency: DisplayCurrency): String = when (currency) {
    DisplayCurrency.SATS -> "%,d sats".format(sats)
    DisplayCurrency.BTC -> {
        val btc = sats / 100_000_000.0
        val text = "%.8f".format(btc).trimEnd('0').trimEnd('.')
        "$text BTC"
    }
    DisplayCurrency.INR -> {
        val inr = sats * INR_PER_BTC / 100_000_000.0
        "₹%,.0f".format(inr)
    }
}
