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
import com.saschl.cameragps.service.SonyBluetoothConstants.CHARACTERISTIC_READ_UUID
import com.saschl.cameragps.service.SonyBluetoothConstants.locationTransmissionNotificationId
import com.saschl.cameragps.utils.PreferencesManager
import com.saschl.cameragps.utils.SentryInit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Constants for Sony camera Bluetooth communication
 */
object SonyBluetoothConstants {
    // Service UUID of the sony cameras
    val SERVICE_UUID: UUID = UUID.fromString("8000dd00-dd00-ffff-ffff-ffffffffffff")

    val CONTROL_SERVICE_UUID: UUID = UUID.fromString("8000CC00-CC00-FFFF-FFFF-FFFFFFFFFFFF")

    // Characteristic for the location services
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000dd11-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_READ_UUID: UUID = UUID.fromString("0000dd21-0000-1000-8000-00805f9b34fb")

    // needed for some cameras to enable the functionality
    val CHARACTERISTIC_ENABLE_UNLOCK_GPS_COMMAND: UUID =
        UUID.fromString("0000dd30-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_ENABLE_LOCK_GPS_COMMAND: UUID =
        UUID.fromString("0000dd31-0000-1000-8000-00805f9b34fb")

    val CHARACTERISTIC_LOCATION_ENABLED_IN_CAMERA: UUID =
        UUID.fromString("0000dd01-0000-1000-8000-00805f9b34fb")

    val TIME_SYNC_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("0000cc13-0000-1000-8000-00805f9b34fb")

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

    private val commandMutex = Mutex()

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

                    Timber.d("Sending initial location to all active connections")
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
    private suspend fun handleNoAddress(startId: Int) {
        if (deviceDao.getAlwaysOnEnabledDeviceCount() == 0) {
            Timber.i("No always-on devices found, shutting down service")
            requestShutdown(startId)
        } else {
            deviceDao.getAllCameraDevices()
                .filter { it.alwaysOnEnabled }
                .forEach { cameraConnectionManager.connect(it.mac) }
        }

    }

    @SuppressLint("MissingPermission")
    private suspend fun handleShutdownAllDevices(startId: Int) {
        if (deviceDao.getAlwaysOnEnabledDeviceCount() == 0) {
            Timber.i("No always-on devices found, disconnecting all cameras and shutting down service")
            cameraConnectionManager.disconnectAll()
            requestShutdown(startId)
        } else {
            Timber.i("At least one always-on device found, not shutting down service")
        }

    }

    @SuppressLint("MissingPermission")
    private suspend fun handleShutdownRequest(address: String, startId: Int) {
        Timber.i("Shutdown requested for device $address")

        if (address == "all") {
            handleShutdownAllDevices(startId)
            return
        }

        if (!deviceDao.isDeviceAlwaysOnEnabled(address)) {
            Timber.d("Disconnecting camera $address as it is not always-on enabled")
            cameraConnectionManager.disconnect(address)
        }

        delay(1000)

        if (cameraConnectionManager.getConnectedCameras().isEmpty()) {
            Timber.d("No connected cameras remaining, shutting down service")
            requestShutdown(startId)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        startAsForegroundService()

        /**
         *  TODO maybe handle with commandqueue to avoid blocking the main thread (although all operations finish rather quickly)
         *
         * val commandQueue = Channel<CommandData>(Channel.UNLIMITED)
         *  data class CommandData(val intent: Intent?, val startId: Int)
         */

        lifecycleScope.launch {
            commandMutex.withLock {
                handleStartCommand(intent, startId)
                Timber.i(
                    "processed start command $startId with intent action ${intent?.action} and address ${
                        intent?.getStringExtra(
                            "address"
                        )
                    }"
                )
            }
        }

        return START_REDELIVER_INTENT
    }

    private suspend fun handleStartCommand(intent: Intent?, startId: Int) {
        val address = intent?.getStringExtra("address")
        val isShutdownRequest = intent?.action == SonyBluetoothConstants.ACTION_REQUEST_SHUTDOWN

        if (address == null) {
            handleNoAddress(startId)
            return
        }

        if (isShutdownRequest) {
            handleShutdownRequest(address, startId)
            return
        }

        if (!cameraConnectionManager.isConnected(address)) {
            Timber.i("Service initialized")
            cameraConnectionManager.connect(address)
        }
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
        cameraConnectionManager.disconnectAll()
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
            Timber.d("Got a new location")

            if (shouldUpdateLocation(lastLocation)) {
                locationResult = lastLocation
                Timber.d("Will update cameras with new location")

                cameraConnectionManager.getActiveConnections().forEach {
                    Timber.d("Sending location to camera ${it.gatt.device.name}")
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

            try {
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
            } catch (e: SecurityException) {
                Timber.e("Failed to start foreground service due to missing permissions: ${e.message}")
            }

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
            Timber.d("No active cameras remaining, stopping location updates")
            if (::locationCallback.isInitialized) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                isLocationTransmitting = false
            }
        } else {
            Timber.d("Active cameras remaining, updating notification")
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

        // value from official Sony app,  might be unused on Android >= 14
        gatt.requestMtu(158)
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

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            gatt.discoverServices()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)

            if (characteristic.uuid.toString().uppercase().startsWith("0000DD01")) {
                Timber.w("Received characteristic change from camera: ${characteristic.uuid}, $value")
            } else {
                Timber.i("Received characteristic change from camera: ${characteristic.uuid}, $value")
            }
        }


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

        @SuppressLint("MissingPermission")
        private fun handleServicesDiscovered(
            gatt: BluetoothGatt,
            service: BluetoothGattService?
        ) {
            // TODO seems like this can be changed on the fly, so we should read it every time
            /*            val dstTimeZoneFlag = deviceDao.getTimezoneDstFlag(gatt.device.address.uppercase());
                        if (dstTimeZoneFlag != TimeZoneDSTState.UNDEFINED) {
                            locationDataConfig =
                                locationDataConfig.copy(shouldSendTimeZoneAndDst = TimeZoneDSTState.ENABLED == dstTimeZoneFlag)
                            enableGpsTransmission(gatt)
                        } else {*/
            val readCharacteristic =
                service?.getCharacteristic(CHARACTERISTIC_READ_UUID)
            if (readCharacteristic != null) {
                Timber.i("Reading characteristic for timezone and DST support: ${readCharacteristic.uuid}")
                gatt.readCharacteristic(readCharacteristic)
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
                Timber.i("Characteristic to enable GPS does not exist, starting transmission directly")
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
                    Timber.i("GPS flag enabled on device, will now send time sync data if feature exists, status was $status")
                    sendTimeSyncData(gatt)
                }

                SonyBluetoothConstants.TIME_SYNC_CHARACTERISTIC_UUID -> {
                    Timber.i("Time sync data sent to device, will now start location transmission, status was $status")
                    startLocationTransmission()
                }
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("Error writing characteristic: $status")
            }
        }

        @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
        private fun sendTimeSyncData(gatt: BluetoothGatt) {
            val service =
                gatt.services?.find { it.uuid == SonyBluetoothConstants.CONTROL_SERVICE_UUID }
            val timeSyncCharacteristic =
                service?.getCharacteristic(SonyBluetoothConstants.TIME_SYNC_CHARACTERISTIC_UUID)

            if (timeSyncCharacteristic == null) {
                Timber.i("Time sync characteristic not found, starting location transmission directly")
                startLocationTransmission()
                return
            }
            timeSyncCharacteristic.let {
                val timeSyncPacket =
                    LocationDataConverter.serializeTimeAreaData(ZonedDateTime.now())
                Timber.d("Sending time sync data to camera")

                if (!BluetoothGattUtils.writeCharacteristic(
                        gatt,
                        it,
                        timeSyncPacket
                    )
                ) {
                    Timber.e("Failed to send time sync data to camera, starting location transmission directly")
                    startLocationTransmission()
                }
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

        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BLUETOOTH_CONNECT])
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                doOnRead(characteristic.value, gatt, characteristic)
            }
        }

        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BLUETOOTH_CONNECT])
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            doOnRead(value, gatt, characteristic)
        }

        // TODO make this a bit cleaner, right now we do not check for specific characteristics, but we only read one anyway
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
        private fun doOnRead(
            value: ByteArray,
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val service = gatt.services?.find { it.uuid == SonyBluetoothConstants.SERVICE_UUID }
            val locationEnabledCharacteristic =
                service?.getCharacteristic(SonyBluetoothConstants.CHARACTERISTIC_LOCATION_ENABLED_IN_CAMERA)

            if (locationEnabledCharacteristic != null && characteristic.uuid.equals(CHARACTERISTIC_READ_UUID)) {

                if (gatt.readCharacteristic(locationEnabledCharacteristic)) {
                    return
                }

                // last read, so we can start writing
            } else {
                Timber.w("Received characteristic read from camera (location status): ${characteristic.uuid}, $value")
            }
            locationDataConfig =
                locationDataConfig.copy(shouldSendTimeZoneAndDst = hasTimeZoneDstFlag(value))
            lifecycleScope.launch {
                deviceDao.setTimezoneDstFlag(
                    gatt.device.address.uppercase(),
                    if (locationDataConfig.shouldSendTimeZoneAndDst) TimeZoneDSTState.ENABLED else TimeZoneDSTState.DISABLED
                )
            }
            Timber.i("Characteristic read, shouldSendTimeZoneAndDst: ${locationDataConfig.shouldSendTimeZoneAndDst}")
            enableGpsTransmission(gatt)

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
