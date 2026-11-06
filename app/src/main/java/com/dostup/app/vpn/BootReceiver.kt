package com.dostup.app.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.dostup.app.DostupApp

/** Brings the VPN back after a reboot or an app update, if the user had it on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = DostupApp.instance
        if (app.store.wantConnected && VpnService.prepare(context) == null) app.vpn.connect()
    }
}
