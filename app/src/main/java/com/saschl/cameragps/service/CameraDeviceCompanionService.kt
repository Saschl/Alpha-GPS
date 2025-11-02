package com.saschl.cameragps.service

import android.Manifest
import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import com.saschl.cameragps.utils.PreferencesManager
import timber.log.Timber
import java.util.Locale


@RequiresApi(Build.VERSION_CODES.S)
class CameraDeviceCompanionService : CompanionDeviceService() {


    private fun startLocationSenderService(address: String?) {
        if (PreferencesManager.isAppEnabled(this)) {

            if (!LocationSenderService.isRunning) {
                val serviceIntent = Intent(this, LocationSenderService::class.java)
                serviceIntent.putExtra("address", address?.uppercase(Locale.getDefault()))
                Timber.i("Starting LocationSenderService for address: $address")

                startForegroundService(serviceIntent)
            } else {
                Timber.i("LocationSenderService already running, will cancel pending shutdowns")
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onDeviceAppeared(address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || missingPermissions()) {
            return
        }
        Timber.i("Device appeared oldest API: $address")

        startLocationSenderService(address)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingPermission")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        super.onDeviceAppeared(associationInfo)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Timber.i("Device appeared old API: ${associationInfo.id}")

            val address = associationInfo.deviceMacAddress?.toString() ?: return

            startLocationSenderService(address)
        }
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    @SuppressLint("MissingPermission")
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        super.onDevicePresenceEvent(event)
        if (missingPermissions()) {
            Timber.e(CameraDeviceCompanionService::class.java.toString(), "Missing permissions")
            return
        }

        if (event.event == DevicePresenceEvent.EVENT_BLE_APPEARED) {

            Timber.i("Device appeared new API: ${event.associationId}")
            val associationId = event.associationId
            val deviceManager = getSystemService<CompanionDeviceManager>()
            val associatedDevices = deviceManager?.getMyAssociations()
            val associationInfo = associatedDevices?.find { it.id == associationId }
            val address = associationInfo?.deviceMacAddress?.toString()

            startLocationSenderService(address)
        }

        if (event.event == DevicePresenceEvent.EVENT_BLE_DISAPPEARED) {
            Timber.i("Device disappeared new API: ${event.associationId}")

            // Request graceful shutdown instead of immediate termination
           /* val shutdownIntent = Intent(this, LocationSenderService::class.java).apply {
                action = LocationSenderService.ACTION_REQUEST_SHUTDOWN
            }
            startService(shutdownIntent)*/
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(address: String) {
        super.onDeviceDisappeared(address)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Timber.i("Device disappeared oldest api: $address. Service will keep running until destroyed")
            return
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            super.onDeviceDisappeared(associationInfo)
            Timber.i("Device disappeared old API: ${associationInfo.id}. Service will keep running until destroyed")
        }

    }

    override fun onCreate() {
        super.onCreate()
        if (Timber.treeCount == 0) {
            FileTree.initialize(this)
            Timber.plant(Timber.DebugTree(), FileTree(this))

            // Set up global exception handler to log crashes
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(defaultHandler))
        }
        Timber.i("CDM started")
    }


    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        Timber.i("CompanionDeviceService destroyed. Will request graceful shutdown now")

        // For some reason Android 12 immediately kills the service without onDeviceDisappeared.
        // FOr now it seems to work as it finds the device again after some time so it's more or less ok
        // Still very weird and should be handled better
        // the disappeared event also seems to be missed sometimes.. we will request shutdown here as well
        // Request graceful shutdown instead of immediate termination
        val shutdownIntent = Intent(this, LocationSenderService::class.java).apply {
            action = SonyBluetoothConstants.ACTION_REQUEST_SHUTDOWN

        }
        startService(shutdownIntent)
    }


    /**
     * Check BLUETOOTH_CONNECT is granted and POST_NOTIFICATIONS is granted for devices running
     * Android 13 and above.
     */
    private fun missingPermissions(): Boolean = ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED

}
