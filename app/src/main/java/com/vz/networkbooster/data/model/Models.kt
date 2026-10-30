package com.vz.networkbooster.data.model

/** Transport used for a reachability/latency probe. */
enum class ProbeProtocol { TCP, UDP }

/**
 * A single measurable destination. For game traffic the practical, root-free
 * measurement is a TCP handshake (or UDP round-trip when the peer answers).
 */
data class ProbeTarget(
    val label: String,
    val host: String,
    val port: Int,
    val protocol: ProbeProtocol = ProbeProtocol.TCP,
)

/**
 * A game profile. Everything the booster needs to know about one game:
 * which packages belong to it, which destinations are worth probing and which
 * server is preferred by default.
 */
data class GameProfile(
    val id: String,
    val displayName: String,
    val packages: List<String>,
    val regionHint: String = "",
    val probeTargets: List<ProbeTarget> = emptyList(),
    val preferredServerId: String? = null,
    val useSplitTunnel: Boolean = true,
    val notes: String = "",
    val isBuiltIn: Boolean = false,
)

/**
 * A WireGuard relay/"booster" server the user can add.
 *
 * [serverPublicKey] is the peer public key of the relay, never a private key.
 * Private keys are generated on the device and never leave it unencrypted.
 * [capacity] is an operator-declared seat count (0 = unknown) and is only used
 * as a tie-breaker by the router, not as a measurement.
 */
data class ServerProfile(
    val id: String,
    val name: String,
    val region: String,
    val endpointHost: String,
    val endpointPort: Int = 51820,
    val serverPublicKey: String,
    val presharedKey: String? = null,
    val address: String,
    val allowedIps: String = "0.0.0.0/0, ::/0",
    val dns: String = "1.1.1.1",
    val mtu: Int = 1420,
    val protocol: String = "wireguard",
    val capacity: Int = 0,
    val isDedicated: Boolean = false,
    val probeHost: String? = null,
    val probePort: Int? = null,
    val enabled: Boolean = true,
    val notes: String = "",
    val isBuiltIn: Boolean = false,
) {
    val endpoint: String get() = "$endpointHost:$endpointPort"

    /** A server can only be used if the minimal peer data is present. */
    val isConfigured: Boolean
        get() = endpointHost.isNotBlank() && serverPublicKey.isNotBlank() && address.isNotBlank()

    val hasProbeTarget: Boolean get() = !probeHost.isNullOrBlank() && (probePort ?: 0) > 0
}

enum class ServerHealth { UNKNOWN, HEALTHY, DEGRADED, FAILING }

data class ServerStatus(
    val serverId: String,
    val health: ServerHealth = ServerHealth.UNKNOWN,
    val lastCheckEpochMs: Long = 0L,
    val detail: String = "not measured yet",
)

enum class TunnelPhase { IDLE, PREPARING, CONNECTING, CONNECTED, RECONNECTING, ERROR }

data class TunnelUiState(
    val phase: TunnelPhase = TunnelPhase.IDLE,
    val serverId: String? = null,
    val serverName: String? = null,
    val message: String = "",
    val sinceEpochMs: Long = 0L,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val reconnects: Int = 0,
) {
    val isBusy: Boolean
        get() = phase == TunnelPhase.PREPARING || phase == TunnelPhase.CONNECTING ||
            phase == TunnelPhase.RECONNECTING

    val isUp: Boolean get() = phase == TunnelPhase.CONNECTED
}

/**
 * Result of one measurement run. All latency numbers are what the device
 * actually observed; nothing here is simulated.
 */
data class QualityReport(
    val targetLabel: String,
    val host: String,
    val port: Int,
    val protocol: ProbeProtocol,
    val path: String,
    val attempted: Int,
    val received: Int,
    val minMs: Double?,
    val avgMs: Double?,
    val maxMs: Double?,
    val jitterMs: Double?,
    val lossPercent: Double,
    val timestampMs: Long,
    val note: String = "",
) {
    val summary: String
        get() = buildString {
            append(targetLabel)
            append(" — ")
            if (avgMs == null) append("no samples") else append("avg %.1f ms".format(avgMs))
            if (jitterMs != null) append(", jitter %.1f ms".format(jitterMs))
            append(", loss %.0f%%".format(lossPercent))
            append(" (")
            append(path)
            append(")")
        }
}

/** One entry of a Smart Routing ranking, with the reasoning that produced it. */
data class RouteScore(
    val server: ServerProfile,
    val score: Double,
    val reasons: List<String>,
)
