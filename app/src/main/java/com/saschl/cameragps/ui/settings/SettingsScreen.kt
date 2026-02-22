package com.saschl.cameragps.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saschl.cameragps.R
import com.saschl.cameragps.ui.ReviewHintDebugPanel
import com.saschl.cameragps.utils.LanguageManager
import com.saschl.cameragps.utils.PreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var isAppEnabled by remember {
        mutableStateOf(PreferencesManager.isAppEnabled(context))
    }
    val currentLanguage = LanguageManager.getCurrentLanguage(context)
    var debugPanelCounter by remember { mutableIntStateOf(0) }
    var locationProvider by remember {
        mutableStateOf(PreferencesManager.getLocationProvider(context))
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
                AppControlsCard(
                    isAppEnabled = isAppEnabled,
                    onAppEnabledChange = { enabled ->
                        isAppEnabled = enabled
                        PreferencesManager.setAppEnabled(context, enabled)
                    }
                )
            }
            item {
                LocationProviderCard(
                    locationProvider = locationProvider,
                    onProviderChange = { provider ->
                        locationProvider = provider
                        PreferencesManager.setLocationProvider(context, provider)
                    }
                )
            }
            item {
                BatteryOptimizationCard()
            }
            item {
                LanguageSettingsCard(
                    currentLanguage = currentLanguage,
                    onLanguageSelected = { language ->
                        val activity = context as? androidx.activity.ComponentActivity
                        activity?.let {
                            LanguageManager.applyLanguageToActivity(it, language)
                        }
                    }
                )
            }
            item {
                LogLevelSettingsCard()
            }
            item {
                SentrySettingsCard()
            }

            if (debugPanelCounter >= 5) {
                item {
                    ReviewHintDebugPanel()
                }
                item {
                    DebugRestartReceiverCard()
                }
            }
        }
    }
}

