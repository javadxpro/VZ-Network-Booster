package com.vz.networkbooster.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vz.networkbooster.ui.VzViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: VzViewModel, onConnectRequested: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tunnel by viewModel.tunnelState.collectAsStateWithLifecycle()
    val traffic by viewModel.traffic.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("VZ Network Booster") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                val labels = listOf("Home", "Games", "Relays", "Measure", "Settings")
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Text("${index + 1}") },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                0 -> HomeScreen(
                    state = state,
                    tunnel = tunnel,
                    traffic = traffic,
                    onConnect = onConnectRequested,
                    onDisconnect = viewModel::disconnect,
                    onOptimize = viewModel::optimizeRoute,
                    onSelectServer = viewModel::selectServer,
                    onOpenRelays = { tab = 2 },
                    onDirectMode = viewModel::setDirectMode,
                )

                1 -> GamesScreen(
                    state = state,
                    onSelectGame = viewModel::selectGame,
                    onAddGame = viewModel::addGame,
                    onDeleteGame = viewModel::deleteGame,
                    onAddProbe = viewModel::addProbeTargetToActiveGame,
                    onRemoveProbe = viewModel::removeProbeTarget,
                )

                2 -> ServersScreen(
                    state = state,
                    onImport = viewModel::importServer,
                    onDelete = viewModel::deleteServer,
                    onSelect = viewModel::selectServer,
                    onRefreshHealth = viewModel::refreshServerHealth,
                )

                3 -> DiagnosticsScreen(
                    state = state,
                    onMeasure = viewModel::runQualityTest,
                    onMeasureAll = { viewModel.runQualityTestBatch() },
                    onOptimize = viewModel::optimizeRoute,
                )

                else -> SettingsScreen(
                    state = state,
                    onAutoReconnect = viewModel::setAutoReconnect,
                    onSplitTunnel = viewModel::setSplitTunnel,
                    onIncludeSelf = viewModel::setIncludeSelfForMeasurement,
                    onResetIdentity = viewModel::resetDeviceIdentity,
                    onRootEnabled = viewModel::setRootEnabled,
                    onRootWifi = viewModel::setRootWifiPowerSave,
                    onRootDoze = viewModel::setRootDozeWhitelist,
                    onRootTcp = viewModel::setRootTcpBbr,
                    onRootCaches = viewModel::setRootDropCaches,
                    onRootIcmp = viewModel::setRootIcmpProbes,
                    onRootQdisc = viewModel::setRootQdisc,
                    onApplyRoot = viewModel::applyRootTweaksNow,
                    onRevertRoot = viewModel::revertRootTweaks,
                    onRefreshRoot = viewModel::refreshRootStatus,
                    onDropCaches = viewModel::dropCachesNow,
                )
            }
        }
    }
}
