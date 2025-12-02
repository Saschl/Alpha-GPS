package com.saschl.cameragps.service

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.annotation.RequiresPermission

class CameraConnectionManager(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val gattCallback: BluetoothGattCallback
) {

    data class CameraConnectionConfig(
        val gatt: BluetoothGatt,
        val state: Int = -1,
        val writeCharacteristic: BluetoothGattCharacteristic? = null
    )

    private val connections = mutableMapOf<String, CameraConnectionConfig>()

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(config: String): Boolean {
        // Check if already connected
        if (connections.containsKey(config)) {
            return true
        }

        val device: BluetoothDevice = bluetoothManager.adapter.getRemoteDevice(config)

        val gatt = device.connectGatt(context, true, gattCallback)
        connections[config] = CameraConnectionConfig(gatt = gatt)
        LocationSenderService.activeTransmissions[config] = false

        return true
    }


    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect(address: String) {
        connections[address]?.let { config ->
            config.gatt.disconnect()
            config.gatt.close()
        }
        connections.remove(address)
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectAll() {
        connections.values.forEach { config ->
            config.gatt.disconnect()
            config.gatt.close()
        }
        connections.clear()
    }

    fun isConnected(config: String): Boolean {
        return connections.containsKey(config)
    }

    fun getConnectedCameras(): Set<String> {
        return connections.keys.toSet()
    }

    fun getActiveCameras(): Set<String> {
        return connections.filter { it.value.state == BluetoothGatt.GATT_SUCCESS }.keys.toSet()
    }

    fun pauseDevice(address: String) {
        connections[address]?.let { config ->
            connections[address] = config.copy(state = BluetoothGatt.GATT_FAILURE)
        }
        LocationSenderService.activeTransmissions[address]?.let {
            LocationSenderService.activeTransmissions[address] = false
        }
    }

    fun resumeDevice(address: String) {
        connections[address]?.let { config ->
            connections[address] = config.copy(state = BluetoothGatt.GATT_SUCCESS)
        }
        LocationSenderService.activeTransmissions[address]?.let {
            LocationSenderService.activeTransmissions[address] = true
        }
    }

    fun getActiveConnections(): Collection<CameraConnectionConfig> {
        return connections.values.filter { it.state == BluetoothGatt.GATT_SUCCESS }.toList()
    }

    fun setWriteCharacteristic(
        address: String,
        writeLocationCharacteristic: BluetoothGattCharacteristic?
    ) {
        connections[address]?.let { config ->
            connections[address] = config.copy(writeCharacteristic = writeLocationCharacteristic)
        }
    }
}