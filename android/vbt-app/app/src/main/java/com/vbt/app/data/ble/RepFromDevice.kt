package com.vbt.app.data.ble

data class RepFromDevice(
    // Średnia prędkość koncentryczna (bajty 0-1 pakietu) - podstawowa metryka VBT
    val meanVelocityMs: Float,
    // Prędkość szczytowa (bajty 12-13 pakietu); stary firmware wysyła 0,
    // wtedy parser podstawia meanVelocityMs jako fallback
    val peakVelocityMs: Float,
    val distanceM: Float,
    val durationMs: Int,
    val deviceTimestamp: Long,
    val repIndex: Int
)
