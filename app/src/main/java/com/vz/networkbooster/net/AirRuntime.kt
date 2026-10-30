package com.vz.networkbooster.net

import com.vz.networkbooster.data.model.ProbeProtocol
import com.vz.networkbooster.data.model.ProbeTarget
import com.vz.networkbooster.data.model.ServerProfile
import java.util.concurrent.ConcurrentHashMap

/**
 * Runtime half of AIR: measures every configured relay that exposes a probe
 * target, keeps its smoothed metrics and asks [AirEngine] whether the active
 * path should change.
 *
 * Measurement honesty:
 *  - candidates are measured **pre-tunnel** (TCP handshake to the relay's own
 *    probe target), which works while another relay is active and costs nothing
 *    in traffic through the tunnel;
 *  - the end-to-end truth for the *active* path still comes from the Measure tab
 *    (through the tunnel), so the UI always shows which one it is;
 *  - relays without a probe target are never guessed: they are listed as
 *    "unmeasurable" instead of being ranked by a made-up number.
 *
 * The loop itself is driven by [com.vz.networkbooster.vpn.BoosterService], so it
 * only runs while the tunnel service is alive.
 */
object AirRuntime {

    data class Context(
        val enabled: Boolean,
        val candidates: List<ServerProfile>,
        val activeServerId: String?,
        val autoSwitch: Boolean,
    )

    private val tester = QualityTester(interProbeDelayMs = 0)
    private val metrics = ConcurrentHashMap<String, PathMetrics>()

    @Volatile
    var context: Context? = null

    @Volatile
    var activePathId: String? = null

    @Volatile
    var onLog: ((String) -> Unit)? = null

    /** Reconnect to another relay; returns true when the tunnel came up. */
    @Volatile
    var switchTo: (suspend (ServerProfile) -> Boolean)? = null

    @Volatile
    private var lastSwitchAtMs: Long = 0L

    @Volatile
    private var nextProbeAtMs: Long = 0L

    @Volatile
    var lastDecision: AirEngine.Decision? = null
        private set

    fun metricsSnapshot(): Map<String, PathMetrics> = metrics.toMap()

    fun scoresSnapshot(): List<Pair<String, Double>> =
        metricsSnapshot().mapValues { (_, value) -> AirEngine.score(value) }
            .map { (id, score) -> id to Math.round(score * 10.0) / 10.0 }
            .sortedByDescending { it.second }

    fun reset() {
        metrics.clear()
        lastSwitchAtMs = 0L
        nextProbeAtMs = 0L
        lastDecision = null
    }

    suspend fun tick(nowMs: Long = System.currentTimeMillis()): AirEngine.Decision? {
        val ctx = context ?: return null
        if (!ctx.enabled) return null

        val measurable = ctx.candidates.filter { it.enabled && it.isConfigured && it.hasProbeTarget }
        val unmeasurable = ctx.candidates.filter { it.isEnabledForAir() && !it.hasProbeTarget }
        if (unmeasurable.isNotEmpty()) {
            onLog?.invoke(
                "AIR: ${unmeasurable.size} relay(s) have no probe target and cannot be compared: " +
                    unmeasurable.joinToString(", ") { it.name }
            )
        }
        if (measurable.isEmpty()) return null

        if (nowMs < nextProbeAtMs) return lastDecision

        for (server in measurable) {
            val target = ProbeTarget(
                label = "air:${server.name}",
                host = server.probeHost!!,
                port = server.probePort!!,
                protocol = ProbeProtocol.TCP,
            )
            val report = tester.measure(target, samples = 3, timeoutMs = 1_200, path = "air")
            val sample = PathSample(
                pathId = server.id,
                rttMs = report.avgMs,
                jitterMs = report.jitterMs,
                lossPercent = report.lossPercent,
                timestampMs = nowMs,
                source = PathSample.Source.PRE_TUNNEL,
            )
            metrics[server.id] = AirEngine.update(metrics[server.id], sample)
        }

        val decision = AirEngine.decide(
            activePathId = activePathId ?: ctx.activeServerId,
            metrics = metricsSnapshot(),
            nowMs = nowMs,
            lastSwitchAtMs = lastSwitchAtMs,
        )
        lastDecision = decision
        nextProbeAtMs = nowMs + decision.nextProbeIntervalMs

        if (decision.action == AirEngine.Action.SWITCH && ctx.autoSwitch) {
            val target = measurable.firstOrNull { it.id == decision.targetPathId }
            if (target == null) {
                onLog?.invoke("AIR: wanted to switch to ${decision.targetPathId} but it is not measurable")
            } else {
                lastSwitchAtMs = nowMs
                onLog?.invoke("AIR: switching to ${target.name} — ${decision.reasons.joinToString("; ")}")
                val ok = switchTo?.invoke(target) ?: false
                onLog?.invoke(if (ok) "AIR: now on ${target.name}" else "AIR: switch to ${target.name} failed")
            }
        } else if (decision.action == AirEngine.Action.KEEP) {
            onLog?.invoke("AIR: keep — ${decision.reasons.joinToString("; ")}")
        } else if (decision.action == AirEngine.Action.NO_DATA) {
            onLog?.invoke("AIR: no data yet — ${decision.reasons.joinToString("; ")}")
        }
        return decision
    }

    private fun ServerProfile.isEnabledForAir(): Boolean = enabled && isConfigured
}
