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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saschl.cameragps.R
import com.saschl.cameragps.service.pairing.startDevicePresenceObservation
import com.saschl.cameragps.ui.DeviceDetailViewModel
import com.saschl.cameragps.utils.PreferencesManager
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
    val viewModel: DeviceDetailViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isDeviceEnabled by remember {
        mutableStateOf(PreferencesManager.isDeviceEnabled(context, device.address))
    }

    var keepAlive by remember {
        mutableStateOf(PreferencesManager.isKeepAliveEnabled(context, device.address))
    }

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
                        checked = isDeviceEnabled,
                        enabled = !keepAlive,
                        onCheckedChange = { enabled ->
                            isDeviceEnabled = enabled
                            PreferencesManager.setDeviceEnabled(context, device.address, enabled)
                            if(!enabled) {

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    deviceManager.stopObservingDevicePresence(device.address)
                                }
                                Timber.i("Stopping LocationSenderService from detail for device ${device.address}")
                                context.stopService(Intent(context.applicationContext, LocationSenderService::class.java))
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
                        checked = keepAlive,
                        enabled = isDeviceEnabled && viewModel.uiState.collectAsState().value.buttonEnabled,
                        onCheckedChange = { enabled ->
                            keepAlive = enabled
                            PreferencesManager.setKeepAliveEnabled(context, device.address, enabled)
                            val intent = Intent(context, LocationSenderService::class.java)
                            intent.putExtra("address", device.address.uppercase())
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    deviceManager.stopObservingDevicePresence(device.address)
                                }

                                context.startForegroundService(intent)
                            } else {
                                viewModel.stopServiceWithDelay(context, device, deviceManager)
                            }
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
