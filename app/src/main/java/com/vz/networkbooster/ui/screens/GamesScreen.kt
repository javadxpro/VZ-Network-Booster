package com.vz.networkbooster.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import com.vz.networkbooster.ui.UiState
import com.vz.networkbooster.ui.components.MonoText
import com.vz.networkbooster.ui.components.SectionCard

@Composable
fun GamesScreen(
    state: UiState,
    onSelectGame: (String) -> Unit,
    onAddGame: (String, String, String) -> Unit,
    onDeleteGame: (String) -> Unit,
    onAddProbe: (String, String, String, String) -> Unit,
    onRemoveProbe: (String, Int) -> Unit,
) {
    var showAddGame by remember { mutableStateOf(false) }
    var showAddProbe by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(
            title = "Games",
            subtitle = "Multi-game by design: a game is a profile (packages + probe targets + region hint).",
        ) {
            state.games.forEach { game ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = game.displayName + if (game.id == state.activeGameId) "  ✓" else "",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            MonoText(game.packages.joinToString(", "))
                            if (game.probeTargets.isNotEmpty()) {
                                Text(
                                    text = "${game.probeTargets.size} probe target(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { onSelectGame(game.id) }) { Text("select") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (game.isBuiltIn) {
                            Text(
                                text = "built-in profile",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            TextButton(onClick = { onDeleteGame(game.id) }) { Text("remove") }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showAddGame = true }) { Text("Add game") }
                Button(onClick = { showAddProbe = true }, enabled = state.activeGame != null) {
                    Text("Add probe target")
                }
            }
        }

        SectionCard(
            title = "Probe targets of ${state.activeGame?.displayName ?: "no game"}",
            subtitle = "These are the destinations measured on the Measure screen.",
        ) {
            val game = state.activeGame
            if (game == null || game.probeTargets.isEmpty()) {
                Text(
                    text = "No probe target yet. Game backends do not answer unsolicited probes, so add " +
                        "the regional host:port you actually want to track (TCP handshake RTT works best).",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                game.probeTargets.forEachIndexed { index, target ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(target.label, style = MaterialTheme.typography.bodyMedium)
                            MonoText("${target.protocol.name} ${target.host}:${target.port}")
                        }
                        TextButton(onClick = { onRemoveProbe(game.id, index) }) { Text("remove") }
                    }
                }
            }
        }

        SectionCard(
            title = "Installed launchable apps (${state.installedPackages.size})",
            subtitle = "Visible only through the launcher <queries> element — no QUERY_ALL_PACKAGES.",
        ) {
            state.installedPackages.take(20).forEach { pkg -> MonoText(pkg) }
            if (state.installedPackages.size > 20) {
                Text("…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showAddGame) {
        var name by remember { mutableStateOf("") }
        var packages by remember { mutableStateOf("") }
        var region by remember { mutableStateOf("global") }
        AlertDialog(
            onDismissRequest = { showAddGame = false },
            title = { Text("Add game profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display name") })
                    OutlinedTextField(
                        value = packages,
                        onValueChange = { packages = it },
                        label = { Text("Package names (comma separated)") },
                    )
                    OutlinedTextField(value = region, onValueChange = { region = it }, label = { Text("Region hint") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddGame(name, packages, region)
                    showAddGame = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddGame = false }) { Text("Cancel") } },
        )
    }

    if (showAddProbe) {
        var label by remember { mutableStateOf("") }
        var host by remember { mutableStateOf("") }
        var port by remember { mutableStateOf("443") }
        var protocol by remember { mutableStateOf("TCP") }
        AlertDialog(
            onDismissRequest = { showAddProbe = false },
            title = { Text("Add probe target") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label") })
                    OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") })
                    OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") })
                    OutlinedTextField(
                        value = protocol,
                        onValueChange = { protocol = it.uppercase() },
                        label = { Text("Protocol (TCP/UDP/ICMP)") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddProbe(label, host, port, protocol)
                    showAddProbe = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddProbe = false }) { Text("Cancel") } },
        )
    }
}
