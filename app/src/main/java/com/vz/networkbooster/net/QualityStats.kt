package com.vz.networkbooster.net

import kotlin.math.abs

/**
 * Pure statistics helpers — unit tested on the JVM, no Android dependencies.
 */
object QualityStats {

    fun average(values: List<Double>): Double? =
        if (values.isEmpty()) null else values.sum() / values.size

    /**
     * Inter-arrival jitter: mean absolute difference between consecutive RTT
     * samples (the same idea as RFC 3550's |D| term, reported unsmoothed).
     */
    fun jitterMs(values: List<Double>): Double? {
        if (values.size < 2) return null
        var sum = 0.0
        for (i in 1 until values.size) sum += abs(values[i] - values[i - 1])
        return sum / (values.size - 1)
    }

    fun lossPercent(attempted: Int, received: Int): Double {
        if (attempted <= 0) return 0.0
        val lost = (attempted - received).coerceAtLeast(0)
        return lost * 100.0 / attempted
    }

    fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0
}
