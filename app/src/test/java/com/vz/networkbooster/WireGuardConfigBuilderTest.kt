package com.vz.networkbooster

import com.vz.networkbooster.data.model.ServerProfile
import com.vz.networkbooster.vpn.WireGuardConfigBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardConfigBuilderTest {

    private val privateKey = "UALKPz9Q6Fvabt+m4rsA3/jHuD4pu1QsxeiG/XEofrc="
    private val peerKey = "C5SkMsovW4QkhUKFjhgdFGMCdMZD6PtRHYlR5Ro2o+0="

    private fun server() = ServerProfile(
        id = "srv-1",
        name = "fra-01",
        region = "eu",
        endpointHost = "relay.example.com",
        endpointPort = 51820,
        serverPublicKey = peerKey,
        address = "10.66.0.2/32",
        dns = "1.1.1.1",
        mtu = 1420,
    )

    @Test
    fun buildsParseableConfigWithSplitTunnel() {
        val result = WireGuardConfigBuilder.build(
            server = server(),
            privateKeyBase64 = privateKey,
            includePackages = setOf("com.tencent.ig", "com.vz.networkbooster"),
        )
        assertTrue(result.error ?: "", result.valid)
        val text = result.configText
        assertTrue(text.contains("[Interface]"))
        assertTrue(text.contains("[Peer]"))
        assertTrue(text.contains("Endpoint = relay.example.com:51820"))
        assertTrue(text.contains("IncludedApplications = com.tencent.ig, com.vz.networkbooster"))
        assertTrue(text.contains("AllowedIPs = 0.0.0.0/0, ::/0"))
        assertTrue(text.contains("MTU = 1420"))
    }

    @Test
    fun rejectsInvalidKeys() {
        val result = WireGuardConfigBuilder.build(server(), privateKeyBase64 = "not-a-key")
        assertFalse(result.valid)
        assertTrue(result.error!!.contains("private key"))
    }

    @Test
    fun rejectsOutOfRangePort() {
        val result = WireGuardConfigBuilder.build(server().copy(endpointPort = 70000), privateKey)
        assertFalse(result.valid)
        assertTrue(result.error!!.contains("port"))
    }

    @Test
    fun rejectsBadAddressThroughParser() {
        // The WireGuard parser is the final authority on config validity.
        val result = WireGuardConfigBuilder.build(server().copy(address = "not-an-address"), privateKey)
        assertFalse(result.valid)
    }

    @Test
    fun fullTunnelHasNoApplicationList() {
        val result = WireGuardConfigBuilder.build(server(), privateKey, includePackages = emptySet())
        assertTrue(result.valid)
        assertFalse(result.configText.contains("IncludedApplications"))
    }
}
