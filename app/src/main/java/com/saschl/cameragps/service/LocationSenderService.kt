package com.saschl.cameragps.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.saschl.cameragps.R
import com.saschl.cameragps.database.LogDatabase
import com.saschl.cameragps.database.devices.CameraDeviceDAO
import com.saschl.cameragps.database.devices.TimeZoneDSTState
import com.saschl.cameragps.notification.NotificationsHelper
import com.saschl.cameragps.service.SonyBluetoothConstants.locationTransmissionNotificationId
import com.saschl.cameragps.utils.PreferencesManager
import com.saschl.cameragps.utils.SentryInit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
class LocationSenderService : LifecycleService() {
    private var isLocationTransmitting: Boolean = false

    private var isInitialized = true
    private var locationDataConfig = LocationDataConfig(shouldSendTimeZoneAndDst = true)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    //private var cameraGatt: BluetoothGatt? = null
    private var locationResult: Location = Location("")

    private lateinit var cameraConnectionManager: CameraConnectionManager

    private lateinit var deviceDao: CameraDeviceDAO

    private val bluetoothManager: BluetoothManager by lazy {
        applicationContext.getSystemService()!!
    }

    private val bluetoothGattCallback = BluetoothGattCallbackHandler()

    private fun hasTimeZoneDstFlag(value: ByteArray): Boolean {
        return value.size >= 5 && (value[4].toInt() and 0x02) != 0
    }

    companion object {
        val activeTransmissions = mutableStateMapOf<String, Boolean>()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationTransmission() {
        if (!isLocationTransmitting) {
            Timber.i("Starting location transmission")

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    locationResult = location

                    cameraConnectionManager.getActiveConnections().forEach { device ->
                        sendData(device.gatt, device.writeCharacteristic)

                    }
                }
            }
            fusedLocationClient.requestLocationUpdates(
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    SonyBluetoothConstants.LOCATION_UPDATE_INTERVAL_MS,
                ).build(), locationCallback, Looper.getMainLooper()
            )
            isLocationTransmitting = true;
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        startAsForegroundService()
        val address = intent?.getStringExtra("address")

        if (address == null) {
            lifecycleScope.launch {
                if (deviceDao.getAlwaysOnEnabledDeviceCount() == 0) {
                    Timber.i("No always-on devices found, shutting down service")
                    requestShutdown(startId)
                    return@launch
                } else {
                    deviceDao.getAllCameraDevices().filter { it.alwaysOnEnabled }.forEach {
                        cameraConnectionManager.connect(it.mac)
                    }
                }
            }
            return START_REDELIVER_INTENT
        }

        // Check if this is a shutdown request
        if (intent.action == SonyBluetoothConstants.ACTION_REQUEST_SHUTDOWN) {
            Timber.i("Shutdown requested for device $address")

            // Will be fired by CDM when all associated devices are gone (hopefully)
            if (address == "all") {
                lifecycleScope.launch {
                    if (deviceDao.getAlwaysOnEnabledDeviceCount() == 0) {
                        Timber.i("No always-on devices found, disconnecting all cameras and shutting down service")
                        cameraConnectionManager.disconnectAll()
                        requestShutdown(startId)
                    } else {
                        Timber.i("At least one always-on device found, not shutting down service")
                    }
                }
                return START_REDELIVER_INTENT
            }

            lifecycleScope.launch {
                if (!deviceDao.isDeviceAlwaysOnEnabled(address)) {
                    Timber.d("Disconnecting camera $address as it is not always-on enabled")
                    cameraConnectionManager.disconnect(address)
                }
                // FIXME was disabled as it can cause issues with events in quick succession (appear <-> disappear with a few ms delay, seems like an Android issue)
                // Wait a bit to ensure the disconnection is fully processed and no weird events appear in te meantime
                delay(1000)
                if (cameraConnectionManager.getConnectedCameras().isEmpty()) {
                    Timber.d("No connected cameras remaining, shutting down service")
                    requestShutdown(startId)
                }
            }

            return START_STICKY
        } else {
            if (cameraConnectionManager.isConnected(address)) {
                return START_STICKY
            }
            Timber.i("Service initialized")
            cameraConnectionManager.connect(address)
        }
        return START_REDELIVER_INTENT
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        if (isInitialized) {
            Timber.e("Service unexpectedly destroyed, attempting to restart")
            val broadcastIntent =
                Intent(applicationContext, RestartReceiver::class.java)
            broadcastIntent.putExtra("was_running", true)
            sendBroadcast(broadcastIntent)
        }

        Timber.i("Destroyed service")
    }

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        deviceDao = LogDatabase.getDatabase(this).cameraDeviceDao()
        initializeLogging()
        initializeLocationServices()
        cameraConnectionManager =
            CameraConnectionManager(this, bluetoothManager, bluetoothGattCallback)
    }

    private fun initializeLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = LocationUpdateHandler()
    }

    private fun initializeLogging() {
        if (Timber.forest().find { it is FileTree } == null) {
            FileTree.initialize(this)
            Timber.plant(FileTree(this, PreferencesManager.logLevel(this)))
            SentryInit.initSentry(this)

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

                cameraConnectionManager.getActiveConnections().forEach {
                    sendData(it.gatt, it.writeCharacteristic)
                }
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
        if (!isLocationTransmitting) {
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
    }

    private fun cancelLocationTransmission() {
        if (cameraConnectionManager.getActiveCameras().isEmpty()) {
            val notification = NotificationsHelper.buildNotification(
                this,
                getString(R.string.app_standby_title),
                getString(R.string.app_standby_content)
            )
            NotificationsHelper.showNotification(
                this,
                locationTransmissionNotificationId,
                notification
            )

            if (::locationCallback.isInitialized) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                isLocationTransmitting = false
            }
        } else {
            val notification = NotificationsHelper.buildNotification(
                this,
                cameraConnectionManager.getActiveCameras().size
            )
            NotificationsHelper.showNotification(
                this,
                locationTransmissionNotificationId,
                notification
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun resumeLocationTransmission(gatt: BluetoothGatt) {
        val notification = NotificationsHelper.buildNotification(
            this,
            cameraConnectionManager.getActiveCameras().size
        )
        NotificationsHelper.showNotification(this, locationTransmissionNotificationId, notification)

        gatt.discoverServices()
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
                cameraConnectionManager.pauseDevice(gatt.device.address.uppercase())
                cancelLocationTransmission()

            } else {
                Timber.i("Connected to device with status %d", status)
                    cameraConnectionManager.resumeDevice(gatt.device.address.uppercase())
                    resumeLocationTransmission(gatt)

            }
        }


        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            val service = gatt.services?.find { it.uuid == SonyBluetoothConstants.SERVICE_UUID }


            val writeLocationCharacteristic =
                service?.getCharacteristic(SonyBluetoothConstants.CHARACTERISTIC_UUID)

            cameraConnectionManager.setWriteCharacteristic(
                gatt.device.address.uppercase(),
                writeLocationCharacteristic
            )

            lifecycleScope.launch {
                handleServicesDiscovered(gatt, service)
            }
        }

        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
        private suspend fun handleServicesDiscovered(
            gatt: BluetoothGatt,
            service: BluetoothGattService?
        ) {
            val dstTimeZoneFlag = deviceDao.getTimezoneDstFlag(gatt.device.address.uppercase());
            if (dstTimeZoneFlag != TimeZoneDSTState.UNDEFINED) {
                locationDataConfig =
                    locationDataConfig.copy(shouldSendTimeZoneAndDst = TimeZoneDSTState.ENABLED == dstTimeZoneFlag)
                enableGpsTransmission(gatt)
            } else {
                val readCharacteristic =
                    service?.getCharacteristic(SonyBluetoothConstants.CHARACTERISTIC_READ_UUID)
                if (readCharacteristic != null) {
                    Timber.i("Reading characteristic for timezone and DST support: ${readCharacteristic.uuid}")
                    gatt.readCharacteristic(readCharacteristic)
                }
            }
        }

        @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
        private fun enableGpsTransmission(gatt: BluetoothGatt) {
            val service = gatt.services?.find { it.uuid == SonyBluetoothConstants.SERVICE_UUID }
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
                Timber.d("Characteristic to enable GPS does not exist, starting transmission directly")
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
                doOnRead(characteristic.value, gatt)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            doOnRead(value, gatt)
        }

        // TODO make this a bit cleaner, right now we do not check for specific characteristics, but we only read one anyway
        private fun doOnRead(value: ByteArray, gatt: BluetoothGatt) {
            locationDataConfig =
                locationDataConfig.copy(shouldSendTimeZoneAndDst = hasTimeZoneDstFlag(value))
            lifecycleScope.launch {
                deviceDao.setTimezoneDstFlag(
                    gatt.device.address.uppercase(),
                    if (locationDataConfig.shouldSendTimeZoneAndDst) TimeZoneDSTState.ENABLED else TimeZoneDSTState.DISABLED
                )
            }
            enableGpsTransmission(gatt)

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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun requestShutdown(startId: Int) {
        activeTransmissions.clear()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        isLocationTransmitting = false
        isInitialized = false
        cameraConnectionManager.disconnectAll()
        stopSelf(startId)
    }
}
