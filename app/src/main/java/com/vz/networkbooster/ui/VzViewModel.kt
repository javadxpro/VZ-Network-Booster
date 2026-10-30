package com.vz.networkbooster.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vz.networkbooster.data.BuiltInCatalog
import com.vz.networkbooster.data.importx.ConfigImporter
import com.vz.networkbooster.data.model.GameProfile
import com.vz.networkbooster.data.model.ProbeProtocol
import com.vz.networkbooster.data.model.ProbeTarget
import com.vz.networkbooster.data.model.QualityReport
import com.vz.networkbooster.data.model.RouteScore
import com.vz.networkbooster.data.model.ServerProfile
import com.vz.networkbooster.data.model.ServerStatus
import com.vz.networkbooster.data.model.TunnelPhase
import com.vz.networkbooster.data.store.GameRepository
import com.vz.networkbooster.data.store.ServerRepository
import com.vz.networkbooster.data.store.SettingsStore
import com.vz.networkbooster.net.QualityTester
import com.vz.networkbooster.net.RootIcmpPinger
import com.vz.networkbooster.net.ServerHealthChecker
import com.vz.networkbooster.net.SmartRouter
import com.vz.networkbooster.root.RootShell
import com.vz.networkbooster.root.RootTweaks
import com.vz.networkbooster.security.DeviceIdentity
import com.vz.networkbooster.security.SecretBox
import com.vz.networkbooster.vpn.BoosterService
import com.vz.networkbooster.vpn.TrafficMonitor
import com.vz.networkbooster.vpn.VpnManager
import com.vz.networkbooster.vpn.WireGuardConfigBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val loading: Boolean = true,
    val games: List<GameProfile> = emptyList(),
    val servers: List<ServerProfile> = emptyList(),
    val statuses: Map<String, ServerStatus> = emptyMap(),
    val measuredAvgMs: Map<String, Double> = emptyMap(),
    val activeGameId: String? = null,
    val activeServerId: String? = null,
    val autoReconnect: Boolean = true,
    val splitTunnelGameOnly: Boolean = true,
    val includeSelfForMeasurement: Boolean = true,
    val reports: List<QualityReport> = emptyList(),
    val ranking: List<RouteScore> = emptyList(),
    val installedPackages: List<String> = emptyList(),
    val devicePublicKey: String = "",
    val rootAvailable: Boolean = false,
    val rootDetail: String = "",
    val rootEnabled: Boolean = false,
    val rootWifiPowerSave: Boolean = true,
    val rootDozeWhitelist: Boolean = true,
    val rootTcpBbr: Boolean = false,
    val rootDropCaches: Boolean = false,
    val rootIcmpProbes: Boolean = true,
    val rootLog: List<String> = emptyList(),
    val busy: String? = null,
    val optimizerRunning: Boolean = false,
    val optimizerLog: List<String> = emptyList(),
    val message: String? = null,
) {
    val activeGame: GameProfile? get() = games.firstOrNull { it.id == activeGameId }
    val activeServer: ServerProfile? get() = servers.firstOrNull { it.id == activeServerId }
}

class VzViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsStore(application)
    private val secrets = SecretBox(application)
    private val identity = DeviceIdentity(application)
    private val gameRepo = GameRepository(application.filesDir)
    private val serverRepo = ServerRepository(application.filesDir)
    private val rootShell = RootShell
    private val rootTweaks = RootTweaks(application, rootShell)
    private val tester = QualityTester(icmpPinger = RootIcmpPinger(rootShell))
    private val healthChecker = ServerHealthChecker()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val tunnelState = VpnManager.state
    val traffic = TrafficMonitor.counter

    init {
        viewModelScope.launch {
            val games = gameRepo.load()
            val servers = serverRepo.load()
            val statuses = serverRepo.loadStatuses()
            val launched = loadLaunchablePackages()
            _state.update {
                it.copy(
                    loading = false,
                    games = games,
                    servers = servers,
                    statuses = statuses,
                    activeGameId = settings.activeGameId ?: games.firstOrNull()?.id,
                    activeServerId = settings.activeServerId ?: servers.firstOrNull()?.id,
                    autoReconnect = settings.autoReconnect,
                    splitTunnelGameOnly = settings.splitTunnelGameOnly,
                    includeSelfForMeasurement = settings.includeSelfForMeasurement,
                    installedPackages = launched,
                    devicePublicKey = identity.publicKeyBase64(),
                    rootEnabled = settings.rootEnabled,
                    rootWifiPowerSave = settings.rootWifiPowerSave,
                    rootDozeWhitelist = settings.rootDozeWhitelist,
                    rootTcpBbr = settings.rootTcpBbr,
                    rootDropCaches = settings.rootDropCaches,
                    rootIcmpProbes = settings.rootIcmpProbes,
                )
            }
            recomputeRanking()
            refreshRootStatus()
        }
    }

    // ---------------------------------------------------------------- games

    fun selectGame(gameId: String) {
        settings.activeGameId = gameId
        _state.update { it.copy(activeGameId = gameId) }
        recomputeRanking()
    }

    fun addGame(displayName: String, packagesCsv: String, region: String) {
        if (displayName.isBlank() || packagesCsv.isBlank()) {
            _state.update { it.copy(message = "game name and at least one package are required") }
            return
        }
        viewModelScope.launch {
            val packages = packagesCsv.split(',', '\n', ' ').map { it.trim() }.filter { it.isNotBlank() }
            val game = GameProfile(
                id = gameRepo.newId(),
                displayName = displayName.trim(),
                packages = packages,
                regionHint = region.trim(),
            )
            val games = _state.value.games + game
            gameRepo.save(games)
            _state.update { it.copy(games = games, activeGameId = game.id, message = "added ${game.displayName}") }
            settings.activeGameId = game.id
            recomputeRanking()
        }
    }

    fun deleteGame(gameId: String) {
        viewModelScope.launch {
            val games = _state.value.games.filterNot { it.id == gameId }
            gameRepo.save(games)
            _state.update {
                it.copy(games = games, activeGameId = if (it.activeGameId == gameId) games.firstOrNull()?.id else it.activeGameId)
            }
            recomputeRanking()
        }
    }

    fun addProbeTargetToActiveGame(label: String, host: String, portText: String, protocol: String) {
        val game = _state.value.activeGame ?: return
        val port = portText.toIntOrNull()
        if (host.isBlank() || port == null || port !in 1..65535) {
            _state.update { it.copy(message = "probe target needs a host and a port 1-65535") }
            return
        }
        viewModelScope.launch {
            val target = ProbeTarget(
                label = label.ifBlank { host },
                host = host.trim(),
                port = port,
                protocol = runCatching { ProbeProtocol.valueOf(protocol.uppercase()) }
                    .getOrDefault(ProbeProtocol.TCP),
            )
            val updated = game.copy(probeTargets = game.probeTargets + target)
            val games = _state.value.games.map { if (it.id == game.id) updated else it }
            gameRepo.save(games)
            _state.update { it.copy(games = games, message = "probe target added") }
        }
    }

    fun removeProbeTarget(gameId: String, index: Int) {
        viewModelScope.launch {
            val games = _state.value.games.map { game ->
                if (game.id == gameId && index in game.probeTargets.indices) {
                    game.copy(probeTargets = game.probeTargets.filterIndexed { i, _ -> i != index })
                } else {
                    game
                }
            }
            gameRepo.save(games)
            _state.update { it.copy(games = games) }
        }
    }

    // -------------------------------------------------------------- servers

    fun selectServer(serverId: String) {
        settings.activeServerId = serverId
        _state.update { it.copy(activeServerId = serverId) }
    }

    fun importServer(text: String, nameHint: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(message = "paste a server bundle (JSON) or a wg-quick config") }
            return
        }
        val id = serverRepo.newId()
        val imported = if (trimmed.startsWith("{")) {
            ConfigImporter.parseBundleJson(trimmed, id, nameHint.ifBlank { "Imported relay" })
        } else {
            ConfigImporter.parseWgQuick(trimmed, id, nameHint.ifBlank { "Imported relay" })
        }
        val server = imported.server
        if (server == null) {
            _state.update { it.copy(message = "import failed: ${imported.error}") }
            return
        }
        viewModelScope.launch {
            if (!imported.privateKey.isNullOrBlank()) {
                // Imported full config carries its own peer key: keep it encrypted, per server.
                secrets.put(keyNameFor(server.id), imported.privateKey)
            }
            val servers = _state.value.servers + server
            serverRepo.save(servers)
            _state.update {
                it.copy(
                    servers = servers,
                    activeServerId = it.activeServerId ?: server.id,
                    message = "imported ${server.name}" + if (imported.warnings.isEmpty()) "" else
                        " (${imported.warnings.joinToString("; ")})",
                )
            }
            recomputeRanking()
        }
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            val servers = _state.value.servers.filterNot { it.id == serverId }
            serverRepo.save(servers)
            secrets.remove(keyNameFor(serverId))
            _state.update {
                it.copy(
                    servers = servers,
                    statuses = it.statuses - serverId,
                    measuredAvgMs = it.measuredAvgMs - serverId,
                    activeServerId = if (it.activeServerId == serverId) servers.firstOrNull()?.id else it.activeServerId,
                )
            }
            recomputeRanking()
        }
    }

    fun refreshServerHealth() {
        val servers = _state.value.servers
        if (servers.isEmpty()) {
            _state.update { it.copy(message = "no servers configured yet") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = "checking server health") }
            val statuses = _state.value.statuses.toMutableMap()
            servers.forEach { server ->
                statuses[server.id] = healthChecker.check(server)
            }
            serverRepo.saveStatuses(statuses)
            _state.update { it.copy(busy = null, statuses = statuses) }
            recomputeRanking()
        }
    }

    // ------------------------------------------------------------- settings

    fun setAutoReconnect(enabled: Boolean) {
        settings.autoReconnect = enabled
        _state.update { it.copy(autoReconnect = enabled) }
    }

    fun setSplitTunnel(enabled: Boolean) {
        settings.splitTunnelGameOnly = enabled
        _state.update { it.copy(splitTunnelGameOnly = enabled) }
    }

    fun setIncludeSelfForMeasurement(enabled: Boolean) {
        settings.includeSelfForMeasurement = enabled
        _state.update { it.copy(includeSelfForMeasurement = enabled) }
    }

    fun onVpnConsentDenied() {
        _state.update {
            it.copy(message = "VPN permission denied — Android will not let the app create a tunnel without it")
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun resetDeviceIdentity() {
        val publicKey = runCatching { identity.resetIdentity(); identity.publicKeyBase64() }
            .getOrElse { "unavailable: ${it.message}" }
        _state.update {
            it.copy(
                devicePublicKey = publicKey,
                message = "new device key generated — re-register the public key on every relay",
            )
        }
    }

    // ----------------------------------------------------------------- root

    fun refreshRootStatus() {
        viewModelScope.launch {
            val status = rootShell.status(force = true)
            _state.update {
                it.copy(
                    rootAvailable = status.available,
                    rootDetail = status.detail,
                    rootEnabled = if (status.available) it.rootEnabled else false,
                )
            }
        }
    }

    fun setRootEnabled(enabled: Boolean) {
        if (enabled && !_state.value.rootAvailable) {
            _state.update { it.copy(message = "root shell not granted — open your root manager and allow this app") }
            refreshRootStatus()
            return
        }
        settings.rootEnabled = enabled
        _state.update { it.copy(rootEnabled = enabled) }
        if (!enabled) revertRootTweaks()
    }

    fun setRootWifiPowerSave(enabled: Boolean) {
        settings.rootWifiPowerSave = enabled
        _state.update { it.copy(rootWifiPowerSave = enabled) }
    }

    fun setRootDozeWhitelist(enabled: Boolean) {
        settings.rootDozeWhitelist = enabled
        _state.update { it.copy(rootDozeWhitelist = enabled) }
    }

    fun setRootTcpBbr(enabled: Boolean) {
        settings.rootTcpBbr = enabled
        _state.update { it.copy(rootTcpBbr = enabled) }
    }

    fun setRootDropCaches(enabled: Boolean) {
        settings.rootDropCaches = enabled
        _state.update { it.copy(rootDropCaches = enabled) }
    }

    fun setRootIcmpProbes(enabled: Boolean) {
        settings.rootIcmpProbes = enabled
        _state.update { it.copy(rootIcmpProbes = enabled) }
    }

    fun applyRootTweaksNow() {
        if (!_state.value.rootAvailable) {
            _state.update { it.copy(message = "root not available") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = "applying root tweaks", rootLog = emptyList()) }
            val steps = rootTweaks.apply(currentRootConfig())
            _state.update {
                it.copy(
                    busy = null,
                    rootLog = steps.map { step -> (if (step.ok) "✔ " else "✘ ") + step.title + " — " + step.detail },
                    message = "root tweaks applied (${steps.count { step -> step.ok }}/${steps.size})",
                )
            }
        }
    }

    fun revertRootTweaks() {
        viewModelScope.launch {
            val steps = rootTweaks.revert()
            if (steps.isEmpty()) return@launch
            _state.update {
                it.copy(
                    rootLog = steps.map { step -> (if (step.ok) "✔ " else "✘ ") + step.title + " — " + step.detail },
                    message = "root state restored",
                )
            }
        }
    }

    fun dropCachesNow() {
        viewModelScope.launch {
            val step = rootTweaks.dropCachesNow()
            _state.update {
                it.copy(
                    rootLog = it.rootLog + ((if (step.ok) "✔ " else "✘ ") + step.title + " — " + step.detail),
                    message = if (step.ok) "caches dropped" else "drop_caches failed",
                )
            }
        }
    }

    private fun currentRootConfig() = RootTweaks.Config(
        wifiPowerSaveOff = _state.value.rootWifiPowerSave,
        dozeWhitelist = _state.value.rootDozeWhitelist,
        tcpBbr = _state.value.rootTcpBbr,
        dropCaches = _state.value.rootDropCaches,
    )

    // ----------------------------------------------------------------- vpn

    fun connect() {
        val server = _state.value.activeServer
        if (server == null) {
            _state.update { it.copy(message = "add or select a relay server first") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = "starting tunnel") }
            val result = connectInternal(server)
            _state.update {
                it.copy(
                    busy = null,
                    message = result.exceptionOrNull()?.let { err -> "connect failed: ${err.message}" }
                        ?: "tunnel up via ${server.name}",
                )
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            if (_state.value.rootEnabled) revertRootTweaks()
            VpnManager.disconnect()
            runCatching { BoosterService.stop(getApplication()) }
            _state.update { it.copy(busy = null, message = "disconnected") }
        }
    }

    private suspend fun connectInternal(server: ServerProfile): Result<Unit> {
        val app = getApplication<Application>()
        val privateKey = secrets.get(keyNameFor(server.id)) ?: identity.privateKeyBase64()
        val game = _state.value.activeGame
        val settingsSnapshot = _state.value
        val include = mutableSetOf<String>()
        if (settingsSnapshot.splitTunnelGameOnly && game != null) {
            include += game.packages
            if (settingsSnapshot.includeSelfForMeasurement) include += app.packageName
        }
        val built = WireGuardConfigBuilder.build(
            server = server,
            privateKeyBase64 = privateKey,
            includePackages = include,
            excludePackages = emptySet(),
        )
        if (!built.valid) {
            return Result.failure(IllegalStateException(built.error ?: "invalid WireGuard config"))
        }
        val result = VpnManager.connect(
            VpnManager.ConnectRequest(
                server = server,
                configText = built.configText,
                includePackages = include,
                excludePackages = emptySet(),
            )
        )
        if (result.isSuccess) {
            runCatching { BoosterService.start(app) }
                .onFailure { _state.update { s -> s.copy(message = "tunnel up but service start failed: ${it.message}") } }
            if (_state.value.rootEnabled && _state.value.rootAvailable) {
                val steps = rootTweaks.apply(currentRootConfig())
                _state.update {
                    it.copy(rootLog = steps.map { step -> (if (step.ok) "✔ " else "✘ ") + step.title + " — " + step.detail })
                }
            }
        }
        return result
    }

    // -------------------------------------------------------- measurements

    fun runQualityTest(target: ProbeTarget, samples: Int = 10) {
        viewModelScope.launch {
            _state.update { it.copy(busy = "measuring ${target.label}") }
            val path = if (VpnManager.state.value.phase == TunnelPhase.CONNECTED) {
                "tunnel:${VpnManager.state.value.serverName ?: "active"}"
            } else {
                "direct"
            }
            val report = tester.measure(target, samples = samples, path = path)
            _state.update { it.copy(busy = null, reports = (listOf(report) + it.reports).take(40)) }
        }
    }

    fun runQualityTestBatch(samples: Int = 10) {
        val game = _state.value.activeGame
        val targets = buildList {
            addAll(game?.probeTargets ?: emptyList())
            addAll(BuiltInCatalog.referenceAnchors)
        }
        viewModelScope.launch {
            for (target in targets) {
                _state.update { it.copy(busy = "measuring ${target.label}") }
                val path = if (VpnManager.state.value.phase == TunnelPhase.CONNECTED) {
                    "tunnel:${VpnManager.state.value.serverName ?: "active"}"
                } else {
                    "direct"
                }
                val report = tester.measure(target, samples = samples, path = path)
                _state.update { it.copy(reports = (listOf(report) + it.reports).take(40)) }
            }
            _state.update { it.copy(busy = null) }
        }
    }

    /**
     * Route optimizer: brings each configured relay up in turn, measures the
     * active game's first probe target (or a neutral anchor) through that
     * tunnel, then ranks the relays by the measured values.
     */
    fun optimizeRoute() {
        if (_state.value.optimizerRunning) return
        val targets = buildList {
            addAll(_state.value.activeGame?.probeTargets ?: emptyList())
            add(BuiltInCatalog.referenceAnchors.first())
        }
        val probe = targets.first()
        val candidates = _state.value.servers.filter { it.enabled && it.isConfigured }
        if (candidates.isEmpty()) {
            _state.update { it.copy(message = "no usable relay configured — import one first") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    optimizerRunning = true,
                    optimizerLog = listOf("optimizer started with ${candidates.size} candidate(s), probe ${probe.host}:${probe.port}"),
                )
            }
            val measured = _state.value.measuredAvgMs.toMutableMap()
            val statuses = _state.value.statuses.toMutableMap()
            for (server in candidates) {
                log("→ bringing up ${server.name} (${server.endpoint})")
                val connectResult = connectInternal(server)
                if (connectResult.isFailure) {
                    log("  connect failed: ${connectResult.exceptionOrNull()?.message}")
                    statuses[server.id] = ServerStatus(
                        serverId = server.id,
                        health = com.vz.networkbooster.data.model.ServerHealth.FAILING,
                        lastCheckEpochMs = System.currentTimeMillis(),
                        detail = "handshake/tunnel failed during optimizer run",
                    )
                    continue
                }
                delay(1_500)
                val report = tester.measure(probe, samples = 5, path = "tunnel:${server.name}")
                val avg = report.avgMs
                if (avg != null) {
                    measured[server.id] = avg
                    log("  measured avg %.1f ms, jitter %s, loss %.0f%%".format(
                        avg,
                        report.jitterMs?.let { "%.1f ms".format(it) } ?: "n/a",
                        report.lossPercent,
                    ))
                    statuses[server.id] = ServerStatus(
                        serverId = server.id,
                        health = if (report.lossPercent < 20.0 && avg < 150.0) {
                            com.vz.networkbooster.data.model.ServerHealth.HEALTHY
                        } else {
                            com.vz.networkbooster.data.model.ServerHealth.DEGRADED
                        },
                        lastCheckEpochMs = System.currentTimeMillis(),
                        detail = "optimizer probe avg ${report.avgMs} ms, loss ${report.lossPercent}%",
                    )
                } else {
                    log("  no samples through this relay (${report.note})")
                }
                VpnManager.disconnect()
                delay(400)
            }
            val ranking = SmartRouter.rank(
                servers = _state.value.servers,
                statuses = statuses,
                measuredAvgMs = measured,
                regionHint = _state.value.activeGame?.regionHint ?: "",
            )
            val best = ranking.firstOrNull()
            if (best != null) log("best route: ${best.server.name} (score ${best.score})")
            serverRepo.saveStatuses(statuses)
            _state.update {
                it.copy(
                    optimizerRunning = false,
                    measuredAvgMs = measured,
                    statuses = statuses,
                    ranking = ranking,
                    activeServerId = best?.server?.id ?: it.activeServerId,
                    message = "optimizer finished",
                )
            }
        }
    }

    private fun log(line: String) = _state.update { it.copy(optimizerLog = it.optimizerLog + line) }

    private fun recomputeRanking() {
        val snapshot = _state.value
        val ranking = SmartRouter.rank(
            servers = snapshot.servers,
            statuses = snapshot.statuses,
            measuredAvgMs = snapshot.measuredAvgMs,
            regionHint = snapshot.activeGame?.regionHint ?: "",
        )
        _state.update { it.copy(ranking = ranking) }
    }

    private suspend fun loadLaunchablePackages(): List<String> = runCatching {
        val app = getApplication<Application>()
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        withContext(Dispatchers.IO) {
            app.packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .mapNotNull { it.activityInfo?.packageName }
                .distinct()
                .sorted()
        }
    }.getOrDefault(emptyList())

    private fun keyNameFor(serverId: String) = "server_key_$serverId"
}
