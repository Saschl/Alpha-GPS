package com.saschl.cameragps.service

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.annotation.RequiresPermission

class CameraConnectionManager(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val gattCallback: BluetoothGattCallback
) {
    private val connections = mutableMapOf<String, BluetoothGatt>()

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(config: String): Boolean {
        // Check if already connected
        if (connections.containsKey(config)) {
            return true
        }

        val device: BluetoothDevice = bluetoothManager.adapter.getRemoteDevice(config)

        val gatt = device.connectGatt(context, true, gattCallback)
        connections[config] = gatt

        return true
    }


    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect(config: String) {
        connections[config]?.let { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        connections.remove(config)
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectAll() {
        connections.values.forEach { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        connections.clear()
    }

    fun isConnected(config: String): Boolean {
        return connections.containsKey(config)
    }

    fun getConnectedCameras(): Set<String> {
        return connections.keys.toSet()
    }

    fun getBluetoothGattConnections(): MutableCollection<BluetoothGatt> {
        return connections.values
    }
}