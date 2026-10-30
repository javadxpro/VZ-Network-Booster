package com.vz.networkbooster.net

import com.vz.networkbooster.data.model.ProbeProtocol
import com.vz.networkbooster.data.model.ProbeTarget
import com.vz.networkbooster.data.model.QualityReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Real, on-device path measurement.
 *
 * TCP mode: full TCP handshake RTT (SYN → SYN/ACK). Works on any reachable
 * host:port without root, which is why it is the default.
 *
 * UDP mode: sends a 32-byte datagram and waits for a reply. Most game servers
 * do not answer unsolicited datagrams; when no reply arrives the report says so
 * instead of inventing jitter numbers.
 *
 * Loss is the share of attempts that produced no RTT; jitter is the mean
 * absolute difference between consecutive RTTs.
 */
class QualityTester(
    private val interProbeDelayMs: Long = 120L,
    private val icmpPinger: IcmpPinger? = null,
) {

    suspend fun measure(
        target: ProbeTarget,
        samples: Int = 10,
        timeoutMs: Int = 1_500,
        path: String = "direct",
    ): QualityReport = withContext(Dispatchers.IO) {
        val attempts = samples.coerceIn(1, 60)
        val rtts = ArrayList<Double>(attempts)
        var lastError: String? = null

        if (target.protocol == ProbeProtocol.ICMP) {
            return@withContext icmpMeasure(target, attempts, timeoutMs, path)
        }

        val address: InetAddress? = runCatching { InetAddress.getByName(target.host) }
            .onFailure { lastError = "DNS: ${it.message ?: it.javaClass.simpleName}" }
            .getOrNull()

        if (address == null) {
            return@withContext QualityReport(
                targetLabel = target.label,
                host = target.host,
                port = target.port,
                protocol = target.protocol,
                path = path,
                attempted = attempts,
                received = 0,
                minMs = null,
                avgMs = null,
                maxMs = null,
                jitterMs = null,
                lossPercent = 100.0,
                timestampMs = System.currentTimeMillis(),
                note = lastError ?: "unresolvable host",
            )
        }

        repeat(attempts) {
            val rtt = when (target.protocol) {
                ProbeProtocol.TCP -> tcpHandshakeRtt(address, target.port, timeoutMs)
                ProbeProtocol.UDP -> udpRoundTrip(address, target.port, timeoutMs)
            }
            if (rtt != null) rtts += rtt
            if (interProbeDelayMs > 0) delay(interProbeDelayMs)
        }

        val sent = attempts
        val received = rtts.size
        val note = when {
            target.protocol == ProbeProtocol.UDP && received == 0 ->
                "no UDP reply (normal for game servers); use TCP mode or a UDP echo target"
            received == 0 -> "no TCP handshake completed — host/port unreachable or filtered"
            received < sent -> "partial loss observed during the run"
            else -> ""
        }

        QualityReport(
            targetLabel = target.label,
            host = target.host,
            port = target.port,
            protocol = target.protocol,
            path = path,
            attempted = sent,
            received = received,
            minMs = rtts.minOrNull()?.let(QualityStats::round1),
            avgMs = QualityStats.average(rtts)?.let(QualityStats::round1),
            maxMs = rtts.maxOrNull()?.let(QualityStats::round1),
            jitterMs = QualityStats.jitterMs(rtts)?.let(QualityStats::round1),
            lossPercent = QualityStats.round1(QualityStats.lossPercent(sent, received)),
            timestampMs = System.currentTimeMillis(),
            note = note,
        )
    }

    private suspend fun icmpMeasure(
        target: ProbeTarget,
        attempts: Int,
        timeoutMs: Int,
        path: String,
    ): QualityReport {
        val pinger = icmpPinger
            ?: return QualityReport(
                targetLabel = target.label,
                host = target.host,
                port = target.port,
                protocol = ProbeProtocol.ICMP,
                path = path,
                attempted = attempts,
                received = 0,
                minMs = null,
                avgMs = null,
                maxMs = null,
                jitterMs = null,
                lossPercent = 100.0,
                timestampMs = System.currentTimeMillis(),
                note = "ICMP requires root: Android blocks the ping binary for normal apps",
            )
        val timeoutSeconds = (timeoutMs / 1_000).coerceIn(1, 10)
        val result = pinger.ping(target.host, attempts, timeoutSeconds)
            ?: return QualityReport(
                targetLabel = target.label,
                host = target.host,
                port = target.port,
                protocol = ProbeProtocol.ICMP,
                path = path,
                attempted = attempts,
                received = 0,
                minMs = null,
                avgMs = null,
                maxMs = null,
                jitterMs = null,
                lossPercent = 100.0,
                timestampMs = System.currentTimeMillis(),
                note = "ICMP probe failed: no root shell or host unreachable",
            )
        return QualityReport(
            targetLabel = target.label,
            host = target.host,
            port = target.port,
            protocol = ProbeProtocol.ICMP,
            path = path,
            attempted = result.transmitted,
            received = result.received,
            minMs = result.minMs?.let(QualityStats::round1),
            avgMs = result.avgMs?.let(QualityStats::round1),
            maxMs = result.maxMs?.let(QualityStats::round1),
            jitterMs = QualityStats.jitterMs(result.rtts)?.let(QualityStats::round1),
            lossPercent = QualityStats.round1(result.lossPercent),
            timestampMs = System.currentTimeMillis(),
            note = "ICMP via root" + (result.mdevMs?.let { ", mdev %.1f ms".format(it) } ?: ""),
        )
    }

    private fun tcpHandshakeRtt(address: InetAddress, port: Int, timeoutMs: Int): Double? = runCatching {
        Socket().use { socket ->
            socket.tcpNoDelay = true
            val started = System.nanoTime()
            socket.connect(InetSocketAddress(address, port), timeoutMs)
            (System.nanoTime() - started) / 1_000_000.0
        }
    }.getOrNull()

    private fun udpRoundTrip(address: InetAddress, port: Int, timeoutMs: Int): Double? = runCatching {
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            socket.connect(address, port)
            val payload = ByteArray(32) { 0x56 }
            val started = System.nanoTime()
            socket.send(DatagramPacket(payload, payload.size))
            val reply = DatagramPacket(ByteArray(64), 64)
            socket.receive(reply)
            (System.nanoTime() - started) / 1_000_000.0
        }
    }.getOrNull()
}
