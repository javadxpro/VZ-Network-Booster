package com.vz.networkbooster.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Minimal, allow-listed root shell.
 *
 * Security rules used everywhere in this app:
 *  - callers pass complete command strings built only from constants and
 *    validated values (package names, IPv4/IPv6 literals, interface names);
 *  - nothing user-typed is interpolated without validation;
 *  - output is never logged with secrets, and the WireGuard private key is never
 *    passed through this shell.
 */
object RootShell {

    private const val TAG = "VzRootShell"

    data class ExecResult(val exitCode: Int, val output: String) {
        val ok: Boolean get() = exitCode == 0
    }

    data class Status(val available: Boolean, val detail: String)

    @Volatile
    private var cachedStatus: Status? = null

    suspend fun status(force: Boolean = false): Status {
        cachedStatus?.let { if (!force) return it }
        val result = exec("id", timeoutMs = 6_000)
        val status = if (result.ok && result.output.contains("uid=0")) {
            val manager = exec("which su", timeoutMs = 3_000).output.trim()
            Status(true, "root available" + if (manager.isNotBlank()) " ($manager)" else "")
        } else {
            Status(false, "su not granted" + if (result.output.isNotBlank()) ": ${result.output.take(120)}" else "")
        }
        cachedStatus = status
        return status
    }

    suspend fun exec(command: String, timeoutMs: Long = 8_000): ExecResult = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) process.destroyForcibly()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val code = if (finished) process.exitValue() else -1
            if (code != 0) Log.w(TAG, "root command failed ($code): ${command.take(40)}")
            ExecResult(code, if (finished) output else output + "\n[timeout after ${timeoutMs}ms]")
        } catch (t: Throwable) {
            Log.w(TAG, "root exec failed", t)
            ExecResult(-2, t.message ?: t.javaClass.simpleName)
        }
    }
}
