package com.vz.networkbooster.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.vz.networkbooster.ui.UiState
import com.vz.networkbooster.ui.components.MonoText
import com.vz.networkbooster.ui.components.SectionCard

@Composable
fun SettingsScreen(
    state: UiState,
    onAutoReconnect: (Boolean) -> Unit,
    onSplitTunnel: (Boolean) -> Unit,
    onIncludeSelf: (Boolean) -> Unit,
    onResetIdentity: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "Connection behaviour") {
            SwitchRow(
                label = "Auto reconnect",
                detail = "Watchdog retries with capped exponential backoff when the tunnel drops.",
                checked = state.autoReconnect,
                onChange = onAutoReconnect,
            )
            SwitchRow(
                label = "Split tunnelling: game apps only",
                detail = "Only the active game's packages (plus this app for measurement) use the tunnel.",
                checked = state.splitTunnelGameOnly,
                onChange = onSplitTunnel,
            )
            SwitchRow(
                label = "Route this app through the tunnel",
                detail = "Needed so on-device measurements describe the tunnel path, not the direct path.",
                checked = state.includeSelfForMeasurement,
                onChange = onIncludeSelf,
            )
        }

        SectionCard(
            title = "Device identity",
            subtitle = "Rotating the key breaks every existing peer registration until you update the relays.",
        ) {
            MonoText(state.devicePublicKey.ifBlank { "generating…" })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(state.devicePublicKey))
                }) { Text("Copy public key") }
                OutlinedButton(onClick = onResetIdentity) { Text("Rotate key") }
            }
        }

        SectionCard(title = "Because it matters") {
            Text(
                text = "VZ Network Booster changes the path your game traffic takes. It cannot make a " +
                    "relay geographically closer to the game server than your ISP is.\n\n" +
                    "• A route only exists when you configure a real WireGuard relay whose operator " +
                    "registered this device's public key.\n" +
                    "• Measurements are TCP/UDP round trips from this device — not ICMP, not fabricated.\n" +
                    "• Keys never enter the repository; the app stores them Keystore-encrypted.\n" +
                    "• No ad, no analytics, no telemetry upload.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
