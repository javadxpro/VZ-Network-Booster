package com.vz.networkbooster.vpn

import com.vz.networkbooster.data.model.ServerProfile
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

/**
 * Turns a [ServerProfile] + the on-device private key into a wg-quick config
 * that the official WireGuard backend can parse and bring up.
 *
 * Split tunnelling is expressed with `IncludedApplications`/`ExcludedApplications`,
 * which `com.wireguard.config.Interface` supports natively and the backend maps
 * onto `VpnService.Builder.addAllowedApplication`/`addDisallowedApplication`.
 *
 * The produced text is validated by the WireGuard parser itself before use, so a
 * typo in a key, address or endpoint fails loudly instead of silently producing
 * a dead tunnel.
 */
object WireGuardConfigBuilder {

    /** WireGuard keys are 32 bytes encoded as 44 base64 characters. */
    private val WIREGUARD_KEY = Regex("^[A-Za-z0-9+/]{42}[AEIMQUYcgkosw480]=$")

    data class BuildResult(
        val configText: String,
        val valid: Boolean,
        val error: String?,
    )

    fun isValidKey(key: String): Boolean = WIREGUARD_KEY.matches(key.trim())

    fun build(
        server: ServerProfile,
        privateKeyBase64: String,
        includePackages: Set<String> = emptySet(),
        excludePackages: Set<String> = emptySet(),
    ): BuildResult {
        val problems = mutableListOf<String>()
        if (!isValidKey(privateKeyBase64)) problems += "device private key is not a valid WireGuard key"
        if (!isValidKey(server.serverPublicKey)) problems += "server public key is not a valid WireGuard key"
        if (server.presharedKey != null && !isValidKey(server.presharedKey)) {
            problems += "preshared key is not a valid WireGuard key"
        }
        if (server.endpointHost.isBlank()) problems += "endpoint host is empty"
        if (server.endpointPort !in 1..65535) problems += "endpoint port is out of range"
        if (server.address.isBlank()) problems += "interface address is empty"
        if (server.mtu !in 576..9000) problems += "MTU must be between 576 and 9000"
        if (problems.isNotEmpty()) {
            return BuildResult("", false, problems.joinToString("; "))
        }

        val text = buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = ${privateKeyBase64.trim()}")
            appendLine("Address = ${server.address.trim()}")
            if (server.dns.isNotBlank()) appendLine("DNS = ${server.dns.trim()}")
            appendLine("MTU = ${server.mtu}")
            if (includePackages.isNotEmpty()) {
                appendLine("IncludedApplications = ${includePackages.joinToString(", ")}")
            }
            if (excludePackages.isNotEmpty()) {
                appendLine("ExcludedApplications = ${excludePackages.joinToString(", ")}")
            }
            appendLine("[Peer]")
            appendLine("PublicKey = ${server.serverPublicKey.trim()}")
            server.presharedKey?.takeIf { it.isNotBlank() }?.let { appendLine("PresharedKey = ${it.trim()}") }
            appendLine("AllowedIPs = ${server.allowedIps.trim()}")
            appendLine("Endpoint = ${server.endpoint}")
            appendLine("PersistentKeepalive = 25")
        }

        val parsed = runCatching { Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))) }
        return parsed.fold(
            onSuccess = { BuildResult(text, true, null) },
            onFailure = { BuildResult(text, false, it.message ?: it.javaClass.simpleName) },
        )
    }
}
