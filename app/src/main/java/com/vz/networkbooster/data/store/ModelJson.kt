package com.vz.networkbooster.data.store

import com.vz.networkbooster.data.model.GameProfile
import com.vz.networkbooster.data.model.ProbeProtocol
import com.vz.networkbooster.data.model.ProbeTarget
import com.vz.networkbooster.data.model.ServerProfile
import com.vz.networkbooster.data.model.ServerHealth
import com.vz.networkbooster.data.model.ServerStatus
import org.json.JSONArray
import org.json.JSONObject

/** Explicit JSON mapping (no reflection, no codegen) for on-device storage. */
object ModelJson {

    fun gameToJson(game: GameProfile): JSONObject = JSONObject().apply {
        put("id", game.id)
        put("displayName", game.displayName)
        put("packages", JSONArray(game.packages))
        put("regionHint", game.regionHint)
        put("preferredServerId", game.preferredServerId ?: JSONObject.NULL)
        put("useSplitTunnel", game.useSplitTunnel)
        put("notes", game.notes)
        put("isBuiltIn", game.isBuiltIn)
        put("probeTargets", JSONArray().apply {
            game.probeTargets.forEach { t ->
                put(JSONObject().apply {
                    put("label", t.label)
                    put("host", t.host)
                    put("port", t.port)
                    put("protocol", t.protocol.name)
                })
            }
        })
    }

    fun gameFromJson(json: JSONObject): GameProfile {
        val targets = mutableListOf<ProbeTarget>()
        val arr = json.optJSONArray("probeTargets") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            targets += ProbeTarget(
                label = o.optString("label", "target"),
                host = o.optString("host", ""),
                port = o.optInt("port", 443),
                protocol = runCatching { ProbeProtocol.valueOf(o.optString("protocol", "TCP")) }
                    .getOrDefault(ProbeProtocol.TCP),
            )
        }
        val packages = mutableListOf<String>()
        val pkgArr = json.optJSONArray("packages") ?: JSONArray()
        for (i in 0 until pkgArr.length()) packages += pkgArr.optString(i)
        return GameProfile(
            id = json.optString("id"),
            displayName = json.optString("displayName", "Game"),
            packages = packages,
            regionHint = json.optString("regionHint", ""),
            probeTargets = targets,
            preferredServerId = json.optString("preferredServerId").takeIf { it.isNotBlank() },
            useSplitTunnel = json.optBoolean("useSplitTunnel", true),
            notes = json.optString("notes", ""),
            isBuiltIn = json.optBoolean("isBuiltIn", false),
        )
    }

    fun serverToJson(server: ServerProfile): JSONObject = JSONObject().apply {
        put("id", server.id)
        put("name", server.name)
        put("region", server.region)
        put("endpointHost", server.endpointHost)
        put("endpointPort", server.endpointPort)
        put("serverPublicKey", server.serverPublicKey)
        put("presharedKey", server.presharedKey ?: JSONObject.NULL)
        put("address", server.address)
        put("allowedIps", server.allowedIps)
        put("dns", server.dns)
        put("mtu", server.mtu)
        put("protocol", server.protocol)
        put("capacity", server.capacity)
        put("isDedicated", server.isDedicated)
        put("probeHost", server.probeHost ?: JSONObject.NULL)
        put("probePort", if (server.probePort == null) JSONObject.NULL else server.probePort)
        put("enabled", server.enabled)
        put("notes", server.notes)
        put("isBuiltIn", server.isBuiltIn)
    }

    fun serverFromJson(json: JSONObject): ServerProfile = ServerProfile(
        id = json.optString("id"),
        name = json.optString("name", "Server"),
        region = json.optString("region", ""),
        endpointHost = json.optString("endpointHost", ""),
        endpointPort = json.optInt("endpointPort", 51820),
        serverPublicKey = json.optString("serverPublicKey", ""),
        presharedKey = json.optString("presharedKey").takeIf { it.isNotBlank() },
        address = json.optString("address", ""),
        allowedIps = json.optString("allowedIps", "0.0.0.0/0, ::/0"),
        dns = json.optString("dns", "1.1.1.1"),
        mtu = json.optInt("mtu", 1420),
        protocol = json.optString("protocol", "wireguard"),
        capacity = json.optInt("capacity", 0),
        isDedicated = json.optBoolean("isDedicated", false),
        probeHost = json.optString("probeHost").takeIf { it.isNotBlank() },
        probePort = if (json.isNull("probePort")) null else json.optInt("probePort").takeIf { it > 0 },
        enabled = json.optBoolean("enabled", true),
        notes = json.optString("notes", ""),
        isBuiltIn = json.optBoolean("isBuiltIn", false),
    )

    fun statusToJson(status: ServerStatus): JSONObject = JSONObject().apply {
        put("serverId", status.serverId)
        put("health", status.health.name)
        put("lastCheckEpochMs", status.lastCheckEpochMs)
        put("detail", status.detail)
    }

    fun statusFromJson(json: JSONObject): ServerStatus = ServerStatus(
        serverId = json.optString("serverId"),
        health = runCatching { ServerHealth.valueOf(json.optString("health", "UNKNOWN")) }
            .getOrDefault(ServerHealth.UNKNOWN),
        lastCheckEpochMs = json.optLong("lastCheckEpochMs", 0L),
        detail = json.optString("detail", ""),
    )
}
