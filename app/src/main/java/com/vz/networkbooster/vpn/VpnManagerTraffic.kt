package com.vz.networkbooster.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Traffic counters live in their own holder so the watchdog can publish them
 * without touching the connection phase state machine.
 */
object TrafficMonitor {
    private val _counter = MutableStateFlow(0L to 0L)
    val counter: StateFlow<Pair<Long, Long>> = _counter.asStateFlow()

    fun publish(rx: Long, tx: Long) {
        _counter.update { rx to tx }
    }
}
