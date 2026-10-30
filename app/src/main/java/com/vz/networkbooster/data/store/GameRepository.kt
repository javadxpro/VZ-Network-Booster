package com.vz.networkbooster.data.store

import com.vz.networkbooster.data.BuiltInCatalog
import com.vz.networkbooster.data.model.GameProfile
import com.vz.networkbooster.data.model.ProbeProtocol
import com.vz.networkbooster.data.model.ProbeTarget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Persists game profiles. Built-in entries are merged on first run and can be
 * edited or removed by the user afterwards (multi-game architecture: adding a
 * game is data, not code).
 */
class GameRepository(filesDir: File) {

    private val store = JsonFileStore(File(filesDir, "games.json"))
    private val mutex = Mutex()

    suspend fun load(): List<GameProfile> = mutex.withLock {
        val json = store.read()
        if (json == null) {
            val seeded = BuiltInCatalog.games
            persist(seeded)
            return@withLock seeded
        }
        val arr = json.optJSONArray("games") ?: JSONArray()
        val result = mutableListOf<GameProfile>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { result += ModelJson.gameFromJson(it) }
        }
        result
    }

    suspend fun save(games: List<GameProfile>) = mutex.withLock { persist(games) }

    private suspend fun persist(games: List<GameProfile>) {
        val arr = JSONArray()
        games.forEach { arr.put(ModelJson.gameToJson(it)) }
        store.write(JSONObject().put("version", 1).put("games", arr))
    }

    fun newId(): String = UUID.randomUUID().toString()

    /** Adds a probe target to a profile, returning an updated immutable copy. */
    fun withProbeTarget(game: GameProfile, label: String, host: String, port: Int, protocol: String): GameProfile {
        val proto = runCatching { ProbeProtocol.valueOf(protocol.uppercase()) }.getOrDefault(ProbeProtocol.TCP)
        return game.copy(probeTargets = game.probeTargets + ProbeTarget(label, host, port, proto))
    }
}
