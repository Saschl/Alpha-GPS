package com.saschl.cameragps.ui.device

import android.annotation.SuppressLint
import android.companion.CompanionDeviceManager
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saschl.cameragps.R
import com.saschl.cameragps.service.AssociatedDeviceCompat
import com.saschl.cameragps.ui.AssociatedDevicesList
import com.saschl.cameragps.ui.HelpActivity
import com.saschl.cameragps.ui.LogViewerActivity
import com.saschl.cameragps.ui.pairing.PairingManager
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun DevicesScreen(
    deviceManager: CompanionDeviceManager,
    isBluetoothEnabled: Boolean,
    isLocationEnabled: Boolean,
    associatedDevices: List<AssociatedDeviceCompat>,
    onDeviceAssociated: (AssociatedDeviceCompat) -> Unit,
    onConnect: (AssociatedDeviceCompat) -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    // State for managing pairing after association
    var pendingPairingDevice by remember { mutableStateOf<AssociatedDeviceCompat?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name_ui),
                        fontWeight = FontWeight.SemiBold
                    )
                },

                actions = {
                    IconButton(
                        onClick = {
                            context.startActivity(
                                Intent(context, HelpActivity::class.java)
                            )
                        }
                    ) {
                        Icon(
                            painterResource(R.drawable.info_24px),
                            contentDescription = stringResource(R.string.help_menu_item),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
                            painterResource(R.drawable.settings_24px),
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
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

            ScanForDevicesMenu(
                deviceManager,
                isBluetoothEnabled,
                isLocationEnabled,
                associatedDevices,
                onSetPairingDevice = { device -> pendingPairingDevice = device },
                onDeviceAssociated = onDeviceAssociated
            )


            AssociatedDevicesList(
                associatedDevices = associatedDevices,
                onConnect = onConnect
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
