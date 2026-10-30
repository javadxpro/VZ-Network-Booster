package com.vz.networkbooster.data.store

import com.vz.networkbooster.data.model.ServerProfile
import com.vz.networkbooster.data.model.ServerStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ServerRepository(filesDir: File) {

    private val store = JsonFileStore(File(filesDir, "servers.json"))
    private val statusStore = JsonFileStore(File(filesDir, "server_status.json"))
    private val mutex = Mutex()

    /**
     * No public relay is bundled on purpose: a WireGuard relay only works when
     * the operator has registered this device's public key, and shipping a
     * random "public" endpoint would be a fake feature. The list starts empty
     * and is filled by the user (bundle import, wg config import, or manual).
     */
    suspend fun load(): List<ServerProfile> = mutex.withLock {
        val json = store.read() ?: return@withLock emptyList()
        val arr = json.optJSONArray("servers") ?: JSONArray()
        val result = mutableListOf<ServerProfile>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { result += ModelJson.serverFromJson(it) }
        }
        result
    }

    suspend fun save(servers: List<ServerProfile>) = mutex.withLock {
        val arr = JSONArray()
        servers.forEach { arr.put(ModelJson.serverToJson(it)) }
        store.write(JSONObject().put("version", 1).put("servers", arr))
    }

    suspend fun loadStatuses(): Map<String, ServerStatus> = mutex.withLock {
        val json = statusStore.read() ?: return@withLock emptyMap()
        val arr = json.optJSONArray("statuses") ?: JSONArray()
        val map = mutableMapOf<String, ServerStatus>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val s = ModelJson.statusFromJson(o)
            map[s.serverId] = s
        }
        map
    }

    suspend fun saveStatuses(statuses: Map<String, ServerStatus>) = mutex.withLock {
        val arr = JSONArray()
        statuses.values.forEach { arr.put(ModelJson.statusToJson(it)) }
        statusStore.write(JSONObject().put("version", 1).put("statuses", arr))
    }

    fun newId(): String = UUID.randomUUID().toString()
}
