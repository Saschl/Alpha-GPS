package com.saschl.cameragps.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import com.saschl.cameragps.R
import com.saschl.cameragps.database.LogDatabase
import com.saschl.cameragps.database.devices.CameraDevice
import com.saschl.cameragps.service.FileTree
import com.saschl.cameragps.service.LocationSenderService
import com.saschl.cameragps.utils.BatteryOptimizationUtil
import com.saschl.cameragps.utils.LanguageManager
import com.saschl.cameragps.utils.PreferencesManager
import timber.log.Timber
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val deviceDao = remember { LogDatabase.getDatabase(context).cameraDeviceDao() }
    var isAppEnabled by remember {
        mutableStateOf(PreferencesManager.isAppEnabled(context))
    }

    val currentLanguage = LanguageManager.getCurrentLanguage(context)
    var showLanguageDialog by remember { mutableStateOf(false) }
    var debugPanelCounter by remember {
        mutableIntStateOf(0)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        modifier = Modifier.clickable {
                            debugPanelCounter++
                        },
                        text = stringResource(R.string.settings),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { paddingValues ->
        BackHandler {
            onBackClick()
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_controls),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.enable_app),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.enable_app_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = isAppEnabled,
                                onCheckedChange = { enabled ->
                                    isAppEnabled = enabled
                                    PreferencesManager.setAppEnabled(context, enabled)
                                    context.stopService(
                                        Intent(
                                            context.applicationContext,
                                            LocationSenderService::class.java
                                        )
                                    )
                                }
                            )

                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(0.6f),

                                ) {
                                Text(
                                    text = stringResource(R.string.enable_auto_start),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.enable_auto_start_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                            }
                            var isAutoStartAfterRebootEnabled by remember {
                                mutableStateOf(
                                    PreferencesManager.getAutoStartAfterBootEnabled(
                                        context
                                    )
                                )
                            }
                            Switch(
                                checked = isAutoStartAfterRebootEnabled,
                                onCheckedChange = { enabled ->
                                    PreferencesManager.setAutoStartAfterBootEnabled(
                                        context,
                                        enabled
                                    )
                                    isAutoStartAfterRebootEnabled = enabled
                                }
                            )

                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(0.6f),
                                horizontalAlignment = Alignment.CenterHorizontally

                            ) {

                                Button(
                                    onClick = {
                                        val toast = Toast.makeText(
                                            context, R.string.will_show_welcome,
                                            Toast.LENGTH_SHORT
                                        )
                                        toast.show()
                                        PreferencesManager.showFirstLaunch(context)
                                    },
                                ) {
                                    Text(text = stringResource(R.string.reset_welcome))
                                }
                            }


                        }
                    }
                }
            }

            item {
                // Language Settings Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.language_settings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLanguageDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.language_selection),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = currentLanguage?.displayName
                                            ?: "System Default (${Locale.getDefault().displayName})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                // Log Level Settings Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.log_settings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        val currentLogLevel = remember {
                            mutableIntStateOf(PreferencesManager.logLevel(context))
                        }
                        var showLogLevelDialog by remember { mutableStateOf(false) }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLogLevelDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.log_level),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = getLogLevelName(currentLogLevel.intValue),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (showLogLevelDialog) {
                            LogLevelSelectionDialog(
                                currentLevel = currentLogLevel.intValue,
                                onLevelSelected = { level ->
                                    currentLogLevel.intValue = level
                                    PreferencesManager.setLogLevel(context, level)
                                    showLogLevelDialog = false
                                    Timber.uprootAll()
                                    Timber.plant(FileTree(context, level))
                                },
                                onDismiss = { showLogLevelDialog = false }
                            )
                        }
                    }
                }
            }

            item {
                // Battery Optimization Settings Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.battery_optimization_settings_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = stringResource(R.string.battery_optimization_settings_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // Battery Optimization Button
                        OutlinedButton(
                            onClick = {
                                try {
                                    val uri = "package:${context.packageName}".toUri()
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        uri
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                    Timber.i("Opened battery optimization settings for package: ${context.packageName}")
                                } catch (e: Exception) {
                                    Timber.e(
                                        e,
                                        "Failed to open battery optimization settings, trying fallback"
                                    )
                                    try {
                                        val fallbackIntent =
                                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(fallbackIntent)
                                        Timber.i("Opened general battery optimization settings")
                                    } catch (fallbackException: Exception) {
                                        Timber.e(
                                            fallbackException,
                                            "Failed to open any battery optimization settings"
                                        )
                                        Toast.makeText(
                                            context,
                                            R.string.battery_optimization_open_failed,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.battery_optimization_open_settings),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Autostart Settings Button (vendor-specific)
                        val autostartIntent =
                            remember { BatteryOptimizationUtil.getResolveableComponentName(context) }

                        if (autostartIntent != null) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(autostartIntent)
                                        Timber.i("Opened autostart settings")
                                    } catch (e: Exception) {
                                        Timber.e(
                                            e,
                                            "Failed to open autostart settings, trying fallback"
                                        )
                                        try {
                                            val fallbackIntent =
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.fromParts(
                                                        "package",
                                                        context.packageName,
                                                        null
                                                    )
                                                }
                                            context.startActivity(fallbackIntent)
                                            Timber.i("Opened app details settings as fallback")
                                        } catch (fallbackException: Exception) {
                                            Timber.e(
                                                fallbackException,
                                                "Failed to open any settings"
                                            )
                                            Toast.makeText(
                                                context,
                                                R.string.battery_optimization_open_failed,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.battery_optimization_open_autostart),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // App Details Settings Button
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent =
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data =
                                                Uri.fromParts("package", context.packageName, null)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    context.startActivity(intent)
                                    Timber.i("Opened app details settings")
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to open app details settings")
                                    Toast.makeText(
                                        context,
                                        R.string.battery_optimization_open_failed,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.battery_optimization_open_app_settings),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (debugPanelCounter >= 5) {
                item {
                    // Debug Panel
                    ReviewHintDebugPanel()
                }
                item {
                    // RestartReceiver Test Button
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Debug: RestartReceiver Test",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Button(
                                    onClick = {
                                        val broadcastIntent = Intent(
                                            context,
                                            com.saschl.cameragps.service.RestartReceiver::class.java
                                        )
                                        broadcastIntent.putExtra("was_running", true)
                                        context.sendBroadcast(broadcastIntent)
                                        Toast.makeText(
                                            context,
                                            "RestartReceiver broadcast sent",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        Timber.d("Manual RestartReceiver broadcast sent from Settings")
                                    }
                                ) {
                                    Text(text = "Send RestartReceiver Broadcast")
                                }

                                Text(
                                    text = "This sends a broadcast to restart the LocationSenderService",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Language Selection Dialog
        if (showLanguageDialog) {
            LanguageSelectionDialog(
                currentLanguage = currentLanguage,
                onLanguageUnset = {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                },
                onLanguageSelected = { language ->
                    val activity = context as? androidx.activity.ComponentActivity
                    activity?.let {
                        LanguageManager.applyLanguageToActivity(
                            it,
                            language
                        )
                    }
                    showLanguageDialog = false
                },
                onDismiss = { showLanguageDialog = false }
            )
        }
    }
}

@Composable
private fun LanguageSelectionDialog(
    currentLanguage: Locale?,
    onLanguageSelected: (Locale) -> Unit,
    onLanguageUnset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.language_selection),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            LazyColumn {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageUnset() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentLanguage == null,
                            onClick = onLanguageUnset
                        )
                        Text(
                            text = "System Default",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                items(LanguageManager.SupportedLanguage.getSupportedLocales()) { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(language) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language == currentLanguage,
                            onClick = { onLanguageSelected(language) }
                        )
                        Text(
                            text = language.displayName,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}

private fun getLogLevelName(level: Int): String {

    // TODO refactor this
    return when (level) {
        android.util.Log.VERBOSE -> "VERBOSE"
        android.util.Log.DEBUG -> "DEBUG"
        android.util.Log.INFO -> "INFO"
        android.util.Log.WARN -> "WARN"
        android.util.Log.ERROR -> "ERROR"
        else -> "DEBUG"
    }
}

@Composable
private fun LogLevelSelectionDialog(
    currentLevel: Int,
    onLevelSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // TODO refactor this
    val logLevels = listOf(
        android.util.Log.VERBOSE to "VERBOSE",
        android.util.Log.DEBUG to "DEBUG",
        android.util.Log.INFO to "INFO",
        android.util.Log.WARN to "WARN",
        android.util.Log.ERROR to "ERROR"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.log_level),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            LazyColumn {
                items(logLevels) { (level, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLevelSelected(level) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = level == currentLevel,
                            onClick = { onLevelSelected(level) }
                        )
                        Text(
                            text = name,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}


@Composable
private fun DeviceItem(
    device: CameraDevice,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = device.deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = device.mac,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (device.alwaysOnEnabled) {
                    Text(
                        text = stringResource(R.string.always_on_enabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    painter = painterResource(R.drawable.delete_24px),
                    contentDescription = stringResource(R.string.delete_device),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(text = stringResource(R.string.delete_device))
            },
            text = {
                Text(
                    text = stringResource(R.string.delete_device_confirmation, device.deviceName)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(R.string.cancel_button))
                }
            }
        )
    }
}
