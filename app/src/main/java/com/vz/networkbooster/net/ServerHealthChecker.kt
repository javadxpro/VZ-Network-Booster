package com.vz.networkbooster.net

import com.vz.networkbooster.data.model.ProbeProtocol
import com.vz.networkbooster.data.model.ProbeTarget
import com.vz.networkbooster.data.model.ServerHealth
import com.vz.networkbooster.data.model.ServerProfile
import com.vz.networkbooster.data.model.ServerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

/**
 * Health check for a relay.
 *
 * A WireGuard endpoint is a UDP port that stays silent unless a valid handshake
 * arrives, so "pinging the WG port" cannot work. What we can honestly check:
 *  - DNS resolution of the endpoint (fails fast for typo'd or dead hosts), and
 *  - if the operator configured an explicit TCP/UDP probe target on the relay,
 *    an actual round-trip time to that target.
 * When no probe target is configured the server is reported UNKNOWN with an
 * explicit "latency unverified" detail instead of a fabricated figure.
 */
class ServerHealthChecker(private val tester: QualityTester = QualityTester(interProbeDelayMs = 0)) {

    suspend fun check(server: ServerProfile): ServerStatus = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!server.isConfigured) {
            return@withContext ServerStatus(
                serverId = server.id,
                health = ServerHealth.UNKNOWN,
                lastCheckEpochMs = now,
                detail = "server profile incomplete (endpoint/public key/address)",
            )
        }

        val resolved = runCatching { InetAddress.getByName(server.endpointHost) }.getOrNull()
            ?: return@withContext ServerStatus(
                serverId = server.id,
                health = ServerHealth.FAILING,
                lastCheckEpochMs = now,
                detail = "endpoint host does not resolve: ${server.endpointHost}",
            )

        val probeHost = server.probeHost
        val probePort = server.probePort
        if (probeHost.isNullOrBlank() || probePort == null) {
            return@withContext ServerStatus(
                serverId = server.id,
                health = ServerHealth.UNKNOWN,
                lastCheckEpochMs = now,
                detail = "endpoint resolves (${resolved.hostAddress}); latency unverified — " +
                    "add a TCP probe target on the relay to measure it",
            )
        }

        val report = tester.measure(
            ProbeTarget("relay probe", probeHost, probePort, ProbeProtocol.TCP),
            samples = 3,
            timeoutMs = 1_500,
            path = "direct",
        )
        val avg = report.avgMs
        if (avg == null) {
            return@withContext ServerStatus(
                serverId = server.id,
                health = ServerHealth.FAILING,
                lastCheckEpochMs = now,
                detail = "probe ${probeHost}:$probePort failed (${report.note.ifBlank { "no reply" }})",
            )
        }
        val health = when {
            report.lossPercent >= 34.0 -> ServerHealth.DEGRADED
            avg <= 120.0 -> ServerHealth.HEALTHY
            avg <= 250.0 -> ServerHealth.DEGRADED
            else -> ServerHealth.FAILING
        }
        ServerStatus(
            serverId = server.id,
            health = health,
            lastCheckEpochMs = now,
            detail = "probe ${probeHost}:$probePort avg ${report.avgMs} ms, loss ${report.lossPercent}%",
        )
    }
}
