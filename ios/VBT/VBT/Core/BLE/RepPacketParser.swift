import Foundation

/// Port 1:1 z Android `data/ble/RepPacketParser.kt` — czysty parser bez zależności od
/// frameworka BLE, żeby dało się go pokryć testami jednostkowymi identycznymi jak w Kotlinie.
///
/// 16-bajtowy pakiet wyniku powtórzenia z czujnika VBT (charakterystyka FF02, little-endian):
///  bajty 0-1   meanVelocity  (uint16, m/s * 1000)
///  bajty 2-3   distance      (uint16, m * 1000)
///  bajty 4-5   duration      (uint16, ms)
///  bajty 6-9   timestamp     (uint32, ms od bootu urządzenia)
///  bajty 10-11 repIndex      (uint16)
///  bajty 12-13 maxVelocity   (uint16, m/s * 1000; 0 = stary firmware bez peak)
///  bajty 14-15 reserved
enum RepPacketParser {
    static let packetSize = 16

    static func parse(_ bytes: Data?) -> RepFromDevice? {
        guard let bytes, bytes.count >= packetSize else { return nil }

        func u16(_ offset: Int) -> UInt16 {
            let lo = UInt16(bytes[bytes.startIndex + offset])
            let hi = UInt16(bytes[bytes.startIndex + offset + 1])
            return lo | (hi << 8)
        }
        func u32(_ offset: Int) -> UInt32 {
            var value: UInt32 = 0
            for i in 0..<4 {
                value |= UInt32(bytes[bytes.startIndex + offset + i]) << (8 * i)
            }
            return value
        }

        let meanVelocity = Float(u16(0)) / 1000
        let distance = Float(u16(2)) / 1000
        let duration = Int(u16(4))
        let timestamp = u32(6)
        let repIndex = Int(u16(10))
        let peakRaw = u16(12)
        // Stary firmware zostawia bajty 12-13 wyzerowane - wtedy najlepszym
        // przybliżeniem peak velocity jest mean velocity (kompatybilność wstecz).
        let peakVelocity = peakRaw == 0 ? meanVelocity : Float(peakRaw) / 1000

        return RepFromDevice(
            meanVelocityMs: meanVelocity,
            peakVelocityMs: peakVelocity,
            distanceM: distance,
            durationMs: duration,
            deviceTimestamp: timestamp,
            repIndex: repIndex
        )
    }
}
