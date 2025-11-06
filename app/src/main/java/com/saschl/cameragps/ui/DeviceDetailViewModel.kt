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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds


data class ServiceToggleState(
    val buttonEnabled: Boolean = true,
)

class DeviceDetailViewModel : ViewModel() {

    // Expose screen UI state
    private val _uiState = MutableStateFlow(ServiceToggleState())
    val uiState: StateFlow<ServiceToggleState> = _uiState.asStateFlow()
    fun stopServiceWithDelay(
        context: Context,
        device: AssociatedDeviceCompat,
        deviceManager: CompanionDeviceManager
    ) {
        viewModelScope.launch {
            val intent = Intent(context, LocationSenderService::class.java)
            intent.putExtra("address", device.address.uppercase())

            Timber.i("Stopping LocationSenderService from detail for device ${device.address}")
            _uiState.update { it.copy(buttonEnabled = false) }
            context.stopService(intent)
            delay(2.seconds)
            startDevicePresenceObservation(deviceManager, device)
            _uiState.update { it.copy(buttonEnabled = true) }

        }
    }
}

