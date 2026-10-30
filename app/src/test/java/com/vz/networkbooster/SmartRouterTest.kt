package com.vz.networkbooster

import com.vz.networkbooster.data.model.ServerHealth
import com.vz.networkbooster.data.model.ServerProfile
import com.vz.networkbooster.data.model.ServerStatus
import com.vz.networkbooster.net.SmartRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRouterTest {

    private fun server(
        id: String,
        region: String = "eu",
        dedicated: Boolean = false,
        capacity: Int = 0,
        enabled: Boolean = true,
        configured: Boolean = true,
    ) = ServerProfile(
        id = id,
        name = id,
        region = region,
        endpointHost = if (configured) "relay-$id.example.com" else "",
        endpointPort = 51820,
        serverPublicKey = "UALKPz9Q6Fvabt+m4rsA3/jHuD4pu1QsxeiG/XEofrc=",
        address = "10.66.0.2/32",
        isDedicated = dedicated,
        capacity = capacity,
        enabled = enabled,
    )

    @Test
    fun measuredLatencyDominatesRanking() {
        val slow = server("slow")
        val fast = server("fast")
        val ranking = SmartRouter.rank(
            servers = listOf(slow, fast),
            measuredAvgMs = mapOf("slow" to 180.0, "fast" to 40.0),
        )
        assertEquals("fast", ranking.first().server.id)
        assertTrue(ranking.first().score > ranking.last().score)
    }

    @Test
    fun failingHealthPushesServerDown() {
        val healthy = server("a")
        val failing = server("b")
        val ranking = SmartRouter.rank(
            servers = listOf(healthy, failing),
            statuses = mapOf(
                "a" to ServerStatus("a", ServerHealth.HEALTHY, 1L, "ok"),
                "b" to ServerStatus("b", ServerHealth.FAILING, 1L, "dead"),
            ),
            measuredAvgMs = mapOf("a" to 60.0, "b" to 60.0),
        )
        assertEquals("a", ranking.first().server.id)
    }

    @Test
    fun unconfiguredAndDisabledServersAreExcluded() {
        val ranking = SmartRouter.rank(
            servers = listOf(
                server("ok"),
                server("disabled", enabled = false),
                server("unconfigured", configured = false),
            )
        )
        assertEquals(1, ranking.size)
        assertEquals("ok", ranking.first().server.id)
    }

    @Test
    fun unmeasuredServerGetsPenaltyAndReason() {
        val ranking = SmartRouter.rank(servers = listOf(server("x")))
        val entry = ranking.first()
        assertTrue(entry.reasons.any { it.contains("no measurement") })
        assertTrue(entry.score < 100.0)
    }

    @Test
    fun regionHintAndDedicatedAddBonus() {
        val eu = server("eu", region = "eu-west", dedicated = true, capacity = 500)
        val us = server("us", region = "na-west")
        val ranking = SmartRouter.rank(
            servers = listOf(eu, us),
            measuredAvgMs = mapOf("eu" to 50.0, "us" to 50.0),
            regionHint = "eu",
        )
        assertEquals("eu", ranking.first().server.id)
        assertTrue(ranking.first().reasons.any { it.contains("region matches") })
    }
}
