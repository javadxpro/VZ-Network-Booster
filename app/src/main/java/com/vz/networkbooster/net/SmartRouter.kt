package com.vz.networkbooster.net

import com.vz.networkbooster.data.model.RouteScore
import com.vz.networkbooster.data.model.ServerHealth
import com.vz.networkbooster.data.model.ServerProfile
import com.vz.networkbooster.data.model.ServerStatus

/**
 * Deterministic route ranking.
 *
 * Inputs are *measured* latencies when they exist (keyed by server id), the
 * last health check, and static operator metadata (region, capacity, dedicated).
 * A server without a measurement is not silently treated as fast: it gets an
 * explicit "unmeasured" penalty and the reason is surfaced in the UI.
 */
object SmartRouter {

    private const val LATENCY_WEIGHT = 0.8

    fun rank(
        servers: List<ServerProfile>,
        statuses: Map<String, ServerStatus> = emptyMap(),
        measuredAvgMs: Map<String, Double> = emptyMap(),
        regionHint: String = "",
    ): List<RouteScore> {
        return servers
            .filter { it.enabled && it.isConfigured }
            .map { server -> score(server, statuses[server.id], measuredAvgMs[server.id], regionHint) }
            .sortedByDescending { it.score }
    }

    private fun score(
        server: ServerProfile,
        status: ServerStatus?,
        measured: Double?,
        regionHint: String,
    ): RouteScore {
        var score = 100.0
        val reasons = mutableListOf<String>()

        if (measured != null) {
            score -= measured * LATENCY_WEIGHT
            reasons += "measured %.1f ms".format(measured)
        } else {
            score -= 12.0
            reasons += "no measurement yet (−12)"
        }

        when (status?.health ?: ServerHealth.UNKNOWN) {
            ServerHealth.HEALTHY -> reasons += "health OK"
            ServerHealth.DEGRADED -> {
                score -= 20.0
                reasons += "health degraded (−20)"
            }

            ServerHealth.FAILING -> {
                score -= 60.0
                reasons += "health failing (−60)"
            }

            ServerHealth.UNKNOWN -> {
                score -= 8.0
                reasons += "health unverified (−8)"
            }
        }

        if (regionHint.isNotBlank() && server.region.isNotBlank() &&
            server.region.contains(regionHint, ignoreCase = true)
        ) {
            score += 5.0
            reasons += "region matches game hint (+5)"
        }
        if (server.isDedicated) {
            score += 3.0
            reasons += "dedicated relay (+3)"
        }
        if (server.capacity > 0) {
            val bonus = (server.capacity / 100).coerceAtMost(3)
            score += bonus
            reasons += "declared capacity ${server.capacity} (+$bonus)"
        }

        return RouteScore(
            server = server,
            score = QualityStats.round1(score.coerceIn(0.0, 150.0)),
            reasons = reasons,
        )
    }
}
