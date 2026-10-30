package com.vz.networkbooster.data.store

import android.content.Context
import androidx.core.content.edit

/** Non-secret preferences only. Secrets live in [com.vz.networkbooster.security.SecretBox]. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("vz_settings", Context.MODE_PRIVATE)

    var activeGameId: String?
        get() = prefs.getString(KEY_GAME, null)
        set(value) = prefs.edit { putString(KEY_GAME, value) }

    var activeServerId: String?
        get() = prefs.getString(KEY_SERVER, null)
        set(value) = prefs.edit { putString(KEY_SERVER, value) }

    var autoReconnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_RECONNECT, value) }

    var splitTunnelGameOnly: Boolean
        get() = prefs.getBoolean(KEY_SPLIT, true)
        set(value) = prefs.edit { putBoolean(KEY_SPLIT, value) }

    var includeSelfForMeasurement: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_SELF, true)
        set(value) = prefs.edit { putBoolean(KEY_INCLUDE_SELF, value) }

    var probeSamples: Int
        get() = prefs.getInt(KEY_SAMPLES, 10)
        set(value) = prefs.edit { putInt(KEY_SAMPLES, value) }

    // ---------------------------------------------------------------- root mode
    var rootEnabled: Boolean
        get() = prefs.getBoolean(KEY_ROOT_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ROOT_ENABLED, value) }

    var rootWifiPowerSave: Boolean
        get() = prefs.getBoolean(KEY_ROOT_WIFI, true)
        set(value) = prefs.edit { putBoolean(KEY_ROOT_WIFI, value) }

    var rootDozeWhitelist: Boolean
        get() = prefs.getBoolean(KEY_ROOT_DOZE, true)
        set(value) = prefs.edit { putBoolean(KEY_ROOT_DOZE, value) }

    var rootTcpBbr: Boolean
        get() = prefs.getBoolean(KEY_ROOT_TCP, false)
        set(value) = prefs.edit { putBoolean(KEY_ROOT_TCP, value) }

    var rootDropCaches: Boolean
        get() = prefs.getBoolean(KEY_ROOT_CACHES, false)
        set(value) = prefs.edit { putBoolean(KEY_ROOT_CACHES, value) }

    var rootIcmpProbes: Boolean
        get() = prefs.getBoolean(KEY_ROOT_ICMP, true)
        set(value) = prefs.edit { putBoolean(KEY_ROOT_ICMP, value) }

    private companion object {
        const val KEY_GAME = "active_game"
        const val KEY_SERVER = "active_server"
        const val KEY_AUTO_RECONNECT = "auto_reconnect"
        const val KEY_SPLIT = "split_tunnel_game_only"
        const val KEY_INCLUDE_SELF = "include_self"
        const val KEY_SAMPLES = "probe_samples"
        const val KEY_ROOT_ENABLED = "root_enabled"
        const val KEY_ROOT_WIFI = "root_wifi_power_save"
        const val KEY_ROOT_DOZE = "root_doze_whitelist"
        const val KEY_ROOT_TCP = "root_tcp_bbr"
        const val KEY_ROOT_CACHES = "root_drop_caches"
        const val KEY_ROOT_ICMP = "root_icmp"
    }
}
