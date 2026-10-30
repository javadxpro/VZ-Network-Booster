package com.vz.networkbooster.vpn

import android.content.Context
import android.net.VpnService
import android.util.Log
import com.vz.networkbooster.data.model.ServerProfile
import com.vz.networkbooster.data.model.TunnelPhase
import com.vz.networkbooster.data.model.TunnelUiState
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import kotlin.math.min

/**
 * Single owner of the WireGuard backend and the currently active tunnel.
 *
 * The actual tun device is created by the WireGuard library's own VpnService
 * (`com.wireguard.android.backend.GoBackend$VpnService`), which is declared in
 * the tunnel AAR manifest. This class orchestrates it and exposes state.
 */
object VpnManager {

    private const val TAG = "VzVpnManager"

    private var appContext: Context? = null
    private var backend: GoBackend? = null
    private var tunnel: WgTunnel? = null
    private var activeServer: ServerProfile? = null

    private val _state = MutableStateFlow(TunnelUiState())
    val state: StateFlow<TunnelUiState> = _state.asStateFlow()

    /** Everything needed to re-establish the same connection after a drop. */
    data class ConnectRequest(
        val server: ServerProfile,
        val configText: String,
        val includePackages: Set<String>,
        val excludePackages: Set<String>,
    )

    private var lastRequest: ConnectRequest? = null
    private var wantsUp = false
    private var reconnectAttempts = 0

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            backend = GoBackend(context.applicationContext)
        }
    }

    fun activeServer(): ServerProfile? = activeServer

    /** True when the user already granted VPN consent for this app. */
    fun hasVpnConsent(): Boolean {
        val ctx = appContext ?: return false
        return VpnService.prepare(ctx) == null
    }

    suspend fun connect(request: ConnectRequest): Result<Unit> = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext Result.failure(IllegalStateException("VpnManager not initialised"))
        _state.update {
            it.copy(
                phase = TunnelPhase.CONNECTING,
                serverId = request.server.id,
                serverName = request.server.name,
                message = "establishing WireGuard tunnel",
                sinceEpochMs = System.currentTimeMillis(),
            )
        }
        try {
            val wgConfig: Config = Config.parse(ByteArrayInputStream(request.configText.toByteArray(Charsets.UTF_8)))
            val newTunnel = WgTunnel(WgTunnel.sanitizeName("vz-${request.server.id}"), wgConfig)
            val be = backend ?: GoBackend(ctx).also { backend = it }
            be.setState(newTunnel, Tunnel.State.UP, wgConfig)
            tunnel = newTunnel
            activeServer = request.server
            lastRequest = request
            wantsUp = true
            reconnectAttempts = 0
            _state.update {
                it.copy(
                    phase = TunnelPhase.CONNECTED,
                    serverId = request.server.id,
                    serverName = request.server.name,
                    message = "tunnel up",
                    sinceEpochMs = System.currentTimeMillis(),
                )
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.w(TAG, "connect failed", t)
            _state.update {
                it.copy(
                    phase = TunnelPhase.ERROR,
                    serverId = request.server.id,
                    serverName = request.server.name,
                    message = describe(t),
                )
            }
            Result.failure(t)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        wantsUp = false
        reconnectAttempts = 0
        val current = tunnel
        if (current != null) {
            runCatching { backend?.setState(current, Tunnel.State.DOWN, null) }
                .onFailure { Log.w(TAG, "disconnect failed", it) }
        }
        tunnel = null
        activeServer = null
        _state.update {
            TunnelUiState(phase = TunnelPhase.IDLE, message = "disconnected", reconnects = it.reconnects)
        }
    }

    /** Current wireguard-go counters, or null when the tunnel is down. */
    suspend fun readTrafficStats(): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        val current = tunnel ?: return@withContext null
        val be = backend ?: return@withContext null
        runCatching {
            val stats = be.getStatistics(current)
            stats.totalRx() to stats.totalTx()
        }.getOrNull()
    }

    /**
     * One watchdog step: when auto-reconnect is enabled and the tunnel is
     * supposed to be up but is not, retry with capped exponential backoff.
     */
    suspend fun autoReconnectTick(enabled: Boolean) {
        if (!enabled || !wantsUp) return
        val snapshot = _state.value
        if (snapshot.isBusy) return
        if (snapshot.isUp) {
            reconnectAttempts = 0
            return
        }
        val request = lastRequest ?: return
        val attempt = reconnectAttempts
        val waitMs = min(30_000L, 2_000L * (1 shl min(attempt, 4)))
        _state.update {
            it.copy(
                phase = TunnelPhase.RECONNECTING,
                message = "auto reconnect attempt ${attempt + 1} in ${waitMs / 1000}s",
                reconnects = it.reconnects + 1,
            )
        }
        delay(waitMs)
        if (!wantsUp || _state.value.isUp) return
        reconnectAttempts = attempt + 1
        connect(request)
    }

    fun wantsConnection(): Boolean = wantsUp

    /** Called by the watchdog service to publish wireguard-go counters. */
    fun publishTraffic(rx: Long, tx: Long) = TrafficMonitor.publish(rx, tx)

    fun onServiceDestroyed() {
        _state.update { it.copy(phase = TunnelPhase.IDLE, message = "service stopped") }
    }

    private fun describe(t: Throwable): String = when (t) {
        is BackendException -> "backend: ${t.reason}"
        else -> t.message ?: t.javaClass.simpleName
    }
}
