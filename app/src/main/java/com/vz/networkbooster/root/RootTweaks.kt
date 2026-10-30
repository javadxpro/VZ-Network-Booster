package com.vz.networkbooster.root

import android.content.Context

/**
 * Root-only system adjustments that have a *real*, explainable effect.
 *
 * Everything here is opt-in, reported step by step, and reverted on disconnect
 * (`revert()`), with the original values kept in private SharedPreferences.
 *
 * Honest scope:
 *  - Wi-Fi power save off: removes a well-known source of periodic latency
 *    spikes/jitter on Wi-Fi. Real effect, no magic.
 *  - Doze whitelist: keeps the tunnel service alive in the background. Real.
 *  - TCP congestion control (BBR when the kernel offers it): improves throughput
 *    on lossy links; it does not lower RTT by itself.
 *  - drop_caches: frees page cache once. Marginal on modern devices, useful when
 *    the game process gets killed on low-RAM phones.
 *
 * Deliberately NOT done: no CPU governor changes, no killing of user apps
 * without an explicit choice, no firewall/routing modifications.
 */
class RootTweaks(
    context: Context,
    private val shell: RootShell = RootShell,
) {

    data class Config(
        val wifiPowerSaveOff: Boolean = true,
        val dozeWhitelist: Boolean = true,
        val tcpBbr: Boolean = false,
        val dropCaches: Boolean = false,
    )

    data class Step(val title: String, val ok: Boolean, val detail: String)

    private val prefs = context.getSharedPreferences("vz_root_state", Context.MODE_PRIVATE)
    private val packageName = context.packageName

    suspend fun apply(config: Config): List<Step> {
        val steps = mutableListOf<Step>()
        if (config.wifiPowerSaveOff) steps += disableWifiPowerSave()
        if (config.dozeWhitelist) steps += whitelistDoze()
        if (config.tcpBbr) steps += enableBbr()
        if (config.dropCaches) steps += dropCaches()
        return steps
    }

    suspend fun revert(): List<Step> {
        val steps = mutableListOf<Step>()

        if (prefs.getBoolean(KEY_WIFI_APPLIED, false)) {
            val iface = prefs.getString(KEY_WIFI_IFACE, null) ?: detectWifiInterface()
            val result = if (iface != null) {
                shell.exec("iw dev $iface set power_save on", timeoutMs = 5_000)
            } else {
                shell.exec("settings put global wifi_sleep_policy 0", timeoutMs = 5_000)
            }
            prefs.edit().putBoolean(KEY_WIFI_APPLIED, false).apply()
            steps += Step("restore Wi-Fi power save", result.ok, iface ?: "wifi_sleep_policy reset")
        }

        if (prefs.getBoolean(KEY_DOZE_APPLIED, false)) {
            val result = shell.exec("cmd deviceidle whitelist -$packageName", timeoutMs = 5_000)
            prefs.edit().putBoolean(KEY_DOZE_APPLIED, false).apply()
            steps += Step("remove Doze whitelist", result.ok, result.output.trim())
        }

        val originalCc = prefs.getString(KEY_ORIGINAL_CC, null)
        if (!originalCc.isNullOrBlank() && originalCc.matches(Regex("^[a-z0-9_]+$"))) {
            val result = shell.exec(
                "echo $originalCc > /proc/sys/net/ipv4/tcp_congestion_control",
                timeoutMs = 5_000,
            )
            prefs.edit().remove(KEY_ORIGINAL_CC).apply()
            steps += Step("restore TCP congestion control", result.ok, originalCc)
        }

        return steps
    }

    suspend fun dropCachesNow(): Step = dropCaches()

    private suspend fun disableWifiPowerSave(): Step {
        if (prefs.getBoolean(KEY_WIFI_APPLIED, false)) {
            return Step("Wi-Fi power save", true, "already disabled in this session")
        }
        val iface = detectWifiInterface()
            ?: return Step("Wi-Fi power save", false, "no wlan interface found (mobile data in use?)")
        val result = shell.exec("iw dev $iface set power_save off", timeoutMs = 5_000)
        if (result.ok) {
            prefs.edit().putBoolean(KEY_WIFI_APPLIED, true).putString(KEY_WIFI_IFACE, iface).apply()
            return Step("Wi-Fi power save off", true, "$iface via iw")
        }
        // `iw` is missing on some Android builds; fall back to the framework setting.
        val fallback = shell.exec("settings put global wifi_sleep_policy 2", timeoutMs = 5_000)
        if (fallback.ok) {
            prefs.edit().putBoolean(KEY_WIFI_APPLIED, true).apply()
            return Step("Wi-Fi power save off", true, "iw unavailable ($iface); used wifi_sleep_policy=2")
        }
        return Step("Wi-Fi power save off", false, result.output.trim().ifBlank { "iw rejected the change" })
    }

    private suspend fun whitelistDoze(): Step {
        val result = shell.exec("cmd deviceidle whitelist +$packageName", timeoutMs = 5_000)
        if (result.ok) {
            prefs.edit().putBoolean(KEY_DOZE_APPLIED, true).apply()
            return Step("Doze whitelist", true, "battery optimisation ignored for $packageName")
        }
        val legacy = shell.exec("dumpsys deviceidle whitelist +$packageName", timeoutMs = 5_000)
        if (legacy.ok) {
            prefs.edit().putBoolean(KEY_DOZE_APPLIED, true).apply()
            return Step("Doze whitelist", true, "added via dumpsys")
        }
        return Step("Doze whitelist", false, result.output.trim().ifBlank { "device policy rejected it" })
    }

    private suspend fun enableBbr(): Step {
        val available = shell.exec("cat /proc/sys/net/ipv4/tcp_available_congestion_control", timeoutMs = 5_000)
        if (!available.output.contains("bbr")) {
            return Step("TCP congestion control", false, "kernel does not offer bbr (has: ${available.output.trim()})")
        }
        val current = shell.exec("cat /proc/sys/net/ipv4/tcp_congestion_control", timeoutMs = 5_000)
            .output.trim()
        if (current.matches(Regex("^[a-z0-9_]+$")) && !prefs.contains(KEY_ORIGINAL_CC)) {
            prefs.edit().putString(KEY_ORIGINAL_CC, current).apply()
        }
        if (current == "bbr") return Step("TCP congestion control", true, "already bbr")
        val result = shell.exec("echo bbr > /proc/sys/net/ipv4/tcp_congestion_control", timeoutMs = 5_000)
        return Step(
            "TCP congestion control",
            result.ok,
            if (result.ok) "bbr enabled (was $current)" else result.output.trim(),
        )
    }

    private suspend fun dropCaches(): Step {
        val result = shell.exec("sync; echo 3 > /proc/sys/vm/drop_caches", timeoutMs = 8_000)
        return Step(
            "drop caches",
            result.ok,
            if (result.ok) "page cache dropped once (Android will re-fill it)" else result.output.trim(),
        )
    }

    private suspend fun detectWifiInterface(): String? {
        val result = shell.exec(
            "ip -o link show | grep -o 'wlan[0-9]\\{1,2\\}' | head -1",
            timeoutMs = 5_000,
        )
        val name = result.output.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        return name.takeIf { it.matches(Regex("^wlan[0-9]{1,2}$")) }
    }

    private companion object {
        const val KEY_WIFI_APPLIED = "wifi_applied"
        const val KEY_WIFI_IFACE = "wifi_iface"
        const val KEY_DOZE_APPLIED = "doze_applied"
        const val KEY_ORIGINAL_CC = "original_tcp_cc"
    }
}
