import XCTest
@testable import VBT

/// Port 1:1 z Android `RepPacketParserTest.kt`.
final class RepPacketParserTests: XCTestCase {

    private func buildPacket(
        meanVelocityRaw: UInt16,
        distanceRaw: UInt16 = 0,
        durationRaw: UInt16 = 0,
        timestampRaw: UInt32 = 0,
        repIndexRaw: UInt16 = 0,
        peakVelocityRaw: UInt16 = 0
    ) -> Data {
        var data = Data()
        func appendU16(_ value: UInt16) {
            data.append(UInt8(value & 0xFF))
            data.append(UInt8((value >> 8) & 0xFF))
        }
        func appendU32(_ value: UInt32) {
            for i in 0..<4 { data.append(UInt8((value >> (8 * i)) & 0xFF)) }
        }

        appendU16(meanVelocityRaw)
        appendU16(distanceRaw)
        appendU16(durationRaw)
        appendU32(timestampRaw)
        appendU16(repIndexRaw)
        appendU16(peakVelocityRaw)
        appendU16(0) // reserved
        return data
    }

    func testParsujeWszystkiePolaPelnegoPakietu() {
        let packet = buildPacket(
            meanVelocityRaw: 750,      // 0.75 m/s
            distanceRaw: 620,          // 0.62 m
            durationRaw: 850,          // 850 ms
            timestampRaw: 123_456,
            repIndexRaw: 7,
            peakVelocityRaw: 1_100     // 1.10 m/s
        )

        let rep = RepPacketParser.parse(packet)

        XCTAssertNotNil(rep)
        XCTAssertEqual(rep!.meanVelocityMs, 0.75, accuracy: 0.0001)
        XCTAssertEqual(rep!.peakVelocityMs, 1.10, accuracy: 0.0001)
        XCTAssertEqual(rep!.distanceM, 0.62, accuracy: 0.0001)
        XCTAssertEqual(rep!.durationMs, 850)
        XCTAssertEqual(rep!.deviceTimestamp, 123_456)
        XCTAssertEqual(rep!.repIndex, 7)
    }

    func testStaryFirmwarePeakZeroUzywaMeanVelocityJakoFallback() {
        let packet = buildPacket(meanVelocityRaw: 500, peakVelocityRaw: 0)

        let rep = RepPacketParser.parse(packet)

        XCTAssertNotNil(rep)
        XCTAssertEqual(rep!.meanVelocityMs, 0.5, accuracy: 0.0001)
        XCTAssertEqual(rep!.peakVelocityMs, 0.5, accuracy: 0.0001)
    }

    func testWartosciUint16PowyzejInt16MaxParsowaneBezZnaku() {
        // 40000 nie mieści się w Int16 ze znakiem - parser musi czytać unsigned
        let packet = buildPacket(meanVelocityRaw: 40_000, durationRaw: 65_000)

        let rep = RepPacketParser.parse(packet)

        XCTAssertNotNil(rep)
        XCTAssertEqual(rep!.meanVelocityMs, 40.0, accuracy: 0.001)
        XCTAssertEqual(rep!.durationMs, 65_000)
    }

    func testTimestampUint32ParsowanyBezZnaku() {
        let bigTimestamp: UInt32 = 3_000_000_000 // > Int32.max
        let packet = buildPacket(meanVelocityRaw: 100, timestampRaw: bigTimestamp)

        let rep = RepPacketParser.parse(packet)

        XCTAssertEqual(rep!.deviceTimestamp, bigTimestamp)
    }

    func testPakietKrotszyNiz16BajtowZwracaNil() {
        XCTAssertNil(RepPacketParser.parse(Data(repeating: 0, count: 15)))
        XCTAssertNil(RepPacketParser.parse(Data()))
        XCTAssertNil(RepPacketParser.parse(nil))
    }
}
