package com.saschl.cameragps.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.saschl.cameragps.R
import com.saschl.cameragps.service.AssociatedDeviceCompat
import com.saschl.cameragps.service.pairing.isDevicePaired
import com.saschl.cameragps.utils.DeviceAssociationUtils
import kotlinx.coroutines.launch
import timber.log.Timber

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
@Composable
fun ScanForDevicesMenu(
    deviceManager: CompanionDeviceManager,
    isBluetoothEnabled: Boolean,
    isLocationEnabled: Boolean,
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
                it.data?.let { intent ->
                    DeviceAssociationUtils.getAssociationResult(intent)?.let { device ->
                        // Device association successful, now check if pairing is needed
                        val bluetoothManager = context.getSystemService<BluetoothManager>()
                        val adapter = bluetoothManager?.adapter

                        if (associatedDevices.any { existingDevice -> existingDevice.address == device.address }) {
                            Timber.i("Device ${device.name} already associated, skipping pairing")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                deviceManager.disassociate(device.id)
                                errorMessage = "The device is already associated."
                            } else {
                                @Suppress("DEPRECATION")
                                deviceManager.disassociate(device.address)
                                errorMessage =
                                    "The device was already associated. The association was removed to prevent duplicates. Please try again."
                            }
                            return@let
                        }
                        if (!isDevicePaired(adapter, device.address)) {
                            Timber.i("Device ${device.name} associated but not paired, initiating pairing")
                            onSetPairingDevice(device)
                        } else {
                            Timber.i("Device ${device.name} already paired, completing association")
                            onDeviceAssociated(device)
                        }
                        errorMessage = ""
                    }
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        if (!isBluetoothEnabled) {
            BluetoothWarningCard()
        }

        if (!isLocationEnabled) {
            LocationWarningCard()
        }

        Row {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                text = stringResource(R.string.scan_for_devices),
            )
            Button(
                modifier = Modifier.weight(0.5f),
                // enabled = associatedDevices.isEmpty() && isBluetoothEnabled && isLocationEnabled,
                onClick = {
                    scope.launch {
                        val intentSender =
                            DeviceAssociationUtils.requestDeviceAssociation(deviceManager)
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
