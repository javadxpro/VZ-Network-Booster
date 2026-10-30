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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vz.networkbooster.data.BuiltInCatalog
import com.vz.networkbooster.data.model.ProbeTarget
import com.vz.networkbooster.ui.Format
import com.vz.networkbooster.ui.UiState
import com.vz.networkbooster.ui.components.KeyValueRow
import com.vz.networkbooster.ui.components.MonoText
import com.vz.networkbooster.ui.components.SectionCard

@Composable
fun DiagnosticsScreen(
    state: UiState,
    onMeasure: (ProbeTarget) -> Unit,
    onMeasureAll: () -> Unit,
    onOptimize: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(
            title = "Measure path quality",
            subtitle = "TCP handshake RTT / UDP round-trip, jitter from consecutive samples, loss from " +
                "attempts without a reply. Everything is measured on this device.",
        ) {
            if (state.busy != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 4.dp))
                    Text(state.busy, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onMeasureAll) { Text("Measure all targets") }
            val targets = buildList {
                addAll(state.activeGame?.probeTargets ?: emptyList())
                addAll(BuiltInCatalog.referenceAnchors)
            }
            targets.forEach { target ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(target.label, style = MaterialTheme.typography.bodyMedium)
                        MonoText("${target.protocol.name} ${target.host}:${target.port}")
                    }
                    Button(onClick = { onMeasure(target) }) { Text("Run") }
                }
            }
        }

        SectionCard(
            title = "Route optimizer",
            subtitle = "Connects to each configured relay in turn, measures a real probe through that " +
                "tunnel, then keeps the best measured route.",
        ) {
            Button(onClick = onOptimize, enabled = !state.optimizerRunning) {
                Text(if (state.optimizerRunning) "Running…" else "Run optimizer")
            }
            state.optimizerLog.forEach { line ->
                MonoText(line)
            }
        }

        SectionCard(
            title = "Last results",
            subtitle = if (state.reports.isEmpty()) "No measurement yet" else "${state.reports.size} recorded",
        ) {
            state.reports.take(12).forEach { report ->
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "${report.targetLabel} · ${report.path}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    KeyValueRow("avg / min / max", "${Format.ms(report.avgMs)} / ${Format.ms(report.minMs)} / ${Format.ms(report.maxMs)}")
                    KeyValueRow("jitter", Format.ms(report.jitterMs))
                    KeyValueRow("loss", "%.0f%% (%d/%d)".format(report.lossPercent, report.received, report.attempted))
                    if (report.note.isNotBlank()) {
                        Text(
                            text = report.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = Format.clock(report.timestampMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
