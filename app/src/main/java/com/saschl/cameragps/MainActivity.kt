package com.saschl.cameragps

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.saschl.cameragps.database.LogDatabase
import com.saschl.cameragps.service.FileTree
import com.saschl.cameragps.service.GlobalExceptionHandler
import com.saschl.cameragps.service.LocationSenderService
import com.saschl.cameragps.ui.SettingsScreen
import com.saschl.cameragps.ui.WelcomeScreen
import com.saschl.cameragps.ui.device.CameraDeviceManager
import com.saschl.cameragps.ui.theme.CameraGpsTheme
import com.saschl.cameragps.utils.PreferencesManager
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Timber.treeCount == 0) {
            val logLevel = PreferencesManager.logLevel(this)
            FileTree.initialize(this)
            Timber.plant(FileTree(this, logLevel))

            // Set up global exception handler to log crashes
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(defaultHandler))
        }

        setContent {
            CameraGpsTheme {
                AppContent()
            }
        }
    }

    @Composable
    private fun AppContent() {
        val context = LocalContext.current
        var showWelcome by remember { mutableStateOf(PreferencesManager.isFirstLaunch(context)) }
        var showSettings by remember { mutableStateOf(false) }
        val cameraDeviceDAO = LogDatabase.getDatabase(context).cameraDeviceDao()
        val scope = rememberCoroutineScope()

        val lifecycleState by ProcessLifecycleOwner.get().lifecycle.currentStateFlow.collectAsState()

        LaunchedEffect(lifecycleState) {
            Timber.d("Lifecycle state changed: $lifecycleState")
            when (lifecycleState) {
                Lifecycle.State.RESUMED -> {
                    showWelcome = PreferencesManager.isFirstLaunch(context)
                    if (showWelcome) {
                        showSettings = false
                    }
                }
                else -> { /* No action needed */
                }
            }
        }

        LaunchedEffect(lifecycleState) {
            when (lifecycleState) {
                Lifecycle.State.RESUMED -> {
                    Timber.d("App started, will resume transmission for configured devices")
                    //  scope.launch {
                    cameraDeviceDAO.getAllCameraDevices().forEach {
                        val shouldTransmissionStart =
                            it.deviceEnabled
                                    && it.alwaysOnEnabled
                                    && PreferencesManager.isAppEnabled(context.applicationContext)
                        if (shouldTransmissionStart) {
                            Timber.d("Resuming location transmission for device ${it.mac}")
                            val intent = Intent(context, LocationSenderService::class.java)
                            intent.putExtra("address", it.mac.uppercase())
                            context.startForegroundService(intent)
                        }
                    }
                    //  }

                }

                else -> { /* No action needed */
                }
            }
        }

        // Check if battery optimization dialog should be shown
        val powerManager = context.getSystemService<PowerManager>()
        val isIgnoringBatteryOptimizations =
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

        var showBatteryOptimizationDialog by remember {
            mutableStateOf(
                !PreferencesManager.isBatteryOptimizationDialogDismissed(context)
            )
        }

        when {
            showWelcome -> {
                WelcomeScreen(
                    onGetStarted = {
                        PreferencesManager.setFirstLaunchCompleted(context)
                        showWelcome = false
                        Timber.i("Welcome screen completed, navigating to main app")
                    }
                )
            }

            showSettings -> {
                SettingsScreen(
                    onBackClick = {
                        showSettings = false
                    }
                )
            }

            else -> {
                // Check battery optimization status when entering main screen
                LaunchedEffect(Unit) {
                    val currentBatteryStatus =
                        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

                    // Update dialog visibility based on current status
                    showBatteryOptimizationDialog =
                        !PreferencesManager.isBatteryOptimizationDialogDismissed(context) &&
                                !currentBatteryStatus
                }

                // Show the main camera device manager
                CameraDeviceManager(
                    onSettingsClick = {
                        showSettings = true
                    }
                )

                // Show battery optimization dialog overlay if needed
                /*if (showBatteryOptimizationDialog) {
                    BatteryOptimizationDialog(
                        onDismiss = {
                            showBatteryOptimizationDialog = false
                        }
                    )
                }*/
            }
        }
    }
}
