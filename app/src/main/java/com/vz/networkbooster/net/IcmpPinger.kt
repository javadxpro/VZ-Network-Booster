package com.vz.networkbooster.net

import com.vz.networkbooster.root.RootShell

/** Abstraction so the quality tester stays testable without a rooted device. */
interface IcmpPinger {
    suspend fun ping(host: String, count: Int, timeoutSeconds: Int): IcmpPingParser.Result?
}

/** Runs `/system/bin/ping` through `su`. No host string ever reaches the shell unchecked. */
class RootIcmpPinger(private val rootShell: RootShell = RootShell) : IcmpPinger {

    private val safeHost = Regex("^[A-Za-z0-9._:-]{1,253}$")

    override suspend fun ping(host: String, count: Int, timeoutSeconds: Int): IcmpPingParser.Result? {
        if (!safeHost.matches(host.trim())) return null
        val status = rootShell.status()
        if (!status.available) return null
        val safeCount = count.coerceIn(1, 60)
        val safeTimeout = timeoutSeconds.coerceIn(1, 10)
        // -i 0.2 is allowed for root only; without it 10 probes take 10s.
        val command = "ping -c $safeCount -W $safeTimeout -i 0.2 ${host.trim()}"
        val result = rootShell.exec(command, timeoutMs = (safeCount * 250L) + (safeTimeout * 1_000L) + 5_000L)
        return IcmpPingParser.parse(result.output)
    }
}
