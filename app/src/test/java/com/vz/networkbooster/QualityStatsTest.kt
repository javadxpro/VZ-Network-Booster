package com.vz.networkbooster

import com.vz.networkbooster.net.QualityStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityStatsTest {

    @Test
    fun averageOfEmptyIsNull() {
        assertNull(QualityStats.average(emptyList()))
    }

    @Test
    fun averageIsComputed() {
        assertEquals(30.0, QualityStats.average(listOf(10.0, 20.0, 60.0))!!, 0.0001)
    }

    @Test
    fun jitterNeedsTwoSamples() {
        assertNull(QualityStats.jitterMs(listOf(20.0)))
    }

    @Test
    fun jitterIsMeanAbsoluteDifference() {
        // |30-20| = 10, |60-30| = 30 -> mean 20
        assertEquals(20.0, QualityStats.jitterMs(listOf(20.0, 30.0, 60.0))!!, 0.0001)
    }

    @Test
    fun lossPercent() {
        assertEquals(0.0, QualityStats.lossPercent(10, 10), 0.0001)
        assertEquals(30.0, QualityStats.lossPercent(10, 7), 0.0001)
        assertEquals(100.0, QualityStats.lossPercent(10, 0), 0.0001)
        assertEquals(0.0, QualityStats.lossPercent(0, 0), 0.0001)
    }
}
