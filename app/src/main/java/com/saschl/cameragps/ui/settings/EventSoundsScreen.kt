package com.saschl.cameragps.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.event_sounds_title
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventSoundsScreen(onBackClick: () -> Unit = {}) {
    SettingsSectionScreen(
        title = stringResource(Res.string.event_sounds_title),
        onBackClick = onBackClick,
    ) {
        EventSoundsSettingsCard()
    }
}


