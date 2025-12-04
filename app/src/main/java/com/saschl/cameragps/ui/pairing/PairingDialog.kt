package com.saschl.cameragps.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saschl.cameragps.R

@Composable
fun PairingConfirmationDialogWithLoading(
    deviceName: String,
    isPairing: Boolean,
    pairingResult: PairingResult?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = if (isPairing) { {} } else onDismiss, // Prevent dismissal during pairing
        title = {
            Text(
                text = when {
                    isPairing -> stringResource(R.string.pairing_camera_title)
                    pairingResult == PairingResult.SUCCESS -> stringResource(R.string.pairing_complete_title)
                    pairingResult == PairingResult.FAILED -> stringResource(R.string.pairing_failed_title)
                    else -> stringResource(R.string.camera_pairing_required_title)
                },
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                when {
                    isPairing -> {
                        // Show loading state
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 16.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = stringResource(
                                    R.string.pairing_with_device_please_wait,
                                    deviceName
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    pairingResult == PairingResult.SUCCESS -> {
                        // Show success state
                        Text(
                            text = stringResource(
                                R.string.successfully_paired_with_device,
                                deviceName
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    pairingResult == PairingResult.FAILED -> {
                        // Show failure state
                        Text(
                            text = stringResource(R.string.failed_to_pair_with_device, deviceName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {
                        // Show initial instructions
                        Text(
                            text = stringResource(
                                R.string.to_pair_with_your_camera_please,
                                deviceName
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string._1_turn_on_your_camera),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string._2_go_to_camera_settings_and_enable_bluetooth_pairing_mode),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string._3_make_sure_the_camera_is_discoverable),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.once_your_camera_is_ready_tap_continue_to_start_pairing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                isPairing -> {
                    // No buttons during pairing
                }
                pairingResult == PairingResult.SUCCESS -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.done))
                    }
                }
                pairingResult == PairingResult.FAILED -> {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.try_again))
                    }
                }
                else -> {
                    TextButton(onClick = onConfirm) {
                        Text(stringResource(R.string.continue_label))
                    }
                }
            }
        },
        dismissButton = {
            if (!isPairing && pairingResult != PairingResult.SUCCESS) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
