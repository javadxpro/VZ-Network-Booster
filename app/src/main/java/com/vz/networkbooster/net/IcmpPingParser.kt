package com.vz.networkbooster.net

/**
 * Parses the output of Android's `/system/bin/ping` (BusyBox/toybox variants
 * included). Pure Kotlin so it is unit tested on the JVM with real outputs.
 *
 * Root is required to run `ping` on Android: the binary is not accessible to
 * unprivileged app domains, which is exactly why ICMP probing is a root-only
 * feature of this app.
 */
object IcmpPingParser {

    data class Result(
        val transmitted: Int,
        val received: Int,
        val rtts: List<Double>,
        val minMs: Double?,
        val avgMs: Double?,
        val maxMs: Double?,
        val mdevMs: Double?,
    ) {
        val lossPercent: Double
            get() = if (transmitted <= 0) 0.0 else
                ((transmitted - received).coerceAtLeast(0) * 100.0) / transmitted
    }

    private val perPacketTime = Regex("""time[=<]\s*([0-9]+(?:\.[0-9]+)?)\s*ms""")
    private val summary = Regex("""(\d+)\s+packets transmitted,\s+(\d+)\s+(?:packets )?received""")
    private val rttLine = Regex(
        """(?:rtt|round-trip)\s+min/avg/max/(?:mdev|stddev)\s*=\s*([0-9.]+)/([0-9.]+)/([0-9.]+)/([0-9.]+)"""
    )

    fun parse(output: String): Result? {
        val reported = output.contains("packets transmitted")
        if (!reported && !perPacketTime.containsMatchIn(output)) return null

        val rtts = perPacketTime.findAll(output)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .toList()

        val summaryMatch = summary.find(output)
        val transmitted = summaryMatch?.groupValues?.get(1)?.toIntOrNull() ?: rtts.size
        val received = summaryMatch?.groupValues?.get(2)?.toIntOrNull() ?: rtts.size

        val rttMatch = rttLine.find(output)
        val minMs = rttMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: rtts.minOrNull()
        val avgMs = rttMatch?.groupValues?.get(2)?.toDoubleOrNull() ?: QualityStats.average(rtts)
        val maxMs = rttMatch?.groupValues?.get(3)?.toDoubleOrNull() ?: rtts.maxOrNull()
        val mdevMs = rttMatch?.groupValues?.get(4)?.toDoubleOrNull()

        return Result(
            transmitted = transmitted,
            received = received,
            rtts = rtts,
            minMs = minMs,
            avgMs = avgMs,
            maxMs = maxMs,
            mdevMs = mdevMs,
        )
    }
}
