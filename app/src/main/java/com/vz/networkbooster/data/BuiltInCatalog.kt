package com.vz.networkbooster.data

import com.vz.networkbooster.data.model.GameProfile
import com.vz.networkbooster.data.model.ProbeProtocol
import com.vz.networkbooster.data.model.ProbeTarget

/**
 * Built-in game catalogue.
 *
 * Package names are the real store identifiers. Probe targets are deliberately
 * *optional* and empty by default for game servers: game backends do not answer
 * unsolicited probes, and inventing host names would produce fake numbers.
 * Users (or a future public endpoint catalogue) can add verified targets; the
 * Diagnostics screen also offers neutral reference anchors such as 1.1.1.1:443
 * that measure the *path*, not the game server.
 */
object BuiltInCatalog {

    val referenceAnchors: List<ProbeTarget> = listOf(
        ProbeTarget("Reference anchor (Cloudflare 1.1.1.1:443)", "1.1.1.1", 443, ProbeProtocol.TCP),
        ProbeTarget("Reference anchor (Google 8.8.8.8:443)", "8.8.8.8", 443, ProbeProtocol.TCP),
    )

    val games: List<GameProfile> = listOf(
        GameProfile(
            id = "pubg-mobile-global",
            displayName = "PUBG Mobile Global",
            packages = listOf("com.tencent.ig"),
            regionHint = "global",
            probeTargets = emptyList(),
            useSplitTunnel = true,
            isBuiltIn = true,
            notes = "Primary target for v1. Add one or more probe targets once you know the " +
                "regional game host:port you want to track.",
        ),
        GameProfile(
            id = "cod-mobile",
            displayName = "Call of Duty: Mobile",
            packages = listOf("com.activision.callofduty.shooter"),
            regionHint = "global",
            probeTargets = emptyList(),
            useSplitTunnel = true,
            isBuiltIn = true,
            notes = "Catalogue entry prepared for phase 2; no route logic is game specific.",
        ),
        GameProfile(
            id = "delta-force",
            displayName = "Delta Force",
            packages = listOf("com.tencent.tmgp.dfm", "com.proximabeta.dfm"),
            regionHint = "global",
            probeTargets = emptyList(),
            useSplitTunnel = true,
            isBuiltIn = true,
            notes = "Catalogue entry prepared for phase 2. Package list may change per store build.",
        ),
    )
}
