package com.example.coinswapmobile

import android.app.Application
import com.example.coinswapmobile.data.TorManager

class CoinSwapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TorManager.startInBackground(this)
    }
}
