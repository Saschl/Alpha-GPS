package com.saschl.cameragps.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.saschl.cameragps.R
import com.saschl.cameragps.notification.NotificationsHelper
import com.saschl.cameragps.service.SonyBluetoothConstants.locationTransmissionNotificationId
import com.saschl.cameragps.utils.PreferencesManager
import timber.log.Timber
import java.util.UUID

/**
 * Constants for Sony camera Bluetooth communication
 */
object SonyBluetoothConstants {
    // Service UUID of the sony cameras
    val SERVICE_UUID: UUID = UUID.fromString("8000dd00-dd00-ffff-ffff-ffffffffffff")

    // Characteristic for the location services
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000dd11-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_READ_UUID: UUID = UUID.fromString("0000dd21-0000-1000-8000-00805f9b34fb")

    // needed for some cameras to enable the functionality
    val CHARACTERISTIC_ENABLE_UNLOCK_GPS_COMMAND: UUID =
        UUID.fromString("0000dd30-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_ENABLE_LOCK_GPS_COMMAND: UUID =
        UUID.fromString("0000dd31-0000-1000-8000-00805f9b34fb")

    const val ACTION_REQUEST_SHUTDOWN = "com.saschl.cameragps.ACTION_REQUEST_SHUTDOWN"

    // GPS enable command bytes
    val GPS_ENABLE_COMMAND = byteArrayOf(0x01)

    // Location update interval
    const val LOCATION_UPDATE_INTERVAL_MS = 5000L

    // Accuracy threshold for location updates
    const val ACCURACY_THRESHOLD_METERS = 200.0

    // Time threshold for old location updates (5 minutes)
    const val OLD_LOCATION_THRESHOLD_MS = 1000 * 60 * 5

    const val locationTransmissionNotificationId = 404
}


/**
 * Service responsible for sending GPS location data to Sony cameras via Bluetooth
 */
class LocationSenderService : Service() {

    private var address: String? = null
    private var locationDataConfig = LocationDataConfig(shouldSendTimeZoneAndDst = true)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var cameraGatt: BluetoothGatt? = null
    private var writeLocationCharacteristic: BluetoothGattCharacteristic? = null
    private var locationResult: Location = Location("")
    private var isShutdownRequested = false


    private val bluetoothManager: BluetoothManager by lazy {
        applicationContext.getSystemService()!!
    }

    private val bluetoothGattCallback = BluetoothGattCallbackHandler()

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun hasTimeZoneDstFlag(value: ByteArray): Boolean {
        return value.size >= 5 && (value[4].toInt() and 0x02) != 0
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationTransmission() {
        fusedLocationClient.lastLocation.addOnSuccessListener {
            if (it != null) {
                locationResult = it
                sendData(cameraGatt, writeLocationCharacteristic)
            }
        }
        fusedLocationClient.requestLocationUpdates(
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                SonyBluetoothConstants.LOCATION_UPDATE_INTERVAL_MS,
            ).build(), locationCallback, Looper.getMainLooper()
        )
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val currentAddress = this.address
        startAsForegroundService()

        if (address != null && address.equals(currentAddress)) {
            return START_STICKY
        }
        // Check if this is a shutdown request
        if (intent?.action == SonyBluetoothConstants.ACTION_REQUEST_SHUTDOWN) {
            if (currentAddress == null || !PreferencesManager.isKeepAliveEnabled(
                    this,
                    currentAddress
                )
            ) {
                requestShutdown(startId)
                return START_NOT_STICKY
            }
        } else {
            address = intent?.getStringExtra("address")

            Timber.i("Service initialized")

            val device: BluetoothDevice = bluetoothManager.adapter.getRemoteDevice(address)

            if (cameraGatt != null) {
                Timber.i("Gatt will be reused")
            } else {
                Timber.i("Gatt will be created")
                cameraGatt = device.connectGatt(this, true, bluetoothGattCallback)
            }
        }
        return START_REDELIVER_INTENT
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()

        cameraGatt?.close()
        cameraGatt = null

        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        Timber.i("Destroyed service")
    }

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        initializeLogging()
        initializeLocationServices()
    }

    private fun initializeLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = LocationUpdateHandler()
    }

    private fun initializeLogging() {
        if (Timber.treeCount == 0) {
            FileTree.initialize(this)
            Timber.plant(FileTree(this))

            // Set up global exception handler to log crashes
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(defaultHandler))
        }
    }

    private inner class LocationUpdateHandler : LocationCallback() {
        override fun onLocationResult(fetchedLocation: LocationResult) {
            val lastLocation = fetchedLocation.lastLocation ?: return

            if (shouldUpdateLocation(lastLocation)) {
                locationResult = lastLocation
                sendData(cameraGatt, writeLocationCharacteristic)
            }
        }

        private fun shouldUpdateLocation(newLocation: Location): Boolean {
            // Any location is better than none initially
            if (locationResult.provider?.isEmpty() == true) {
                return true
            }

            val accuracyDifference = newLocation.accuracy - locationResult.accuracy

            // If new location is significantly less accurate
            if (accuracyDifference > SonyBluetoothConstants.ACCURACY_THRESHOLD_METERS) {
                val timeDifference = newLocation.time - locationResult.time

                Timber.w("New location is way less accurate than the old one, will only update if the last location is older than 5 minutes")

                // Only update if the current location is very old
                if (timeDifference > SonyBluetoothConstants.OLD_LOCATION_THRESHOLD_MS) {
                    Timber.d("Last accurate location is older than 5 minutes, updating anyway")
                    return true
                }
                return false
            }

            return true
        }
    }

    private fun startAsForegroundService() {
        // create the notification channel
        NotificationsHelper.createNotificationChannel(this)

        // promote service to foreground service
        ServiceCompat.startForeground(
            this,
            locationTransmissionNotificationId,
            NotificationsHelper.buildNotification(
                this, getString(R.string.app_standby_title),
                getString(R.string.app_standby_content)
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

        )
    }

    private fun cancelLocationTransmission() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        val notification = NotificationsHelper.buildNotification(
            this,
            getString(R.string.app_standby_title),
            getString(R.string.app_standby_content)
        )
        NotificationsHelper.showNotification(this, locationTransmissionNotificationId, notification)
    }

    @SuppressLint("MissingPermission")
    private fun resumeLocationTransmission() {
        if (address == null) {
            Timber.w("Cannot resume location transmission: address is null")
            return
        }

        val notification = NotificationsHelper.buildNotification(this)
        NotificationsHelper.showNotification(this, locationTransmissionNotificationId, notification)

        cameraGatt?.discoverServices()
    }

    private inner class BluetoothGattCallbackHandler : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            super.onConnectionStateChange(gatt, status, newState)

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("An error happened: $status")
                cancelLocationTransmission()
            } else {
                Timber.i("Connected to device %d", status)
                resumeLocationTransmission()
            }
        }


        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            val service = gatt.services?.find { it.uuid == SonyBluetoothConstants.SERVICE_UUID }

            // If the Sony camera service is found, get the characteristics
            writeLocationCharacteristic =
                service?.getCharacteristic(SonyBluetoothConstants.CHARACTERISTIC_UUID)

            // TODO reenable reading characteristic for DST and timezone support
            val readCharacteristic =
                service?.getCharacteristic(SonyBluetoothConstants.CHARACTERISTIC_READ_UUID)

            // Enable GPS if needed
            val gpsEnableCharacteristic =
                service?.getCharacteristic(SonyBluetoothConstants.CHARACTERISTIC_ENABLE_UNLOCK_GPS_COMMAND)

            if (gpsEnableCharacteristic != null) {
                Timber.i("Enabling GPS characteristic: ${gpsEnableCharacteristic.uuid}")
                BluetoothGattUtils.writeCharacteristic(
                    gatt,
                    gpsEnableCharacteristic,
                    SonyBluetoothConstants.GPS_ENABLE_COMMAND
                )
            } else {
                // Characteristic to enable GPS does not exist, starting transmission directly
                startLocationTransmission()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            writtenCharacteristic: BluetoothGattCharacteristic?,
            status: Int,
        ) {
            super.onCharacteristicWrite(gatt, writtenCharacteristic, status)

            when (writtenCharacteristic?.uuid) {
                SonyBluetoothConstants.CHARACTERISTIC_ENABLE_UNLOCK_GPS_COMMAND -> {
                    handleGpsEnableResponse(gatt)
                }

                SonyBluetoothConstants.CHARACTERISTIC_ENABLE_LOCK_GPS_COMMAND -> {
                    Timber.i("GPS flag enabled on device, will now send data")
                    startLocationTransmission()
                }
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("Error writing characteristic: $status")
            }
        }

        @SuppressLint("MissingPermission")
        private fun handleGpsEnableResponse(gatt: BluetoothGatt) {
            // The GPS command has been unlocked, now lock it for us
            val lockCharacteristic = BluetoothGattUtils.findCharacteristic(
                gatt,
                SonyBluetoothConstants.CHARACTERISTIC_ENABLE_LOCK_GPS_COMMAND
            )

            lockCharacteristic?.let {
                Timber.i("Found characteristic to lock GPS: ${it.uuid}")
                BluetoothGattUtils.writeCharacteristic(
                    gatt,
                    it,
                    SonyBluetoothConstants.GPS_ENABLE_COMMAND
                )
            }
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                doOnRead(characteristic.value)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            doOnRead(value)
        }

        private fun doOnRead(value: ByteArray) {
            locationDataConfig =
                locationDataConfig.copy(shouldSendTimeZoneAndDst = hasTimeZoneDstFlag(value))
            Timber.i("Characteristic read, shouldSendTimeZoneAndDst: ${locationDataConfig.shouldSendTimeZoneAndDst}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendData(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
    ) {
        if (gatt == null || characteristic == null) {
            Timber.w("Cannot send data: GATT or characteristic is null")
            return
        }

        val locationPacket =
            LocationDataConverter.buildLocationDataPacket(locationDataConfig, locationResult)

        if (!BluetoothGattUtils.writeCharacteristic(gatt, characteristic, locationPacket)) {
            Timber.e("Failed to send location data to camera")
        }
    }


    @SuppressLint("MissingPermission")
    fun requestShutdown(startId: Int) {
        stopSelfResult(startId)
    }
}
