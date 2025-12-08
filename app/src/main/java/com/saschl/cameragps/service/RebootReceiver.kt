package com.saschl.cameragps.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.saschl.cameragps.utils.PreferencesManager

class RebootReceiver : BroadcastReceiver() {

    /*
        companion object {
            fun enable(context: Context) {
                val receiver = ComponentName(context, RebootReceiver::class.java)
                context.packageManager.setComponentEnabledSetting(
                    receiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            }
        }
    */


    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, LocationSenderService::class.java)

        Log.i(
            "yo",
            "RebootReceiver received intent: ${intent.action} with preference ${
                PreferencesManager.getAutoStartAfterBootEnabled(context)
            }"
        )
        if (Intent.ACTION_BOOT_COMPLETED == intent.action && PreferencesManager.getAutoStartAfterBootEnabled(
                context
            )
        ) {
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}