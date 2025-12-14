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
import com.saschl.cameragps.ui.SentryConsentDialog
import com.saschl.cameragps.ui.SettingsScreen
import com.saschl.cameragps.ui.WelcomeScreen
import com.saschl.cameragps.ui.device.CameraDeviceManager
import com.saschl.cameragps.ui.theme.CameraGpsTheme
import com.saschl.cameragps.utils.PreferencesManager
import com.saschl.cameragps.utils.SentryInit
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // waiting for view to draw to better represent a captured error with a screenshot
        /*   findViewById<android.view.View>(android.R.id.content).viewTreeObserver.addOnGlobalLayoutListener {

               throw Exception("Eggs1000 :)")

           }*/
        // Sentry will be initialized by the consent dialog or if already consented
        if (PreferencesManager.sentryEnabled(this) && PreferencesManager.isSentryConsentDialogDismissed(
                this
            )
        ) {
            SentryInit.initSentry(this)
        }

        if (Timber.forest().find { it is FileTree } == null) {
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
                }

                else -> { /* No action needed */
                }
            }
        }

        // Check if battery optimization dialog should be shown
        val powerManager = context.getSystemService<PowerManager>()

        var showSentryDialog by remember {
            mutableStateOf(
                !PreferencesManager.isSentryConsentDialogDismissed(context)
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
                LaunchedEffect(Unit) {

                    // Update dialog visibility based on current status
                    showSentryDialog =
                        !PreferencesManager.isSentryConsentDialogDismissed(context)
                }

                // Show the main camera device manager
                CameraDeviceManager(
                    onSettingsClick = {
                        showSettings = true
                    }
                )

                if (showSentryDialog) {
                    SentryConsentDialog(
                        onDismiss = {
                            showSentryDialog = false
                        }
                    )
                }
            }
        }
    }
}
