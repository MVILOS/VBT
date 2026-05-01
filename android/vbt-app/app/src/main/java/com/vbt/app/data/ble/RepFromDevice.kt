package com.vbt.app.data.ble

data class RepFromDevice(
    val maxVelocityMs: Float,
    val distanceM: Float,
    val durationMs: Int,
    val deviceTimestamp: Long,
    val repIndex: Int
)
