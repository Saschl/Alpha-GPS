/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.saschl.cameragps.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.saschl.cameragps.R
import com.saschl.cameragps.service.pairing.PairingManager
import com.saschl.cameragps.service.pairing.isDevicePaired
import com.saschl.cameragps.service.pairing.startDevicePresenceObservation
import com.saschl.cameragps.ui.EnhancedLocationPermissionBox
import com.saschl.cameragps.ui.LogViewerActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executor
import java.util.regex.Pattern

@SuppressLint("MissingPermission")
@Composable
fun CameraDeviceManager(
    onSettingsClick: () -> Unit = {}
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceManager = context.getSystemService<CompanionDeviceManager>()
    val adapter = context.getSystemService<BluetoothManager>()?.adapter
    var selectedDevice by remember {
        mutableStateOf<AssociatedDeviceCompat?>(null)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()


    var associatedDevices by remember {
        // If we already associated the device no need to do it again.
        mutableStateOf(deviceManager!!.getAssociatedDevices())
    }



    LaunchedEffect(lifecycleState) {
        // Do something with your state
        // You may want to use DisposableEffect or other alternatives
        // instead of LaunchedEffect
        when (lifecycleState) {
            Lifecycle.State.DESTROYED -> {}
            Lifecycle.State.INITIALIZED -> {}
            Lifecycle.State.CREATED -> {}
            Lifecycle.State.STARTED -> {}
            Lifecycle.State.RESUMED -> {
                associatedDevices = deviceManager!!.getAssociatedDevices()
            }
        }
    }


    if (deviceManager == null || adapter == null) {
        Text(text = "No Companion device manager found. The device does not support it.")
    } else {
        if (selectedDevice == null) {
            EnhancedLocationPermissionBox {
                DevicesScreen(
                    deviceManager,
                    onDeviceAssociated = {
                        scope.launch {
                            delay(1000) // give the system a short time to breathe
                            startDevicePresenceObservation(deviceManager, it)
                            associatedDevices =
                                associatedDevices.filter { associatedDevice -> associatedDevice != it }; it.isPaired =
                            true; associatedDevices = associatedDevices + it
                        }

                    },
                    onConnect = { device ->
                        selectedDevice =
                            (device)

                    },
                    associatedDevices = associatedDevices,
                    onSettingsClick = onSettingsClick
                )
            }
        } else {
            EnhancedLocationPermissionBox {
                DeviceDetailScreen(device = selectedDevice!!, deviceManager, onDisassociate = {
                    associatedDevices.find { ass -> ass.address == it.address }?.let { it ->
                        Timber.i("Disassociating device: ${it.name} (${it.address})")
                        scope.launch {

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                                deviceManager.stopObservingDevicePresence(
                                    ObservingDevicePresenceRequest.Builder().setAssociationId(it.id)
                                        .build()
                                )

                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                @Suppress("DEPRECATION")
                                deviceManager.stopObservingDevicePresence(it.address)
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                deviceManager.disassociate(it.id)
                            } else {
                                @Suppress("DEPRECATION")
                                deviceManager.disassociate(it.address)
                            }
                            val serviceIntent = Intent(
                                context.applicationContext,
                                LocationSenderService::class.java
                            )
                            context.stopService(serviceIntent)

                            associatedDevices = deviceManager.getAssociatedDevices()
                        }
                        selectedDevice = null
                    }
                }, onClose = { selectedDevice = null })

            }
        }
    }
}


private data class DeviceConnectionState(
    val gatt: BluetoothGatt?,
    val connectionState: Int,
    val mtu: Int,
    val services: List<BluetoothGattService> = emptyList(),
    val messageSent: Boolean = false,
    val messageReceived: String = "",
) {
    companion object {
        val None = DeviceConnectionState(null, -1, -1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
private fun DevicesScreen(
    deviceManager: CompanionDeviceManager,
    associatedDevices: List<AssociatedDeviceCompat>,
    onDeviceAssociated: (AssociatedDeviceCompat) -> Unit,
    onConnect: (AssociatedDeviceCompat) -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current

    val scope = rememberCoroutineScope()

    // State for managing pairing after association
    var pendingPairingDevice by remember { mutableStateOf<AssociatedDeviceCompat?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name_ui),
                      //  style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },

                actions = {
                    IconButton(
                        onClick = {
                            context.startActivity(
                                Intent(context, LogViewerActivity::class.java)
                            )
                        }
                    ) {
                        Icon(
                            painterResource(R.drawable.baseline_view_list_24),
                            contentDescription = "View Logs",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { onSettingsClick() }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { innerPadding ->


        Column(
            modifier = Modifier
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,

            ) {
/*            Button(onClick = {
                context.startActivity(
                    Intent(
                        context,
                        LogViewerActivity::class.java
                    )
                )
            }) { Text(text = stringResource(R.string.view_logs)) }*/
            ScanForDevicesMenu(
                deviceManager,
                associatedDevices,
                onSetPairingDevice = { device -> pendingPairingDevice = device })
            {
                onDeviceAssociated(it)
                //  BlePresenceScanner.start(context)
                //startDevicePresenceObservation(deviceManager, it)
                //  startForegroundService(context, serviceIntent)

            }
            AssociatedDevicesList(
                associatedDevices = associatedDevices,
                deviceManager,
                onConnect = onConnect,
                onSetPendingPairingDevice = { device ->
                    pendingPairingDevice = device
                }
            )
            // Handle pairing for newly associated device
            pendingPairingDevice?.let { device ->
                PairingManager(
                    device = device,
                    deviceManager = deviceManager,
                    onPairingComplete = {
                        Timber.i("Pairing completed for newly associated device ${device.name}")
                        onDeviceAssociated(device)
                        pendingPairingDevice = null

                    },
                    onPairingCancelled = {
                        Timber.i("Pairing cancelled for newly associated device ${device.name}")
                        // Still add the device even if pairing was cancelled
                        onDeviceAssociated(device)
                        pendingPairingDevice = null
                    }
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
@Composable
private fun ScanForDevicesMenu(
    deviceManager: CompanionDeviceManager,
    associatedDevices: List<AssociatedDeviceCompat>,
    onSetPairingDevice: (AssociatedDeviceCompat) -> Unit,
    onDeviceAssociated: (AssociatedDeviceCompat) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var errorMessage by remember {
        mutableStateOf("")
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        when (it.resultCode) {
            CompanionDeviceManager.RESULT_OK -> {
                it.data?.getAssociationResult()?.run {

                    // Device association successful, now check if pairing is needed
                    val bluetoothManager = context.getSystemService<BluetoothManager>()
                    val adapter = bluetoothManager?.adapter

                    if (associatedDevices.any { it -> it.address == this.address }) {
                        Timber.i("Device ${this.name} already associated, skipping pairing")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            deviceManager.disassociate(this.id)
                            errorMessage = "The device is already associated."
                        } else {
                            // Android 12 can only disassociate by address, but that will probably also delete the existing association
                            @Suppress("DEPRECATION")
                            deviceManager.disassociate(this.address)
                            errorMessage =
                                "The device was already associated. The association was removed to prevent duplicates. Please try again."
                        }
                        return@run
                    }
                    if (!isDevicePaired(adapter, this.address)) {
                        Timber.i("Device ${this.name} associated but not paired, initiating pairing")
                        onSetPairingDevice(this)
                    } else {
                        Timber.i("Device ${this.name} already paired, completing association")
                        onDeviceAssociated(this)
                    }
                    errorMessage = ""
                }
            }

            CompanionDeviceManager.RESULT_CANCELED -> {
                errorMessage = context.getString(R.string.the_request_was_canceled)
            }

            CompanionDeviceManager.RESULT_INTERNAL_ERROR -> {
                errorMessage = context.getString(R.string.internal_error_happened)
            }

            CompanionDeviceManager.RESULT_DISCOVERY_TIMEOUT -> {
                errorMessage =
                    context.getString(R.string.no_device_matching_the_given_filter_were_found)
            }

            CompanionDeviceManager.RESULT_USER_REJECTED -> {
                errorMessage = context.getString(R.string.the_user_explicitly_declined_the_request)
            }

            else -> {
                errorMessage = context.getString(R.string.unknown_error)
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                text = stringResource(R.string.scan_for_devices),
            )
            Button(
                modifier = Modifier.weight(0.7f),
                onClick = {
                    scope.launch {
                        val intentSender =
                            requestDeviceAssociation(deviceManager)
                        launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                    }
                },
            ) {
                Text(text = "Start", maxLines = 1)
            }
        }
        if (errorMessage.isNotBlank()) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssociatedDevicesList(
    associatedDevices: List<AssociatedDeviceCompat>,
    deviceManager: CompanionDeviceManager,
    onConnect: (AssociatedDeviceCompat) -> Unit,
    onSetPendingPairingDevice: (AssociatedDeviceCompat) -> Unit,
) {

    val context = LocalContext.current
    val bluetoothManager = context.getSystemService<BluetoothManager>()
    val adapter = bluetoothManager?.adapter

    Column {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            stickyHeader {
                Text(
                    text = stringResource(R.string.associated_devices),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (associatedDevices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.baseline_photo_camera_24),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.no_devices_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.no_devices_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            items(associatedDevices) { device ->
                val isPaired = try {
                    adapter?.let { isDevicePaired(it, device.address) } ?: false
                } catch (e: SecurityException) {
                    false
                }

                if(device.name == "N/A") {
                    device.name = adapter?.getRemoteDevice(device.address)!!.name
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clickable(
                            true,
                            onClick = {
                                /* FIXME restore once properly implemented
                                 if (isPaired) onConnect(device) else onSetPendingPairingDevice(
                                    device
                                ) */
                                onConnect(device)
                            }),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,

                    ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(0.2f),
                    ) {
                        Icon(
                            painterResource(R.drawable.baseline_photo_camera_24),
                            contentDescription = "Device Icon"
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        Text(fontWeight = FontWeight.Bold, text = device.name)

                        /* FIXME restore once properly implemented
                         if (!isPaired) {
                            Text(
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                text = context.getString(R.string.not_paired_tap_to_pair_again),
                            )

                            // TODO refactor into separate method
                            // TODO extract logic to service properly this should not be in UI
                            context.stopService(
                                Intent(
                                    context.applicationContext,
                                    LocationSenderService::class.java
                                )
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                                deviceManager.stopObservingDevicePresence(
                                    ObservingDevicePresenceRequest.Builder()
                                        .setAssociationId(device.id)
                                        .build()
                                )

                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                @Suppress("DEPRECATION")
                                deviceManager.stopObservingDevicePresence(device.address)
                            }
                        } */

                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(0.8f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Show details"
                        )

                    }

                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 2.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        // FIXME pairing logic needs to be refactored so that it can be used here as well
        // This is for adding pairing logic to the "Pair new camera" button as the last element (TBC)
        /* Row(
             Modifier
                 .fillMaxWidth()
                 *//* .background(color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp))*//*
                *//* .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(16.dp)
                )*//*.padding(24.dp)
                .clickable(true, onClick = {  }),

            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,

            ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(0.2f),
            ) {
                Icon(
                    painterResource(R.drawable.outline_add_circle_24),
                    contentDescription = "Device Icon"
                )

            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Text(fontWeight = FontWeight.Bold, text = "Pair new camera")
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(0.8f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                *//* IconButton(onClick = {onConnect(device)}) {*//*
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Hi")
                *//*   }*//*
                *//*   OutlinedButton(
                    onClick = { onConnect(device) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Connect")
                }*//*

            }
        }*/
    }
}

private fun Intent.getAssociationResult(): AssociatedDeviceCompat? {
    var result: AssociatedDeviceCompat? = null

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        result = getParcelableExtra(
            CompanionDeviceManager.EXTRA_ASSOCIATION,
            AssociationInfo::class.java,
        )?.toAssociatedDevice()
    } else {
        // Below Android 33 the result returns either a BLE ScanResult, a
        // Classic BluetoothDevice or a Wifi ScanResult
        // In our case we are looking for our BLE GATT server so we can cast directly
        // to the BLE ScanResult
        @Suppress("DEPRECATION")
        val scanResult = getParcelableExtra<ScanResult>(CompanionDeviceManager.EXTRA_DEVICE)
        if (scanResult != null) {
            result = AssociatedDeviceCompat(
                id = scanResult.advertisingSid,
                address = scanResult.device.address ?: "N/A",
                name = scanResult.scanRecord?.deviceName ?: "N/A",
                device = scanResult.device,
            )
        }
    }
    return result

}

private suspend fun requestDeviceAssociation(
    deviceManager: CompanionDeviceManager
): IntentSender {
    // Match only Bluetooth devices whose service UUID matches this pattern.
    // For this demo we will match our GATTServerSample
    val deviceFilter = BluetoothLeDeviceFilter.Builder()
        .setNamePattern(Pattern.compile("ILCE"))
        .build()

    val pairingRequest: AssociationRequest = AssociationRequest.Builder()
        // Find only devices that match this request filter.
        //.addDeviceFilter(deviceFilter)
        // Stop scanning as soon as one device matching the filter is found.
        //  .setSingleDevice(true)
        .build()

    val result = CompletableDeferred<IntentSender>()

    val callback = object : CompanionDeviceManager.Callback() {
        override fun onAssociationPending(intentSender: IntentSender) {
            result.complete(intentSender)
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onDeviceFound(intentSender: IntentSender) {
            result.complete(intentSender)
        }

        override fun onAssociationCreated(associationInfo: AssociationInfo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Timber.i("Association created: ${associationInfo.displayName} (${associationInfo.id})")
                //startDevicePresenceObservation(deviceManager, associationInfo.toAssociatedDevice())
            }
        }

        override fun onFailure(errorMessage: CharSequence?) {
            result.completeExceptionally(IllegalStateException(errorMessage?.toString().orEmpty()))
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val executor = Executor { it.run() }
        deviceManager.associate(pairingRequest, executor, callback)
    } else {
        deviceManager.associate(pairingRequest, callback, null)
    }

    return result.await()
}
