package com.paygate.smsfwd.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.paygate.smsfwd.data.prefs.PreferenceManager
import com.paygate.smsfwd.service.PayGateForwarderService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = PreferenceManager(context.applicationContext)
            if (prefs.isPaired && prefs.isServiceEnabled && !prefs.isAuthRevoked) {
                PayGateForwarderService.start(context.applicationContext)
                PayGateForwarderService.triggerDrain(context.applicationContext)
            }
        }
    }
}
