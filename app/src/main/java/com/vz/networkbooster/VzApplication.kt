package com.vz.networkbooster

import android.app.Application
import com.vz.networkbooster.vpn.VpnManager

class VzApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        VpnManager.init(this)
    }
}
