package com.vz.networkbooster.data.importx

import com.vz.networkbooster.data.model.ServerProfile
import org.json.JSONObject

/**
 * Imports relay definitions from the two formats that matter in practice:
 *
 * 1. VZ server bundle (JSON) — endpoint + relay public key + our assigned
 *    address. The device private key is generated locally, so a bundle contains
 *    no secret and can be shared/checked into a private ops repo.
 * 2. wg-quick config (INI) — a full WireGuard config that already contains a
 *    private key. It is imported too, but the key is kept encrypted on-device
 *    and never written to the project or logs.
 */
object ConfigImporter {

    data class Imported(
        val server: ServerProfile?,
        val privateKey: String? = null,
        val warnings: List<String> = emptyList(),
        val error: String? = null,
    )

    fun parseBundleJson(text: String, id: String, fallbackName: String): Imported {
        val json = runCatching { JSONObject(text) }.getOrElse {
            return Imported(null, error = "not valid JSON: ${it.message}")
        }
        val endpoint = json.optString("endpoint")
        val host: String
        val port: Int
        if (endpoint.contains(":")) {
            host = endpoint.substringBeforeLast(":").trim().removeSurrounding("[", "]")
            port = endpoint.substringAfterLast(":").toIntOrNull() ?: 51820
        } else {
            host = endpoint.trim()
            port = json.optInt("port", 51820)
        }
        val publicKey = json.optString("serverPublicKey", json.optString("publicKey"))
        val address = json.optString("address")
        val warnings = mutableListOf<String>()
        if (json.has("privateKey")) {
            warnings += "bundle contains a private key; it was ignored (bundles must stay secret-free)"
        }
        val server = ServerProfile(
            id = id,
            name = json.optString("name", fallbackName),
            region = json.optString("region", ""),
            endpointHost = host,
            endpointPort = port,
            serverPublicKey = publicKey,
            presharedKey = json.optString("presharedKey").takeIf { it.isNotBlank() },
            address = address,
            allowedIps = json.optString("allowedIps", "0.0.0.0/0, ::/0"),
            dns = json.optString("dns", "1.1.1.1"),
            mtu = json.optInt("mtu", 1420),
            protocol = json.optString("protocol", "wireguard"),
            capacity = json.optInt("capacity", 0),
            isDedicated = json.optBoolean("dedicated", false) || json.optBoolean("isDedicated", false),
            probeHost = json.optString("probeHost").takeIf { it.isNotBlank() },
            probePort = json.optInt("probePort", 0).takeIf { it > 0 },
            notes = json.optString("notes", ""),
        )
        if (!server.isConfigured) {
            return Imported(null, warnings = warnings, error = "bundle needs endpoint, serverPublicKey and address")
        }
        return Imported(server, warnings = warnings)
    }

    fun parseWgQuick(text: String, id: String, fallbackName: String): Imported {
        if (!text.contains("[Interface]", ignoreCase = true) || !text.contains("[Peer]", ignoreCase = true)) {
            return Imported(null, error = "not a wg-quick config (missing [Interface]/[Peer])")
        }
        var section = ""
        var privateKey: String? = null
        var address = ""
        var dns = "1.1.1.1"
        var mtu = 1420
        var publicKey = ""
        var presharedKey: String? = null
        var allowedIps = "0.0.0.0/0, ::/0"
        var endpoint = ""
        val warnings = mutableListOf<String>()

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("[")) {
                section = line.lowercase()
                return@forEach
            }
            val key = line.substringBefore('=').trim().lowercase()
            val value = line.substringAfter('=', "").trim()
            if (value.isEmpty()) return@forEach
            when {
                section == "[interface]" && key == "privatekey" -> privateKey = value
                section == "[interface]" && key == "address" -> address = value
                section == "[interface]" && key == "dns" -> dns = value
                section == "[interface]" && key == "mtu" -> mtu = value.toIntOrNull() ?: 1420
                section == "[peer]" && key == "publickey" -> publicKey = value
                section == "[peer]" && key == "presharedkey" -> presharedKey = value
                section == "[peer]" && key == "allowedips" -> allowedIps = value
                section == "[peer]" && key == "endpoint" -> endpoint = value
            }
        }

        val host = endpoint.substringBeforeLast(":", "").trim().removeSurrounding("[", "]")
        val port = endpoint.substringAfterLast(":", "").toIntOrNull() ?: 51820
        if (host.isBlank() || publicKey.isBlank() || address.isBlank()) {
            return Imported(null, error = "config needs Endpoint, PublicKey and Address")
        }
        if (privateKey == null) warnings += "no PrivateKey in config; the app's device key will be used"

        val server = ServerProfile(
            id = id,
            name = fallbackName,
            region = "",
            endpointHost = host,
            endpointPort = port,
            serverPublicKey = publicKey,
            presharedKey = presharedKey,
            address = address,
            allowedIps = allowedIps,
            dns = dns,
            mtu = mtu,
            notes = "imported from wg-quick config",
        )
        return Imported(server, privateKey = privateKey, warnings = warnings)
    }
}
