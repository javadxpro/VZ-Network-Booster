package com.vz.networkbooster

import com.vz.networkbooster.data.importx.ConfigImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigImporterTest {

    @Test
    fun parsesJsonBundle() {
        val json = """
            {
              "name": "fra-01",
              "region": "eu",
              "endpoint": "relay.example.com:51820",
              "serverPublicKey": "C5SkMsovW4QkhUKFjhgdFGMCdMZD6PtRHYlR5Ro2o+0=",
              "address": "10.66.0.2/32",
              "capacity": 200,
              "dedicated": true
            }
        """.trimIndent()
        val imported = ConfigImporter.parseBundleJson(json, "id1", "fallback")
        val server = imported.server
        assertNotNull(server)
        assertEquals("fra-01", server!!.name)
        assertEquals("relay.example.com", server.endpointHost)
        assertEquals(51820, server.endpointPort)
        assertEquals(200, server.capacity)
        assertTrue(server.isDedicated)
        assertTrue(server.isConfigured)
    }

    @Test
    fun bundleWithoutRequiredFieldsFails() {
        val imported = ConfigImporter.parseBundleJson("""{"name":"x"}""", "id2", "fallback")
        assertNull(imported.server)
        assertNotNull(imported.error)
    }

    @Test
    fun bundlePrivateKeyIsIgnored() {
        val json = """{"endpoint":"1.2.3.4:51820","serverPublicKey":"C5SkMsovW4QkhUKFjhgdFGMCdMZD6PtRHYlR5Ro2o+0=",
            "address":"10.0.0.2/32","privateKey":"secret"}"""
        val imported = ConfigImporter.parseBundleJson(json, "id3", "fallback")
        assertNotNull(imported.server)
        assertNull(imported.privateKey)
        assertTrue(imported.warnings.any { it.contains("private key") })
    }

    @Test
    fun parsesWgQuickConfig() {
        val text = """
            [Interface]
            PrivateKey = UALKPz9Q6Fvabt+m4rsA3/jHuD4pu1QsxeiG/XEofrc=
            Address = 10.66.0.2/32
            DNS = 1.1.1.1
            MTU = 1380

            [Peer]
            PublicKey = C5SkMsovW4QkhUKFjhgdFGMCdMZD6PtRHYlR5Ro2o+0=
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = relay.example.com:51820
        """.trimIndent()
        val imported = ConfigImporter.parseWgQuick(text, "id4", "imported")
        val server = imported.server
        assertNotNull(server)
        assertEquals("relay.example.com", server!!.endpointHost)
        assertEquals(51820, server.endpointPort)
        assertEquals(1380, server.mtu)
        assertEquals("10.66.0.2/32", server.address)
        assertEquals("UALKPz9Q6Fvabt+m4rsA3/jHuD4pu1QsxeiG/XEofrc=", imported.privateKey)
    }

    @Test
    fun rejectsNonWgQuickText() {
        val imported = ConfigImporter.parseWgQuick("hello world", "id5", "x")
        assertNull(imported.server)
        assertNotNull(imported.error)
    }

    @Test
    fun detectsRelaySideConfig() {
        val serverConf = """
            [Interface]
            Address = 10.66.0.1/24
            ListenPort = 51820
            PrivateKey = UALKPz9Q6Fvabt+m4rsA3/jHuD4pu1QsxeiG/XEofrc=
            PostUp = iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE
            PostDown = iptables -t nat -D POSTROUTING -o eth0 -j MASQUERADE

            [Peer]
            PublicKey = C5SkMsovW4QkhUKFjhgdFGMCdMZD6PtRHYlR5Ro2o+0=
            AllowedIPs = 10.66.0.2/32
        """.trimIndent()
        assertTrue(ConfigImporter.looksLikeServerConfig(serverConf))
        val imported = ConfigImporter.parseWgQuick(serverConf, "id6", "server")
        assertNull(imported.server)
        assertNotNull(imported.error)
        assertTrue(imported.error!!.contains("رله") || imported.error!!.contains("relay"))
    }

    @Test
    fun clientConfigIsNotTreatedAsServerConfig() {
        val clientConf = """
            [Interface]
            PrivateKey = UALKPz9Q6Fvabt+m4rsA3/jHuD4pu1QsxeiG/XEofrc=
            Address = 10.66.0.2/32

            [Peer]
            PublicKey = C5SkMsovW4QkhUKFjhgdFGMCdMZD6PtRHYlR5Ro2o+0=
            Endpoint = relay.example.com:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
        assertFalse(ConfigImporter.looksLikeServerConfig(clientConf))
        assertNotNull(ConfigImporter.parseWgQuick(clientConf, "id7", "client").server)
    }
}
