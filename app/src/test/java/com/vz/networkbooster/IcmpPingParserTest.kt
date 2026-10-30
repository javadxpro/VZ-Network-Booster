package com.vz.networkbooster

import com.vz.networkbooster.net.IcmpPingParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcmpPingParserTest {

    @Test
    fun parsesToyboxPingOutput() {
        val output = """
            PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.
            64 bytes from 1.1.1.1: icmp_seq=1 ttl=58 time=12.1 ms
            64 bytes from 1.1.1.1: icmp_seq=2 ttl=58 time=13.4 ms
            64 bytes from 1.1.1.1: icmp_seq=3 ttl=58 time=15.9 ms
            64 bytes from 1.1.1.1: icmp_seq=4 ttl=58 time=19.2 ms

            --- 1.1.1.1 ping statistics ---
            4 packets transmitted, 4 received, 0% packet loss, time 604ms
            rtt min/avg/max/mdev = 12.100/15.150/19.200/2.806 ms
        """.trimIndent()

        val result = IcmpPingParser.parse(output)
        assertNotNull(result)
        result!!
        assertEquals(4, result.transmitted)
        assertEquals(4, result.received)
        assertEquals(4, result.rtts.size)
        assertEquals(12.1, result.minMs!!, 0.001)
        assertEquals(15.15, result.avgMs!!, 0.001)
        assertEquals(19.2, result.maxMs!!, 0.001)
        assertEquals(2.806, result.mdevMs!!, 0.001)
    }

    @Test
    fun parsesPacketLoss() {
        val output = """
            PING 10.255.255.1 (10.255.255.1) 56(84) bytes of data.

            --- 10.255.255.1 ping statistics ---
            3 packets transmitted, 0 received, 100% packet loss, time 2043ms
        """.trimIndent()

        val result = IcmpPingParser.parse(output)
        assertNotNull(result)
        assertEquals(3, result!!.transmitted)
        assertEquals(0, result.received)
        assertEquals(100.0, result.lossPercent, 0.001)
        assertNull(result.avgMs)
    }

    @Test
    fun parsesBusyboxStyleWithoutMdev() {
        val output = """
            PING 8.8.8.8 (8.8.8.8): 56 data bytes
            64 bytes from 8.8.8.8: seq=0 ttl=117 time=24.451 ms
            64 bytes from 8.8.8.8: seq=1 ttl=117 time=26.104 ms
            --- 8.8.8.8 ping statistics ---
            2 packets transmitted, 2 packets received, 0% packet loss
            round-trip min/avg/max = 24.451/25.277/26.104 ms
        """.trimIndent()

        val result = IcmpPingParser.parse(output)
        assertNotNull(result)
        assertEquals(2, result!!.received)
        assertEquals(24.451, result.minMs!!, 0.001)
        assertEquals(2, result.rtts.size)
    }

    @Test
    fun partialLossKeepsSamples() {
        val output = """
            64 bytes from 1.1.1.1: icmp_seq=1 ttl=58 time=11.0 ms
            64 bytes from 1.1.1.1: icmp_seq=3 ttl=58 time=13.0 ms
            --- 1.1.1.1 ping statistics ---
            4 packets transmitted, 2 received, 50% packet loss, time 3001ms
        """.trimIndent()

        val result = IcmpPingParser.parse(output)!!
        assertEquals(4, result.transmitted)
        assertEquals(2, result.received)
        assertEquals(50.0, result.lossPercent, 0.001)
        assertEquals(2.0, com.vz.networkbooster.net.QualityStats.jitterMs(result.rtts)!!, 0.001)
    }

    @Test
    fun nonPingOutputIsRejected() {
        assertNull(IcmpPingParser.parse("su: permission denied"))
        assertNull(IcmpPingParser.parse(""))
    }

    @Test
    fun lossPercentIsClampedForWeirdCounts() {
        val result = IcmpPingParser.Result(0, 0, emptyList(), null, null, null, null)
        assertEquals(0.0, result.lossPercent, 0.001)
        assertTrue(result.rtts.isEmpty())
    }
}
