package com.dostup.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Drives the VPN from adb in debug builds, without touching the screen:
 *
 *   adb shell am broadcast -a com.dostup.app.DEBUG -p com.dostup.app --es cmd connect|disconnect|failover|survey|diagnose
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val vpn = DostupApp.instance.vpn
        val cmd = intent.getStringExtra("cmd")
        Log.i("Dostup/Debug", "command: $cmd")
        when (cmd) {
            "connect" -> vpn.connect()
            "disconnect" -> vpn.disconnect()
            "failover" -> vpn.debugFailover()
            "survey" -> vpn.debugSurvey()
            "diagnose" -> vpn.debugDiagnose()
        }
    }
}
