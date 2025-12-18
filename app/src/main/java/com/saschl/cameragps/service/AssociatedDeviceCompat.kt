package com.saschl.cameragps.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * Wrapper for the different type of classes the CDM returns
 */
data class AssociatedDeviceCompat(
    val id: Int,
    val address: String,
    var name: String,
    val device: BluetoothDevice?,
    var isPaired: Boolean = false
)


@SuppressLint("MissingPermission")
internal fun CompanionDeviceManager.getAssociatedDevices(adapter: BluetoothAdapter): List<AssociatedDeviceCompat> {
    val associatedDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        myAssociations.map {
            it.toAssociatedDevice().apply {
                // Check if device is Bluetooth paired
                isPaired = adapter.bondedDevices?.any { bondedDevice ->
                    bondedDevice.address.equals(address, ignoreCase = true)
                } ?: false
            }
        }
    } else {
        // Before Android 34 we can only get the MAC.
        @Suppress("DEPRECATION")
        associations.map {
            val deviceAddress = it.uppercase(Locale.getDefault())
            AssociatedDeviceCompat(
                id = -1,
                address = deviceAddress,
                name = adapter.getRemoteDevice(it.uppercase()).name ?: "N/A",
                device = null,
                isPaired = adapter.bondedDevices?.any { bondedDevice ->
                    bondedDevice.address == deviceAddress
                } ?: false
            )
        }
    }
    return associatedDevice
}


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun AssociationInfo.toAssociatedDevice() = AssociatedDeviceCompat(
    id = id,
    address = deviceMacAddress?.toString().let { it?.uppercase(Locale.getDefault()) } ?: "N/A",
    name = displayName?.ifBlank { "N/A" }?.toString() ?: "N/A",
    device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        associatedDevice?.bleDevice?.device
    } else {
        null
    },
)
