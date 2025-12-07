package com.saschl.cameragps.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.saschl.cameragps.utils.PreferencesManager
import timber.log.Timber

class RebootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Timber.w("LocationSenderService service is being killed, broadcast received. Attempting to restart")
        val wasRunning = intent.getBooleanExtra("was_running", false)
        Timber.i("was_running:%s", wasRunning)

        val serviceIntent = Intent(context, LocationSenderService::class.java)

        if (Intent.ACTION_BOOT_COMPLETED == intent.action && PreferencesManager.getAutoStartAfterBootEnabled(
                context
            )
        ) {
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}