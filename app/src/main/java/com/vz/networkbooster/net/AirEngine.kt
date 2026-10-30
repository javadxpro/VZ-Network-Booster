package com.vz.networkbooster.net

/**
 * AIR — Adaptive Intelligent Routing (our own implementation).
 *
 * There is no RFC or open-source component called "AIR"; vendors use the term as
 * branding. This file is the deterministic engine behind our version:
 *
 *  1. every path (relay) gets EWMA-smoothed RTT / jitter / loss metrics,
 *  2. each path is scored with a transparent formula (no black box),
 *  3. switching happens only when the measured improvement exceeds a threshold
 *     (hysteresis) and a cooldown has elapsed (no flapping),
 *  4. repeated failures trigger failover regardless of score,
 *  5. the probing interval adapts: fast while the path is unstable, slow while
 *     it is clean (less battery, no measurement noise).
 *
 * Everything here is pure Kotlin and unit tested on the JVM.
 */
data class PathSample(
    val pathId: String,
    /** null means "no reply at all" — counted as a failure, not as 0 ms. */
    val rttMs: Double?,
    val jitterMs: Double?,
    val lossPercent: Double,
    val timestampMs: Long,
    val source: Source = Source.PRE_TUNNEL,
) {
    enum class Source {
        /** Measured to the relay itself (works without being connected to it). */
        PRE_TUNNEL,

        /** Measured end-to-end through that relay (what actually matters). */
        END_TO_END,
    }
}

data class PathMetrics(
    val pathId: String,
    val rttMs: Double,
    val jitterMs: Double,
    val lossPercent: Double,
    val samples: Int,
    val consecutiveFailures: Int,
    val updatedAtMs: Long,
    val source: PathSample.Source = PathSample.Source.PRE_TUNNEL,
) {
    /** How much this path's numbers can be trusted yet (0..1). */
    val confidence: Double get() = (samples / 5.0).coerceIn(0.0, 1.0)
}

object AirEngine {

    data class Config(
        val alpha: Double = 0.4,
        val minImprovement: Double = 8.0,
        val switchCooldownMs: Long = 45_000L,
        val failureThreshold: Int = 3,
        val stableProbeIntervalMs: Long = 15_000L,
        val unstableProbeIntervalMs: Long = 4_000L,
        val rttCeilingMs: Double = 500.0,
        val jitterUnstableMs: Double = 15.0,
        val lossUnstablePercent: Double = 2.0,
        /** A path needs at least this many samples before we are willing to leave it. */
        val minSamplesToSwitch: Int = 2,
    )

    enum class Action { KEEP, SWITCH, NO_DATA }

    data class Decision(
        val action: Action,
        val targetPathId: String?,
        val reasons: List<String>,
        val nextProbeIntervalMs: Long,
        val currentScore: Double,
        val bestScore: Double,
    )

    fun update(previous: PathMetrics?, sample: PathSample, config: Config = Config()): PathMetrics {
        val failed = sample.rttMs == null
        if (previous == null) {
            return PathMetrics(
                pathId = sample.pathId,
                rttMs = sample.rttMs ?: 0.0,
                jitterMs = sample.jitterMs ?: 0.0,
                lossPercent = sample.lossPercent,
                samples = 1,
                consecutiveFailures = if (failed) 1 else 0,
                updatedAtMs = sample.timestampMs,
                source = sample.source,
            )
        }
        val alpha = config.alpha.coerceIn(0.05, 0.95)
        val rtt = if (failed) previous.rttMs else alpha * sample.rttMs!! + (1 - alpha) * previous.rttMs
        val jitter = when {
            sample.jitterMs == null -> previous.jitterMs
            else -> alpha * sample.jitterMs + (1 - alpha) * previous.jitterMs
        }
        val loss = alpha * sample.lossPercent.coerceIn(0.0, 100.0) + (1 - alpha) * previous.lossPercent
        return previous.copy(
            rttMs = rtt,
            jitterMs = jitter,
            lossPercent = loss,
            samples = previous.samples + 1,
            consecutiveFailures = if (failed) previous.consecutiveFailures + 1 else 0,
            updatedAtMs = sample.timestampMs,
            source = sample.source,
        )
    }

    /**
     * Transparent score in 0..100 (higher is better):
     *   latency: 1 point per 5 ms (capped at [Config.rttCeilingMs])
     *   jitter : 0.6 point per ms
     *   loss   : 1.2 point per percent
     *   failed probes: 15 points each, max 5
     */
    fun score(metrics: PathMetrics, config: Config = Config()): Double {
        val latencyPenalty = metrics.rttMs.coerceIn(0.0, config.rttCeilingMs) / 5.0
        val jitterPenalty = metrics.jitterMs.coerceIn(0.0, 100.0) * 0.6
        val lossPenalty = metrics.lossPercent.coerceIn(0.0, 100.0) * 1.2
        val failurePenalty = metrics.consecutiveFailures.coerceAtMost(5) * 15.0
        return (100.0 - latencyPenalty - jitterPenalty - lossPenalty - failurePenalty).coerceIn(0.0, 100.0)
    }

    fun probeInterval(metrics: PathMetrics?, config: Config = Config()): Long {
        if (metrics == null) return config.unstableProbeIntervalMs
        val unstable = metrics.lossPercent > config.lossUnstablePercent ||
            metrics.jitterMs > config.jitterUnstableMs ||
            metrics.consecutiveFailures > 0
        return if (unstable) config.unstableProbeIntervalMs else config.stableProbeIntervalMs
    }

    fun decide(
        activePathId: String?,
        metrics: Map<String, PathMetrics>,
        nowMs: Long,
        lastSwitchAtMs: Long,
        config: Config = Config(),
    ): Decision {
        val usable = metrics.values.filter { it.samples > 0 }
        if (usable.isEmpty()) {
            return Decision(
                action = Action.NO_DATA,
                targetPathId = null,
                reasons = listOf("no measured path yet — run a probe cycle"),
                nextProbeIntervalMs = config.unstableProbeIntervalMs,
                currentScore = 0.0,
                bestScore = 0.0,
            )
        }

        val current = activePathId?.let { metrics[it] }
        val scored = usable.associateWith { score(it, config) }.toList().sortedByDescending { it.second }
        val best = scored.first()
        val currentScore = current?.let { score(it, config) } ?: -1.0
        val interval = probeInterval(current, config)
        val cooldownLeft = (lastSwitchAtMs + config.switchCooldownMs) - nowMs

        if (current == null) {
            return Decision(
                action = Action.SWITCH,
                targetPathId = best.first.pathId,
                reasons = listOf(
                    "no metrics for the active path; best candidate is ${best.first.pathId} (${fmt(best.second)})"
                ),
                nextProbeIntervalMs = interval,
                currentScore = 0.0,
                bestScore = best.second,
            )
        }

        if (current.consecutiveFailures >= config.failureThreshold) {
            val alternative = scored.firstOrNull { it.first.pathId != current.pathId }
            return if (alternative != null) {
                Decision(
                    action = Action.SWITCH,
                    targetPathId = alternative.first.pathId,
                    reasons = listOf(
                        "failover: ${current.consecutiveFailures} consecutive failures on ${current.pathId}",
                        "next best: ${alternative.first.pathId} (${fmt(alternative.second)})",
                    ),
                    nextProbeIntervalMs = config.unstableProbeIntervalMs,
                    currentScore = currentScore,
                    bestScore = alternative.second,
                )
            } else {
                Decision(
                    action = Action.KEEP,
                    targetPathId = null,
                    reasons = listOf("failover wanted but no alternative path is configured"),
                    nextProbeIntervalMs = config.unstableProbeIntervalMs,
                    currentScore = currentScore,
                    bestScore = currentScore,
                )
            }
        }

        if (best.first.pathId == current.pathId) {
            return Decision(
                action = Action.KEEP,
                targetPathId = null,
                reasons = listOf("active path is already the best measured path (${fmt(best.second)})"),
                nextProbeIntervalMs = interval,
                currentScore = currentScore,
                bestScore = best.second,
            )
        }

        val gap = best.second - currentScore
        val reasons = mutableListOf(
            "active ${current.pathId}: ${fmt(currentScore)}",
            "best ${best.first.pathId}: ${fmt(best.second)} (gap ${fmt(gap)})",
        )
        return when {
            gap < config.minImprovement -> {
                reasons += "gap below ${fmt(config.minImprovement)} threshold — staying (hysteresis)"
                Decision(Action.KEEP, null, reasons, interval, currentScore, best.second)
            }

            cooldownLeft > 0 -> {
                reasons += "switch cooldown active for another ${cooldownLeft / 1000}s"
                Decision(Action.KEEP, null, reasons, interval, currentScore, best.second)
            }

            current.samples < config.minSamplesToSwitch -> {
                reasons += "active path has too few samples (${current.samples}) — measuring further before switching"
                Decision(Action.KEEP, null, reasons, config.unstableProbeIntervalMs, currentScore, best.second)
            }

            else -> {
                reasons += "improvement exceeds threshold — switching to ${best.first.pathId}"
                Decision(Action.SWITCH, best.first.pathId, reasons, config.unstableProbeIntervalMs, currentScore, best.second)
            }
        }
    }

    private fun fmt(value: Double): String = "%.1f".format(value)
}
