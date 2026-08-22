package com.example.coinswapmobile

import android.app.Application
import com.example.coinswapmobile.data.TorManager

class CoinSwapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Soft demotes were over-banning good makers (Online now: 0). Reset them;
        // HARD_EXCLUDE still blocks known PoF-EOF onions.
        com.example.coinswapmobile.data.MakerRoutePrefs.clearSoftDemotes(this)
        com.example.coinswapmobile.data.MakerRoutePrefs.ensureHardExcludes(this)
        TorManager.startInBackground(this)
    }
}
