package com.saschl.cameragps.ui.pairing

import com.saschl.cameragps.service.AssociatedDeviceCompat

// Data class to hold pairing dialog state
data class PairingDialogState(
    val device: AssociatedDeviceCompat? = null,
    val isVisible: Boolean = false,
    val isPairing: Boolean = false,
    val pairingResult: PairingResult? = null
) {
    companion object {
        val Hidden = PairingDialogState()
    }
}

enum class PairingResult {
    SUCCESS,
    FAILED
}
