package com.vz.networkbooster

import com.vz.networkbooster.net.AirEngine
import com.vz.networkbooster.net.PathMetrics
import com.vz.networkbooster.net.PathSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AirEngineTest {

    private fun metrics(
        id: String,
        rtt: Double,
        jitter: Double = 0.0,
        loss: Double = 0.0,
        samples: Int = 5,
        failures: Int = 0,
    ) = PathMetrics(
        pathId = id,
        rttMs = rtt,
        jitterMs = jitter,
        lossPercent = loss,
        samples = samples,
        consecutiveFailures = failures,
        updatedAtMs = 0L,
    )

    @Test
    fun firstSampleInitialisesMetrics() {
        val sample = PathSample("a", 42.0, 3.0, 0.0, 1_000L)
        val result = AirEngine.update(null, sample)
        assertEquals(42.0, result.rttMs, 0.001)
        assertEquals(1, result.samples)
        assertEquals(0, result.consecutiveFailures)
    }

    @Test
    fun ewmaSmoothsTowardsTheNewSample() {
        val previous = metrics("a", rtt = 100.0, samples = 1)
        val updated = AirEngine.update(previous, PathSample("a", 50.0, 0.0, 0.0, 2_000L))
        // alpha 0.4 -> 0.4*50 + 0.6*100 = 80
        assertEquals(80.0, updated.rttMs, 0.001)
        assertEquals(2, updated.samples)
    }

    @Test
    fun failedProbeCountsAsFailureNotAsZeroLatency() {
        val previous = metrics("a", rtt = 100.0)
        val updated = AirEngine.update(previous, PathSample("a", null, null, 100.0, 3_000L))
        assertEquals(100.0, updated.rttMs, 0.001)
        assertEquals(1, updated.consecutiveFailures)
        assertTrue(updated.lossPercent > 0.0)
    }

    @Test
    fun successResetsFailureCounter() {
        val failing = metrics("a", 100.0, failures = 2)
        val recovered = AirEngine.update(failing, PathSample("a", 90.0, 1.0, 0.0, 4_000L))
        assertEquals(0, recovered.consecutiveFailures)
    }

    @Test
    fun scorePrefersLowerLatencyJitterAndLoss() {
        val good = AirEngine.score(metrics("good", 30.0, 2.0, 0.0))
        val mediocre = AirEngine.score(metrics("mid", 90.0, 12.0, 2.0))
        val bad = AirEngine.score(metrics("bad", 250.0, 40.0, 15.0))
        assertTrue(good > mediocre)
        assertTrue(mediocre > bad)
    }

    @Test
    fun noDataWhenNothingMeasured() {
        val decision = AirEngine.decide("a", emptyMap(), 10_000L, 0L)
        assertEquals(AirEngine.Action.NO_DATA, decision.action)
    }

    @Test
    fun switchesWhenImprovementBeatsThresholdAndCooldownPassed() {
        val decision = AirEngine.decide(
            activePathId = "slow",
            metrics = mapOf(
                "slow" to metrics("slow", 160.0),
                "fast" to metrics("fast", 40.0),
            ),
            nowMs = 120_000L,
            lastSwitchAtMs = 0L,
        )
        assertEquals(AirEngine.Action.SWITCH, decision.action)
        assertEquals("fast", decision.targetPathId)
    }

    @Test
    fun keepsPathWhenImprovementIsBelowHysteresis() {
        val decision = AirEngine.decide(
            activePathId = "a",
            metrics = mapOf(
                "a" to metrics("a", 60.0),
                "b" to metrics("b", 50.0),
            ),
            nowMs = 120_000L,
            lastSwitchAtMs = 0L,
        )
        assertEquals(AirEngine.Action.KEEP, decision.action)
        assertTrue(decision.reasons.any { it.contains("hysteresis") })
    }

    @Test
    fun keepsPathDuringCooldown() {
        val decision = AirEngine.decide(
            activePathId = "slow",
            metrics = mapOf(
                "slow" to metrics("slow", 200.0),
                "fast" to metrics("fast", 30.0),
            ),
            nowMs = 100_000L,
            lastSwitchAtMs = 90_000L,
        )
        assertEquals(AirEngine.Action.KEEP, decision.action)
        assertTrue(decision.reasons.any { it.contains("cooldown") })
    }

    @Test
    fun failoverIgnoresScoreWhenCurrentKeepsFailing() {
        val decision = AirEngine.decide(
            activePathId = "dying",
            metrics = mapOf(
                "dying" to metrics("dying", 20.0, failures = 3),
                "other" to metrics("other", 300.0),
            ),
            nowMs = 100_000L,
            lastSwitchAtMs = 99_000L,
        )
        assertEquals(AirEngine.Action.SWITCH, decision.action)
        assertEquals("other", decision.targetPathId)
        assertTrue(decision.reasons.any { it.contains("failover") })
    }

    @Test
    fun keepsPathWhenItIsAlreadyTheBest() {
        val decision = AirEngine.decide(
            activePathId = "best",
            metrics = mapOf(
                "best" to metrics("best", 25.0),
                "worse" to metrics("worse", 180.0),
            ),
            nowMs = 100_000L,
            lastSwitchAtMs = 0L,
        )
        assertEquals(AirEngine.Action.KEEP, decision.action)
        assertEquals(null, decision.targetPathId)
    }

    @Test
    fun probeIntervalAdaptsToInstability() {
        val stable = AirEngine.probeInterval(metrics("a", 30.0, jitter = 1.0, loss = 0.0))
        val unstableLoss = AirEngine.probeInterval(metrics("b", 30.0, jitter = 1.0, loss = 8.0))
        val unstableJitter = AirEngine.probeInterval(metrics("c", 30.0, jitter = 40.0))
        assertEquals(15_000L, stable)
        assertEquals(4_000L, unstableLoss)
        assertEquals(4_000L, unstableJitter)
    }

    @Test
    fun switchTargetIsNotChosenWithSingleSampleConfidence() {
        val decision = AirEngine.decide(
            activePathId = "slow",
            metrics = mapOf(
                "slow" to metrics("slow", 300.0, samples = 1),
                "fast" to metrics("fast", 20.0, samples = 5),
            ),
            nowMs = 120_000L,
            lastSwitchAtMs = 0L,
            config = AirEngine.Config(),
        )
        // single sample on the active path blocks the switch until it is confirmed
        assertEquals(AirEngine.Action.KEEP, decision.action)
        assertTrue(decision.reasons.any { it.contains("too few samples") })
    }
}
