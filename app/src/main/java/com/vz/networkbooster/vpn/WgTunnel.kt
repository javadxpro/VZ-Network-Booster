package com.vz.networkbooster.vpn

import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config

/**
 * Tunnel handle handed to the WireGuard backend.
 *
 * The upstream `Tunnel` interface is minimal (`getName`, `onStateChange`); the
 * configuration is passed to the backend separately and the backend calls
 * [onStateChange] whenever the tunnel goes up or down.
 */
class WgTunnel(
    private val tunnelName: String,
    val wgConfig: Config,
) : Tunnel {

    @Volatile
    var lastState: Tunnel.State = Tunnel.State.DOWN
        private set

    override fun getName(): String = tunnelName

    override fun onStateChange(newState: Tunnel.State) {
        lastState = newState
    }

    companion object {
        /** WireGuard interface names must match [a-zA-Z0-9_=+.-]{1,15}. */
        fun sanitizeName(raw: String): String {
            val cleaned = raw.replace(Regex("[^a-zA-Z0-9_=+.-]"), "")
            return (if (cleaned.isEmpty()) "vz-wg" else cleaned).take(15)
        }
    }
}
