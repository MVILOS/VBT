import XCTest
@testable import VBT

/// Port 1:1 z Android `GetVelocityZoneUseCaseTest.kt`.
final class GetVelocityZoneUseCaseTests: XCTestCase {
    private let useCase = GetVelocityZoneUseCase()

    func testStrefyDobieraneWgProgowPredkosci() {
        XCTAssertEqual(useCase(0.2), .absoluteStrength)
        XCTAssertEqual(useCase(0.45), .strengthSpeed)
        XCTAssertEqual(useCase(0.75), .power)
        XCTAssertEqual(useCase(1.0), .speedStrength)
        XCTAssertEqual(useCase(1.3), .speed)
        XCTAssertEqual(useCase(2.0), .ballistic)
    }

    func testDolnaGranicaStrefyNalezyDoTejStrefy() {
        XCTAssertEqual(useCase(0.35), .strengthSpeed)
        XCTAssertEqual(useCase(0.6), .power)
        XCTAssertEqual(useCase(1.5), .ballistic)
    }

    func testPredkoscZeroINiegatywnaWpadaDoAbsoluteStrength() {
        XCTAssertEqual(useCase(0), .absoluteStrength)
        XCTAssertEqual(useCase(-0.1), .absoluteStrength)
    }
}
