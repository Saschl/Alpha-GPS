package com.saschl.cameragps.service

import android.Manifest
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saschl.cameragps.R
import com.saschl.cameragps.database.LogDatabase
import com.saschl.cameragps.service.pairing.startDevicePresenceObservation
import com.saschl.cameragps.ui.DeviceDetailViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun DeviceDetailScreen(
    device: AssociatedDeviceCompat,
    deviceManager: CompanionDeviceManager,
    onDisassociate: (device: AssociatedDeviceCompat) -> Unit,
    onClose: () -> Unit
) {

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val extras = MutableCreationExtras().apply {
        set(
            DeviceDetailViewModel.MY_REPOSITORY_KEY,
            LogDatabase.getDatabase(context).cameraDeviceDao()
        )
    }
    val viewModel: DeviceDetailViewModel = viewModel(
        factory = DeviceDetailViewModel.Factory,
        extras = extras,
        key = device.address
    )
    val cameraDeviceDAO = LogDatabase.getDatabase(context.applicationContext).cameraDeviceDao()

    LaunchedEffect(Unit) {
        viewModel.deviceEnabledFromDB(device.address)
    }
    /*   var isDeviceEnabled by remember { mutableStateOf(false) }

       var keepAlive by remember { mutableStateOf(false) }

       LaunchedEffect(device.address) {
           isDeviceEnabled = cameraDeviceDAO.isDeviceEnabled(device.address)
           keepAlive = cameraDeviceDAO.isDeviceAlwaysOnEnabled(device.address)
       }*/

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name_ui),
                        //  style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },

                navigationIcon = {
                    IconButton(
                        onClick = onClose
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
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
                            painter = painterResource(R.drawable.info_24px),
                            contentDescription = stringResource(R.string.help_menu_item),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }) { innerPadding ->

        BackHandler {
            onClose()
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(0.6f)
                ) {
                    Text(text = "Name: ${device.name} (${device.address})")
                }

                Column(
                    modifier = Modifier.weight(0.4f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { scope.launch { onDisassociate(device) } },
                        border = ButtonDefaults.outlinedButtonBorder().copy(
                            brush = SolidColor(MaterialTheme.colorScheme.error),
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.remove),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.enable_device),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = viewModel.uiState.collectAsState().value.isDeviceEnabled,
                        enabled = !viewModel.uiState.collectAsState().value.isAlwaysOnEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setDeviceEnabled(enabled, device.address)
                            if(!enabled) {

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    deviceManager.stopObservingDevicePresence(device.address)
                                }
                                Timber.i("Stopping LocationSenderService from detail for device ${device.address}")

                                val shutdownIntent =
                                    Intent(context, LocationSenderService::class.java).apply {
                                        action = SonyBluetoothConstants.ACTION_REQUEST_SHUTDOWN
                                        putExtra("address", device.address.uppercase())
                                    }
                                context.startService(shutdownIntent)
                            } else {
                                startDevicePresenceObservation(deviceManager, device)
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.enableConstantly),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = viewModel.uiState.collectAsState().value.isAlwaysOnEnabled,
                        enabled = viewModel.uiState.collectAsState().value.isDeviceEnabled && viewModel.uiState.collectAsState().value.buttonEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setAlwaysOnEnabled(enabled, device, deviceManager, context)
                        }
                    )
                }

                Text(
                    text = stringResource(R.string.always_on_description),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
                    lineHeight = MaterialTheme.typography.labelSmall.lineHeight
                )
            }
        }
    }
}
