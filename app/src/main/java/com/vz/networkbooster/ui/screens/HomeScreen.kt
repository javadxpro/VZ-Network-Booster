package com.vz.networkbooster.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vz.networkbooster.data.model.TunnelPhase
import com.vz.networkbooster.data.model.TunnelUiState
import com.vz.networkbooster.ui.Format
import com.vz.networkbooster.ui.UiState
import com.vz.networkbooster.ui.components.HealthChip
import com.vz.networkbooster.ui.components.KeyValueRow
import com.vz.networkbooster.ui.components.SectionCard

@Composable
fun HomeScreen(
    state: UiState,
    tunnel: TunnelUiState,
    traffic: Pair<Long, Long>,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOptimize: () -> Unit,
    onSelectServer: (String) -> Unit,
    onOpenRelays: () -> Unit,
    onDirectMode: (Boolean) -> Unit,
    onAirEnabled: (Boolean) -> Unit,
    onAirAutoSwitch: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(
            title = "حالت اتصال",
            subtitle = if (state.directMode) {
                "مستقیم (بدون واسطه): ترافیک بازی همان مسیر ISP را می‌رود؛ اپ هیچ مسیری را عوض نمی‌کند."
            } else {
                "رله: ترافیک بازی از سرور WireGuard انتخاب‌شده شما عبور می‌کند."
            },
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onDirectMode(false) },
                    enabled = state.directMode,
                    modifier = Modifier.weight(1f),
                ) { Text("رله") }
                OutlinedButton(
                    onClick = { onDirectMode(true) },
                    enabled = !state.directMode,
                    modifier = Modifier.weight(1f),
                ) { Text("مستقیم") }
            }
            if (state.directMode) {
                Text(
                    text = "بدون واسطه تونلی ساخته نمی‌شود. تنها کارهای ممکن: اندازه‌گیری مسیر واقعی و " +
                        "تیونینگ سمت دستگاه با روت (Wi-Fi power save، fq_codel، BBR، Doze).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = if (state.rootEnabled && state.rootAvailable) {
                        "Root mode فعال است: دکمه پایین تیونینگ را اعمال می‌کند."
                    } else {
                        "Root mode خاموش است: در حالت مستقیم فقط اندازه‌گیری انجام می‌شود."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(
            title = when (tunnel.phase) {
                TunnelPhase.CONNECTED -> "Tunnel active"
                TunnelPhase.CONNECTING -> "Connecting"
                TunnelPhase.RECONNECTING -> "Reconnecting"
                TunnelPhase.ERROR -> "Connection error"
                TunnelPhase.PREPARING -> "Preparing"
                TunnelPhase.IDLE -> "Tunnel idle"
            },
            subtitle = tunnel.message.ifBlank { "No tunnel activity yet" },
        ) {
            KeyValueRow("Relay", tunnel.serverName ?: state.activeServer?.name ?: "none selected")
            KeyValueRow("Phase", tunnel.phase.name)
            KeyValueRow("Uptime", Format.duration(tunnel.sinceEpochMs))
            KeyValueRow("Download", Format.bytes(if (traffic.first > 0) traffic.first else tunnel.rxBytes))
            KeyValueRow("Upload", Format.bytes(if (traffic.second > 0) traffic.second else tunnel.txBytes))
            KeyValueRow("Auto reconnects", tunnel.reconnects.toString())
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (tunnel.isUp) {
                    Button(onClick = onDisconnect, modifier = Modifier.weight(1f)) { Text("Disconnect") }
                } else {
                    Button(
                        onClick = onConnect,
                        enabled = !tunnel.isBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            when {
                                tunnel.isBusy -> "Working…"
                                state.directMode -> "Apply device tuning"
                                else -> "Connect"
                            }
                        )
                    }
                }
                OutlinedButton(onClick = onOptimize, modifier = Modifier.weight(1f)) { Text("Optimize route") }
            }
        }

        SectionCard(
            title = "Game profile",
            subtitle = "Split tunnelling and probe targets follow the selected game.",
        ) {
            KeyValueRow("Active game", state.activeGame?.displayName ?: "none")
            KeyValueRow("Packages", state.activeGame?.packages?.joinToString(", ") ?: "—")
            KeyValueRow(
                "Mode",
                if (state.splitTunnelGameOnly) "only selected game apps" else "full tunnel",
            )
        }

        SectionCard(
            title = "AIR — مسیریابی تطبیقی (اختیاری)",
            subtitle = "هر رله‌ای که probe target دارد دائماً اندازه‌گیری می‌شود. تعویض مسیر فقط وقتی انجام " +
                "می‌شود که بهبود اندازه‌گیری‌شده از آستانه بگذرد (hysteresis + cooldown).",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("فعال", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = state.airEnabled, onCheckedChange = onAirEnabled)
            }
            if (state.airEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("تعویض خودکار مسیر", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = state.airAutoSwitch, onCheckedChange = onAirAutoSwitch)
                }
                Text(
                    text = state.airAction.ifBlank { "منتظر اولین چرخه‌ی اندازه‌گیری" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                state.airScores.take(4).forEach { (serverId, score) ->
                    val name = state.servers.firstOrNull { it.id == serverId }?.name ?: serverId
                    KeyValueRow(name, "score %.1f".format(score))
                }
                state.airLog.takeLast(4).forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = "برای معنا داشتن مقایسه، حداقل دو رله با probe target لازم است. " +
                        "بدون probe target هیچ رله‌ای رتبه‌بندی نمی‌شود (عدد ساختگی ساخته نمی‌شود).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SectionCard(
            title = "Route ranking (Smart Routing)",
            subtitle = "Measured latency (when available) + health + operator metadata.",
        ) {
            if (state.ranking.isEmpty()) {
                Text(
                    text = "No usable relay yet. A relay needs an endpoint, its public key and our " +
                        "assigned address before it can be used — add one in Relays.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.ranking.take(5).forEach { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${entry.server.name} · ${entry.server.region.ifBlank { "region unknown" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "score ${entry.score}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HealthChip(state.statuses[entry.server.id]?.health ?: com.vz.networkbooster.data.model.ServerHealth.UNKNOWN)
                        state.measuredAvgMs[entry.server.id]?.let {
                            Text("measured ${Format.ms(it)}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onSelectServer(entry.server.id) }) { Text("use") }
                    }
                    Text(
                        text = entry.reasons.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onOpenRelays) { Text("Manage relays") }
        }

        SectionCard(title = "Honest limits", subtitle = "What this build does and does not claim") {
            Text(
                text = "• Path changes are real (WireGuard tunnel to the relay you configure).\n" +
                    "• Ping/jitter/loss numbers come from actual TCP/UDP probes on this device.\n" +
                    "• No latency improvement is promised: if the relay path is longer than the direct " +
                    "path, your numbers will get worse and the app shows that.\n" +
                    "• A relay only works once its operator has registered this device's public key.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
