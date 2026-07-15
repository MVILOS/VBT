import XCTest
@testable import VBT

/// Port 1:1 z Android `CalculatePowerUseCaseTest.kt`.
final class CalculatePowerUseCaseTests: XCTestCase {
    private let useCase = CalculatePowerUseCase()

    func testMocSzczytowaToSilaRazyPredkoscSzczytowa() {
        // 100 kg * 9.81 * 1.0 m/s = 981 W
        XCTAssertEqual(useCase.peakPower(loadKg: 100, peakVelocityMs: 1.0), 981, accuracy: 0.01)
        // 60 kg * 9.81 * 0.5 m/s = 294.3 W
        XCTAssertEqual(useCase.peakPower(loadKg: 60, peakVelocityMs: 0.5), 294.3, accuracy: 0.01)
    }

    func testMocSredniaLiczonaZDystansuICzasu() {
        // 100 kg, 0.5 m w 1000 ms -> v = 0.5 m/s -> 100*9.81*0.5 = 490.5 W
        XCTAssertEqual(useCase.meanPower(loadKg: 100, distanceM: 0.5, durationMs: 1000), 490.5, accuracy: 0.01)
        // Dwa razy krótszy czas -> dwa razy większa moc
        XCTAssertEqual(useCase.meanPower(loadKg: 100, distanceM: 0.5, durationMs: 500), 981, accuracy: 0.01)
    }

    func testMocSredniaLiczonaBezposrednioZeSredniejPredkosci() {
        // 100 kg * 9.81 * 0.5 m/s = 490.5 W
        XCTAssertEqual(useCase.meanPower(loadKg: 100, meanVelocityMs: 0.5), 490.5, accuracy: 0.01)
        // 60 kg * 9.81 * 0.8 m/s = 470.88 W
        XCTAssertEqual(useCase.meanPower(loadKg: 60, meanVelocityMs: 0.8), 470.88, accuracy: 0.01)
    }

    func testZerowyLubUjemnyCzasTrwaniaDajeZeroMocy() {
        XCTAssertEqual(useCase.meanPower(loadKg: 100, distanceM: 0.5, durationMs: 0), 0)
        XCTAssertEqual(useCase.meanPower(loadKg: 100, distanceM: 0.5, durationMs: -100), 0)
    }
}
