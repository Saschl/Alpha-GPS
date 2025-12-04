package com.saschl.cameragps.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.saschl.cameragps.R
import com.saschl.cameragps.utils.PreferencesManager
import timber.log.Timber


@Composable
fun BatteryOptimizationDialog(
    onDismiss: () -> Unit
) {
    when {
        isXiaomiDevice() -> VendorSpecificBatteryOptimizationDialog(
            onDismiss = onDismiss,
            vendor = DeviceVendor.XIAOMI
        )

        isOppoDevice() -> VendorSpecificBatteryOptimizationDialog(
            onDismiss = onDismiss,
            vendor = DeviceVendor.OPPO
        )

        else -> StandardBatteryOptimizationDialog(onDismiss = onDismiss)
    }
}

enum class DeviceVendor {
    XIAOMI,
    OPPO
}

@Composable
private fun VendorSpecificBatteryOptimizationDialog(
    onDismiss: () -> Unit,
    vendor: DeviceVendor
) {
    val context = LocalContext.current

    val titleRes = when (vendor) {
        DeviceVendor.XIAOMI -> R.string.battery_optimization_xiaomi_title
        DeviceVendor.OPPO -> R.string.battery_optimization_oppo_title
    }

    val messageRes = when (vendor) {
        DeviceVendor.XIAOMI -> R.string.battery_optimization_xiaomi_message
        DeviceVendor.OPPO -> R.string.battery_optimization_oppo_message
    }

    val batteryButtonRes = when (vendor) {
        DeviceVendor.XIAOMI -> R.string.battery_optimization_xiaomi_battery
        DeviceVendor.OPPO -> R.string.battery_optimization_oppo_battery
    }

    val autostartButtonRes = when (vendor) {
        DeviceVendor.XIAOMI -> R.string.battery_optimization_xiaomi_autostart
        DeviceVendor.OPPO -> R.string.battery_optimization_oppo_autostart
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Battery optimization settings button
                TextButton(
                    onClick = {
                        try {
                            val intent = when (vendor) {
                                DeviceVendor.XIAOMI -> Intent().apply {
                                    component = ComponentName(
                                        "com.miui.securitycenter",
                                        "com.miui.appmanager.AppManagerMainActivity"
                                    )
                                    putExtra("package_name", context.packageName)
                                    putExtra(
                                        "package_label",
                                        context.applicationInfo.loadLabel(context.packageManager)
                                    )
                                }

                                DeviceVendor.OPPO -> Intent().apply {
                                    component = ComponentName(
                                        "com.coloros.safecenter",
                                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                                    )
                                }
                            }
                            context.startActivity(intent)
                            Timber.i("Opened ${vendor.name} battery optimization settings")
                        } catch (e: Exception) {
                            Timber.e(
                                e,
                                "Failed to open ${vendor.name} battery optimization settings"
                            )
                            // Fallback to general app settings
                            try {
                                val fallbackIntent =
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                context.startActivity(fallbackIntent)
                                Timber.i("Opened app details settings as fallback")
                            } catch (fallbackException: Exception) {
                                Timber.e(fallbackException, "Failed to open any settings")
                            }
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(batteryButtonRes),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Autostart settings button
                TextButton(
                    onClick = {
                        try {
                            val autostartIntent = when (vendor) {
                                DeviceVendor.XIAOMI -> Intent().apply {
                                    component = ComponentName(
                                        "com.miui.securitycenter",
                                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                                    )
                                }

                                DeviceVendor.OPPO -> Intent().apply {
                                    component = ComponentName(
                                        "com.coloros.safecenter",
                                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                                    )
                                }
                            }
                            context.startActivity(autostartIntent)
                            Timber.i("Opened ${vendor.name} autostart settings")
                        } catch (e: Exception) {
                            Timber.e(
                                e,
                                "Failed to open ${vendor.name} autostart settings, trying fallback"
                            )
                            try {
                                // Fallback to general app settings
                                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(fallbackIntent)
                                Timber.i("Opened app details settings as fallback")
                            } catch (fallbackException: Exception) {
                                Timber.e(fallbackException, "Failed to open any settings")
                            }
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(autostartButtonRes),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Don't show again button
                TextButton(
                    onClick = {
                        PreferencesManager.setBatteryOptimizationDialogDismissed(context, true)
                        Timber.i("${vendor.name} battery optimization dialog dismissed permanently")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.battery_optimization_dont_show))
                }

                // Cancel button
                TextButton(
                    onClick = {
                        Timber.i("${vendor.name} battery optimization dialog cancelled")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.battery_optimization_cancel))
                }
            }
        }
    )
}

@Composable
private fun StandardBatteryOptimizationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.battery_optimization_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.battery_optimization_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = {
                        try {
                            val uri = "package:${context.packageName}".toUri()
                            // should be safe to use as we do not request the permission and let the user decide
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            context.startActivity(intent)
                            Timber.i("Opened battery optimization settings for package: ${context.packageName}")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to open battery optimization settings, trying fallback")
                            // Fallback to general settings if specific intent fails
                            try {
                                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(fallbackIntent)
                                Timber.i("Opened general battery optimization settings")
                            } catch (fallbackException: Exception) {
                                Timber.e(fallbackException, "Failed to open any battery optimization settings")
                            }
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.battery_optimization_proceed),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                TextButton(
                    onClick = {
                        PreferencesManager.setBatteryOptimizationDialogDismissed(context, true)
                        Timber.i("Battery optimization dialog dismissed permanently")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.battery_optimization_dont_show))
                }
                
                TextButton(
                    onClick = {
                        Timber.i("Battery optimization dialog cancelled")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.battery_optimization_cancel))
                }
            }
        }
    )
}

fun isXiaomiDevice(): Boolean {
    return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) || Build.MANUFACTURER.equals("Redmi", ignoreCase = true)
}

fun isOppoDevice(): Boolean {
    return Build.MANUFACTURER.equals(
        "Oppo",
        ignoreCase = true
    ) || Build.MANUFACTURER.equals(
        "Realme",
        ignoreCase = true
    ) || Build.MANUFACTURER.equals("OnePlus", ignoreCase = true)
}

