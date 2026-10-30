package com.vz.networkbooster.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vz.networkbooster.data.importx.ConfigImporter
import com.vz.networkbooster.ui.Format
import com.vz.networkbooster.ui.UiState
import com.vz.networkbooster.ui.components.HealthChip
import com.vz.networkbooster.ui.components.KeyValueRow
import com.vz.networkbooster.ui.components.MonoText
import com.vz.networkbooster.ui.components.SectionCard

@Composable
fun ServersScreen(
    state: UiState,
    onImport: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onSelect: (String) -> Unit,
    onRefreshHealth: () -> Unit,
) {
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importName by remember { mutableStateOf("") }
    val context = LocalContext.current
    val configFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!text.isNullOrBlank() && text.length <= 512_000) {
                importText = text
                if (importName.isBlank()) {
                    importName = (uri.lastPathSegment ?: "imported relay")
                        .substringAfterLast('/')
                        .substringBeforeLast('.')
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(
            title = "Relays (${state.servers.size})",
            subtitle = "Hybrid model: shared relays for trials, dedicated relays for stable routes.",
        ) {
            if (state.servers.isEmpty()) {
                Text(
                    text = "No relay yet. This app deliberately ships no anonymous \"public\" relay: a " +
                        "WireGuard peer only works after the operator registers this device's public key.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.servers.forEach { server ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = server.name + if (server.id == state.activeServerId) "  ✓" else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        HealthChip(state.statuses[server.id]?.health ?: com.vz.networkbooster.data.model.ServerHealth.UNKNOWN)
                    }
                    MonoText(server.endpoint)
                    KeyValueRow("Region", server.region.ifBlank { "unknown" })
                    KeyValueRow("Type", if (server.isDedicated) "dedicated" else "shared")
                    KeyValueRow("Capacity", if (server.capacity > 0) "${server.capacity} seats" else "not declared")
                    KeyValueRow("Probe target", if (server.hasProbeTarget) "${server.probeHost}:${server.probePort}" else "none (latency unverified)")
                    state.measuredAvgMs[server.id]?.let { KeyValueRow("Measured", Format.ms(it)) }
                    state.statuses[server.id]?.let {
                        Text(
                            text = it.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onSelect(server.id) }) { Text("select") }
                        TextButton(onClick = { onDelete(server.id) }) { Text("remove") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showImport = true }) { Text("Add relay") }
                OutlinedButton(onClick = onRefreshHealth) { Text("Check health") }
            }
        }

        SectionCard(
            title = "This device's WireGuard public key",
            subtitle = "Register it on the relay with: wg set wg0 peer <key> allowed-ips 10.66.0.2/32",
        ) {
            MonoText(state.devicePublicKey.ifBlank { "generating…" })
            Text(
                text = "The private half is generated on this device and stored only in " +
                    "Keystore-encrypted form. It is never logged, exported or committed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Add relay") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = importName,
                        onValueChange = { importName = it },
                        label = { Text("Name") },
                    )
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text("Server bundle JSON or client wg-quick config") },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    OutlinedButton(
                        onClick = { configFilePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Choose file… (.conf / .json)") }
                    if (ConfigImporter.looksLikeServerConfig(importText)) {
                        Text(
                            text = "این فایل کانفیگ سمت سرور است. کلید خصوصی سرور را داخل گوشی نگذارید؛ " +
                                "کلید عمومی گوشی را روی سرور ثبت کنید و رله را با bundle اضافه کنید.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        text = "Bundle: {\"name\":\"fra-01\",\"endpoint\":\"relay.example.com:51820\"," +
                            "\"serverPublicKey\":\"…\",\"address\":\"10.66.0.2/32\",\"region\":\"eu\"}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onImport(importText, importName)
                    showImport = false
                    importText = ""
                    importName = ""
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } },
        )
    }
}
