package com.vbt.app.data.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RepPacketParserTest {

    private fun buildPacket(
        meanVelocityRaw: Int,
        distanceRaw: Int = 0,
        durationRaw: Int = 0,
        timestampRaw: Long = 0L,
        repIndexRaw: Int = 0,
        peakVelocityRaw: Int = 0
    ): ByteArray {
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(meanVelocityRaw.toShort())
        buffer.putShort(distanceRaw.toShort())
        buffer.putShort(durationRaw.toShort())
        buffer.putInt(timestampRaw.toInt())
        buffer.putShort(repIndexRaw.toShort())
        buffer.putShort(peakVelocityRaw.toShort())
        buffer.putShort(0) // reserved
        return buffer.array()
    }

    @Test
    fun `parsuje wszystkie pola pelnego pakietu`() {
        val packet = buildPacket(
            meanVelocityRaw = 750,     // 0.75 m/s
            distanceRaw = 620,         // 0.62 m
            durationRaw = 850,         // 850 ms
            timestampRaw = 123_456L,
            repIndexRaw = 7,
            peakVelocityRaw = 1_100    // 1.10 m/s
        )

        val rep = RepPacketParser.parse(packet)

        assertNotNull(rep)
        rep!!
        assertEquals(0.75f, rep.meanVelocityMs, 0.0001f)
        assertEquals(1.10f, rep.peakVelocityMs, 0.0001f)
        assertEquals(0.62f, rep.distanceM, 0.0001f)
        assertEquals(850, rep.durationMs)
        assertEquals(123_456L, rep.deviceTimestamp)
        assertEquals(7, rep.repIndex)
    }

    @Test
    fun `stary firmware - peak 0 uzywa mean velocity jako fallback`() {
        val packet = buildPacket(meanVelocityRaw = 500, peakVelocityRaw = 0)

        val rep = RepPacketParser.parse(packet)

        assertNotNull(rep)
        assertEquals(0.5f, rep!!.meanVelocityMs, 0.0001f)
        assertEquals(0.5f, rep.peakVelocityMs, 0.0001f)
    }

    @Test
    fun `wartosci uint16 powyzej Short MAX parsowane bez znaku`() {
        // 40000 nie mieści się w Short ze znakiem - parser musi czytać unsigned
        val packet = buildPacket(meanVelocityRaw = 40_000, durationRaw = 65_000)

        val rep = RepPacketParser.parse(packet)

        assertNotNull(rep)
        assertEquals(40.0f, rep!!.meanVelocityMs, 0.001f)
        assertEquals(65_000, rep.durationMs)
    }

    @Test
    fun `timestamp uint32 parsowany bez znaku`() {
        val bigTimestamp = 3_000_000_000L // > Int.MAX_VALUE
        val packet = buildPacket(meanVelocityRaw = 100, timestampRaw = bigTimestamp)

        val rep = RepPacketParser.parse(packet)

        assertEquals(bigTimestamp, rep!!.deviceTimestamp)
    }

    @Test
    fun `pakiet krotszy niz 16 bajtow zwraca null`() {
        assertNull(RepPacketParser.parse(ByteArray(15)))
        assertNull(RepPacketParser.parse(ByteArray(0)))
        assertNull(RepPacketParser.parse(null))
    }
}
