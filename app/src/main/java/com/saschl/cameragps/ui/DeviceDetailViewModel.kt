package com.saschl.cameragps.ui

import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saschl.cameragps.service.AssociatedDeviceCompat
import com.saschl.cameragps.service.LocationSenderService
import com.saschl.cameragps.service.pairing.startDevicePresenceObservation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

class DeviceDetailViewModel : ViewModel() {

    fun stopServiceWithDelay(
        context: Context,
        device: AssociatedDeviceCompat,
        deviceManager: CompanionDeviceManager
    ) {
        viewModelScope.launch {
            val intent = Intent(context, LocationSenderService::class.java)
            intent.putExtra("address", device.address.uppercase())

            Timber.i("Stopping LocationSenderService from detail for device ${device.address}")
            context.stopService(intent)
            delay(2.seconds)

            startDevicePresenceObservation(deviceManager, device)
        }
    }
}

