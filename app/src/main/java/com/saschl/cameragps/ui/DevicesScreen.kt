package com.saschl.cameragps.ui

import android.annotation.SuppressLint
import android.companion.CompanionDeviceManager
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.saschl.cameragps.R
import com.saschl.cameragps.database.LogDatabase
import com.saschl.cameragps.service.AssociatedDeviceCompat
import com.saschl.cameragps.service.LocationSenderService
import com.saschl.cameragps.service.pairing.PairingManager
import com.saschl.cameragps.utils.PreferencesManager
import kotlinx.coroutines.launch
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val cameraDeviceDAO = LogDatabase.getDatabase(context.applicationContext).cameraDeviceDao()

    // State for managing pairing after association
    var pendingPairingDevice by remember { mutableStateOf<AssociatedDeviceCompat?>(null) }

    LaunchedEffect(lifecycleState) {
        when (lifecycleState) {
            Lifecycle.State.RESUMED -> {
                Timber.d("App started, will resume transmission for configured devices")
                scope.launch {
                    associatedDevices.forEach {
                        val shouldTransmissionStart =
                            cameraDeviceDAO.isDeviceEnabled(it.address)
                                    && cameraDeviceDAO.isDeviceAlwaysOnEnabled(
                                it.address
                            ) && PreferencesManager.isAppEnabled(context.applicationContext)
                        if (shouldTransmissionStart) {
                            Timber.d("Resuming location transmission for device ${it.address}")
                            val intent = Intent(context, LocationSenderService::class.java)
                            intent.putExtra("address", it.address.uppercase())
                            context.startForegroundService(intent)
                        }
                    }
                }

            }

            else -> { /* No action needed */
            }
        }
    }

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
                                Intent(context, com.saschl.cameragps.ui.HelpActivity::class.java)
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

            // Single device limitation hint
            if (associatedDevices.size >= 1) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.info_24px),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.single_device_limitation_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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
