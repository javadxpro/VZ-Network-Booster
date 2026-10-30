package com.vz.networkbooster.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    fun bytes(value: Long): String = when {
        value >= 1_073_741_824 -> "%.2f GB".format(value / 1_073_741_824.0)
        value >= 1_048_576 -> "%.1f MB".format(value / 1_048_576.0)
        value >= 1024 -> "%.1f KB".format(value / 1024.0)
        else -> "$value B"
    }

    fun duration(sinceEpochMs: Long): String {
        if (sinceEpochMs <= 0L) return "—"
        val seconds = (System.currentTimeMillis() - sinceEpochMs) / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%dh %02dm".format(h, m) else "%dm %02ds".format(m, s)
    }

    fun clock(epochMs: Long): String =
        if (epochMs <= 0L) "—" else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(epochMs))

    fun ms(value: Double?): String = if (value == null) "—" else "%.1f ms".format(value)
}
