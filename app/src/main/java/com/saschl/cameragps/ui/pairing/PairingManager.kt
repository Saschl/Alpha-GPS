package com.saschl.cameragps.ui.pairing

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import com.saschl.cameragps.service.AssociatedDeviceCompat
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun isDevicePaired(adapter: BluetoothAdapter?, deviceAddress: String): Boolean {
    
    // To avoid weird state if bluetooth is disabled
    if (adapter?.isEnabled != true) return true

    return adapter.bondedDevices?.any {
        it.address == deviceAddress.uppercase(Locale.getDefault())
    } ?: false

}

@RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT])
fun initiateBluetoothPairing(device: BluetoothDevice): Boolean {
    return try {
        @Suppress("MissingPermission") // Permission is checked by caller
        device.createBond()
    } catch (e: SecurityException) {
        Timber.e(e, "Failed to initiate pairing due to security exception")
        false
    } catch (e: Exception) {
        Timber.e(e, "Failed to initiate pairing")
        false
    }
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun BondingStateListener(
    device: BluetoothDevice,
    onBondingSuccess: () -> Unit,
    onBondingFailed: () -> Unit
) {
    val context = LocalContext.current
    val currentOnBondingSuccess by rememberUpdatedState(onBondingSuccess)
    val currentOnBondingFailed by rememberUpdatedState(onBondingFailed)

    DisposableEffect(device) {
        val bondingReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val bondState = intent.getIntExtra(
                            BluetoothDevice.EXTRA_BOND_STATE,
                            BluetoothDevice.BOND_NONE
                        )
                        val bondedDevice =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE,
                                    BluetoothDevice::class.java
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }

                        if (bondedDevice?.address == device.address) {
                            when (bondState) {
                                BluetoothDevice.BOND_BONDED -> {
                                    currentOnBondingSuccess()
                                }

                                BluetoothDevice.BOND_NONE -> {
                                    currentOnBondingFailed()
                                }
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(bondingReceiver, filter)

        onDispose {
            context.unregisterReceiver(bondingReceiver)
        }
    }
}

// =============================================================================
// High-Level Pairing Manager
// =============================================================================

/**
 * A centralized pairing manager that handles the complete pairing flow for devices.
 * This component manages pairing dialogs, state transitions, and device presence observation.
 */
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun PairingManager(
    device: AssociatedDeviceCompat,
    deviceManager: CompanionDeviceManager,
    onPairingComplete: () -> Unit = {},
    onPairingCancelled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pairingDialogState by remember(device) {
        mutableStateOf(PairingDialogState.Hidden)
    }

    // Check if pairing is needed when device changes
    LaunchedEffect(device) {
        scope.launch {
            val bluetoothManager = context.getSystemService<BluetoothManager>()
            val adapter = bluetoothManager?.adapter
            val bluetoothDevice = device.device ?: adapter?.getRemoteDevice(device.address)

            if (bluetoothDevice != null && !isDevicePaired(adapter, device.address)) {
                Timber.i("Device ${device.name} not paired, showing pairing confirmation dialog")
                pairingDialogState = PairingDialogState(device = device, isVisible = true)
            } else if (isDevicePaired(adapter, device.address)) {
                Timber.i("Device ${device.name} is already paired")
                //startDevicePresenceObservation(deviceManager, device)
                onPairingComplete()
            }
        }
    }

    // Show pairing dialog when needed
    if (pairingDialogState.isVisible && pairingDialogState.device != null) {
        PairingConfirmationDialogWithLoading(
            deviceName = pairingDialogState.device!!.name,
            isPairing = pairingDialogState.isPairing,
            pairingResult = pairingDialogState.pairingResult,
            onConfirm = {
                handlePairingConfirmation(
                    context = context,
                    device = device,
                    currentState = pairingDialogState,
                    onStateUpdate = { pairingDialogState = it }
                )
            },
            onDismiss = {
                Timber.i("User cancelled pairing for ${device.name}")
                pairingDialogState = PairingDialogState.Hidden
                onPairingCancelled()
            },
            onRetry = {
                pairingDialogState = pairingDialogState.copy(
                    isPairing = false,
                    pairingResult = null
                )
            }
        )
    }

    // Listen for bonding state changes
    if (pairingDialogState.isPairing && pairingDialogState.device != null) {
        val bluetoothManager = context.getSystemService<BluetoothManager>()
        val adapter = bluetoothManager?.adapter
        val bluetoothDevice = pairingDialogState.device!!.device ?: adapter?.getRemoteDevice(
            pairingDialogState.device!!.address
        )

        if (bluetoothDevice != null) {
            BondingStateListener(
                device = bluetoothDevice,
                onBondingSuccess = {
                    Timber.i("Pairing successful for ${device.name}")
                    pairingDialogState = pairingDialogState.copy(
                        isPairing = false,
                        pairingResult = PairingResult.SUCCESS
                    )

                    scope.launch {
                        onPairingComplete()
                    }
                },
                onBondingFailed = {
                    Timber.w("Pairing failed for ${device.name}")
                    pairingDialogState = pairingDialogState.copy(
                        isPairing = false,
                        pairingResult = PairingResult.FAILED
                    )
                }
            )
        }
    }
}

/**
 * Handles the pairing confirmation logic
 */
@SuppressLint("MissingPermission")
private fun handlePairingConfirmation(
    context: Context,
    device: AssociatedDeviceCompat,
    currentState: PairingDialogState,
    onStateUpdate: (PairingDialogState) -> Unit
) {
    onStateUpdate(currentState.copy(isPairing = true))

    val bluetoothManager = context.getSystemService<BluetoothManager>()
    val adapter = bluetoothManager?.adapter
    val bluetoothDevice = device.device ?: adapter?.getRemoteDevice(device.address)

    if (bluetoothDevice != null) {
        Timber.i("User confirmed pairing for ${device.name}, initiating pairing")
        val pairingResult = initiateBluetoothPairing(bluetoothDevice)
        if (!pairingResult) {
            Timber.w("Failed to initiate pairing for ${device.name}")
            onStateUpdate(
                currentState.copy(
                    isPairing = false,
                    pairingResult = PairingResult.FAILED
                )
            )
        }
    } else {
        onStateUpdate(
            currentState.copy(
                isPairing = false,
                pairingResult = PairingResult.FAILED
            )
        )
    }
}

/**
 * Starts device presence observation after successful pairing
 */
fun startDevicePresenceObservation(
    deviceManager: CompanionDeviceManager,
    device: AssociatedDeviceCompat
) {
    Timber.i("Starting device presence observation for ${device.name}")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        deviceManager.startObservingDevicePresence(
            ObservingDevicePresenceRequest.Builder().setAssociationId(device.id).build()
        )
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            deviceManager.startObservingDevicePresence(device.address)
        }
    }
}
