package com.vbt.app.data.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Czysty (bez zależności od Androida) parser 16-bajtowego pakietu wyniku
 * powtórzenia z czujnika VBT (charakterystyka FF02, little-endian):
 *
 *  bajty 0-1   meanVelocity  (uint16, m/s * 1000)
 *  bajty 2-3   distance      (uint16, m * 1000)
 *  bajty 4-5   duration      (uint16, ms)
 *  bajty 6-9   timestamp     (uint32, ms od bootu urządzenia)
 *  bajty 10-11 repIndex      (uint16)
 *  bajty 12-13 maxVelocity   (uint16, m/s * 1000; 0 = stary firmware bez peak)
 *  bajty 14-15 reserved
 */
object RepPacketParser {

    const val PACKET_SIZE = 16

    fun parse(bytes: ByteArray?): RepFromDevice? {
        if (bytes == null || bytes.size < PACKET_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val meanVelocity = (buffer.short.toInt() and 0xFFFF) / 1000f
        val distance = (buffer.short.toInt() and 0xFFFF) / 1000f
        val duration = buffer.short.toInt() and 0xFFFF
        val timestamp = buffer.int.toLong() and 0xFFFFFFFFL
        val repIndex = buffer.short.toInt() and 0xFFFF
        val peakRaw = buffer.short.toInt() and 0xFFFF
        // Stary firmware zostawia bajty 12-13 wyzerowane - wtedy najlepszym
        // przybliżeniem peak velocity jest mean velocity (kompatybilność wstecz).
        val peakVelocity = if (peakRaw == 0) meanVelocity else peakRaw / 1000f

        return RepFromDevice(
            meanVelocityMs = meanVelocity,
            peakVelocityMs = peakVelocity,
            distanceM = distance,
            durationMs = duration,
            deviceTimestamp = timestamp,
            repIndex = repIndex
        )
    }
}
