package com.vz.networkbooster.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vz.networkbooster.MainActivity
import com.vz.networkbooster.R
import com.vz.networkbooster.data.model.TunnelPhase
import com.vz.networkbooster.data.store.SettingsStore
import com.vz.networkbooster.net.AirRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process alive while the tunnel is up,
 * publishes the live status notification and runs the auto-reconnect watchdog.
 *
 * It does not create the tun device itself — the WireGuard library's own
 * VpnService does that. Keeping this service in the foreground is what makes
 * long gaming sessions and reconnects reliable on Android 8+.
 */
class BoosterService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settings by lazy { SettingsStore(this) }
    private var stateJob: Job? = null
    private var watchdogJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        VpnManager.init(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    VpnManager.disconnect()
                    stopSelf()
                }
                return START_NOT_STICKY
            }

            else -> {
                startInForeground(buildNotification("Starting tunnel", "Preparing WireGuard route"))
                startLoops()
            }
        }
        return START_STICKY
    }

    private fun startLoops() {
        if (stateJob == null) {
            stateJob = scope.launch {
                VpnManager.state.collectLatest { state ->
                    val title = when (state.phase) {
                        TunnelPhase.CONNECTED -> "Connected to ${state.serverName ?: "relay"}"
                        TunnelPhase.CONNECTING -> "Connecting…"
                        TunnelPhase.RECONNECTING -> "Reconnecting…"
                        TunnelPhase.ERROR -> "Connection error"
                        TunnelPhase.PREPARING -> "Preparing…"
                        TunnelPhase.IDLE -> "Disconnected"
                    }
                    val traffic = if (state.rxBytes + state.txBytes > 0) {
                        " · ↓${formatBytes(state.rxBytes)} ↑${formatBytes(state.txBytes)}"
                    } else {
                        ""
                    }
                    val text = "${state.message}$traffic"
                    notify(buildNotification(title, text))
                }
            }
        }
        if (watchdogJob == null) {
            watchdogJob = scope.launch {
                while (isActive) {
                    VpnManager.autoReconnectTick(settings.autoReconnect)
                    // AIR: adaptive path evaluation (no-op unless the user enabled it).
                    if (AirRuntime.context?.enabled == true) {
                        runCatching { AirRuntime.tick() }
                    }
                    VpnManager.readTrafficStats()?.let { (rx, tx) ->
                        VpnManager.publishTraffic(rx, tx)
                    }
                    if (VpnManager.state.value.phase == TunnelPhase.IDLE && !VpnManager.wantsConnection()) {
                        stopSelf()
                        break
                    }
                    delay(WATCHDOG_INTERVAL_MS)
                }
            }
        }
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BoosterService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vz)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(0, "Disconnect", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tunnel status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows the active WireGuard route and connection state."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    override fun onDestroy() {
        stateJob?.cancel()
        watchdogJob?.cancel()
        scope.cancel()
        VpnManager.onServiceDestroyed()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "vz_tunnel_status"
        private const val NOTIFICATION_ID = 4711
        private const val WATCHDOG_INTERVAL_MS = 5_000L
        const val ACTION_STOP = "com.vz.networkbooster.action.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BoosterService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BoosterService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
