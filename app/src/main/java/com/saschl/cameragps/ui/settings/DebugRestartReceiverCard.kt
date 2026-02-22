package com.saschl.cameragps.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.saschl.cameragps.service.RestartReceiver
import timber.log.Timber

@Composable
internal fun DebugRestartReceiverCard() {
    val context = LocalContext.current

    SettingsCard(title = "Debug: RestartReceiver Test") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    val broadcastIntent = Intent(
                        context,
                        RestartReceiver::class.java
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

