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
    onRootEnabled: (Boolean) -> Unit,
    onRootWifi: (Boolean) -> Unit,
    onRootDoze: (Boolean) -> Unit,
    onRootTcp: (Boolean) -> Unit,
    onRootCaches: (Boolean) -> Unit,
    onRootIcmp: (Boolean) -> Unit,
    onRootQdisc: (Boolean) -> Unit,
    onApplyRoot: () -> Unit,
    onRevertRoot: () -> Unit,
    onRefreshRoot: () -> Unit,
    onDropCaches: () -> Unit,
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
            title = "Root mode (اختیاری)",
            subtitle = "روت فقط چیزهایی را ممکن می‌کند که بدون آن غیرممکن است؛ هیچ کاهش جادویی پینگ در کار نیست.",
        ) {
            Text(
                text = if (state.rootAvailable) {
                    "root: available — ${state.rootDetail}"
                } else {
                    "root: not available — ${state.rootDetail}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.rootAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            SwitchRow(
                label = "استفاده از روت",
                detail = "فعال‌سازی این گزینه اجازه می‌دهد اپ دستورهای allow-list شده را با su اجرا کند.",
                checked = state.rootEnabled,
                onChange = onRootEnabled,
            )
            SwitchRow(
                label = "وای‌فای: خاموش کردن power save",
                detail = "یکی از منابع واقعی جهش تأخیر (jitter) روی وای‌فای؛ هنگام قطع اتصال بازگردانی می‌شود.",
                checked = state.rootWifiPowerSave,
                onChange = onRootWifi,
            )
            SwitchRow(
                label = "Whitelist در Doze",
                detail = "سرویس تونل در پس‌زمینه توسط سیستم کشته نمی‌شود.",
                checked = state.rootDozeWhitelist,
                onChange = onRootDoze,
            )
            SwitchRow(
                label = "BBR برای TCP",
                detail = "در کرنل‌هایی که BBR دارند throughput روی لینک پرافت بهتر می‌شود (RTT را کم نمی‌کند).",
                checked = state.rootTcpBbr,
                onChange = onRootTcp,
            )
            SwitchRow(
                label = "پاک‌سازی کش هنگام اتصال",
                detail = "drop_caches یک‌باره؛ روی گوشی‌های کم‌رم مفید است، روی گوشی مدرن تأثیر کم.",
                checked = state.rootDropCaches,
                onChange = onRootCaches,
            )
            SwitchRow(
                label = "Bufferbloat guard (fq_codel)",
                detail = "qdisc فعال را موقتاً fq_codel می‌کند؛ جهش تأخیر وقتی برنامه دیگری آپلود را پر می‌کند کم می‌شود و هنگام بازگردانی مقدار قبلی برمی‌گردد.",
                checked = state.rootQdisc,
                onChange = onRootQdisc,
            )
            SwitchRow(
                label = "پروب ICMP (ping واقعی)",
                detail = "بدون روت، اندروید اجرای ping را برای اپ‌ها می‌بندد. با روت، RTT/loss واقعی ICMP اندازه‌گیری می‌شود.",
                checked = state.rootIcmpProbes,
                onChange = onRootIcmp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onApplyRoot, enabled = state.rootAvailable) { Text("اعمال") }
                OutlinedButton(onClick = onRevertRoot) { Text("بازگردانی") }
                OutlinedButton(onClick = onDropCaches, enabled = state.rootAvailable) { Text("drop caches") }
            }
            OutlinedButton(onClick = onRefreshRoot, modifier = Modifier.fillMaxWidth()) { Text("بررسی مجدد روت") }
            state.rootLog.forEach { line -> MonoText(line) }
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
