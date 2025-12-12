package com.saschl.cameragps.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.saschl.cameragps.utils.PreferencesManager
import com.saschl.cameragps.utils.SentryInit
import timber.log.Timber

class RestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (PreferencesManager.sentryEnabled(context)) {
            SentryInit.initSentry(context)
        }

        if (Timber.forest().find { it is FileTree } == null) {
            val logLevel = PreferencesManager.logLevel(context)
            FileTree.initialize(context)
            Timber.plant(FileTree(context, logLevel))

            // Set up global exception handler to log crashes
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(defaultHandler))
        }
        Timber.w("LocationSenderService service is being killed, broadcast received. Attempting to restart")
        val wasRunning = intent.getBooleanExtra("was_running", false)
        Timber.i("was_running:%s", wasRunning)

        val serviceIntent = Intent(context, LocationSenderService::class.java)

        if (wasRunning) {
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}