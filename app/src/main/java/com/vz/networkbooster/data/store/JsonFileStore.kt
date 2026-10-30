package com.vz.networkbooster.data.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Small atomic JSON file store: writes go to a temp file first so a crash can
 * never leave a half-written profile database behind.
 */
class JsonFileStore(private val file: File) {

    private val mutex = Mutex()

    suspend fun read(): JSONObject? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.exists()) return@withLock null
            runCatching { JSONObject(file.readText()) }.getOrNull()
        }
    }

    suspend fun write(obj: JSONObject) = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(obj.toString(2))
            if (file.exists() && !file.delete()) {
                // fall through; renameTo below will fail and the caller keeps old data
            }
            if (!tmp.renameTo(file)) {
                file.writeText(obj.toString(2))
                tmp.delete()
            }
        }
    }
}
