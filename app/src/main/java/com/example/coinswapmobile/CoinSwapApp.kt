package com.example.coinswapmobile

import android.app.Application
import com.example.coinswapmobile.data.MakerRoutePrefs
import com.example.coinswapmobile.data.TorManager

class CoinSwapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Clear legacy maker bans so all online makers stay usable for swaps.
        MakerRoutePrefs.clearAllBans(this)
        TorManager.startInBackground(this)
    }
}
