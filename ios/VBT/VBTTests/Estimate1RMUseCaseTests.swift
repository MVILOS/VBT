import XCTest
@testable import VBT

/// Port 1:1 z Android `Estimate1RMUseCaseTest.kt`.
final class Estimate1RMUseCaseTests: XCTestCase {
    private let useCase = Estimate1RMUseCase()

    // --- Tabela prędkość -> %1RM (González-Badillo) ---

    func testPredkosc03Odpowiada100Procent1RM() {
        XCTAssertEqual(useCase.percentOf1RM(0.3), 100.0, accuracy: 0.001)
        XCTAssertEqual(useCase.estimateFromVelocity(loadKg: 100, meanVelocityMs: 0.3)!, 100.0, accuracy: 0.01)
    }

    func testPredkosc05Odpowiada90Procent1RM() {
        XCTAssertEqual(useCase.percentOf1RM(0.5), 90.0, accuracy: 0.001)
        // 90 kg @ 0.5 m/s -> 1RM = 90 / 0.9 = 100 kg
        XCTAssertEqual(useCase.estimateFromVelocity(loadKg: 90, meanVelocityMs: 0.5)!, 100.0, accuracy: 0.01)
    }

    func testPredkosc10Odpowiada70Procent1RM() {
        XCTAssertEqual(useCase.percentOf1RM(1.0), 70.0, accuracy: 0.001)
        XCTAssertEqual(useCase.estimateFromVelocity(loadKg: 70, meanVelocityMs: 1.0)!, 100.0, accuracy: 0.01)
    }

    func testInterpolacjaLiniowaMiedzyPunktamiTabeli() {
        // Środek odcinka 0.5-0.75: (90+80)/2 = 85%
        XCTAssertEqual(useCase.percentOf1RM(0.625), 85.0, accuracy: 0.001)
        // Środek odcinka 0.3-0.5: (100+90)/2 = 95%
        XCTAssertEqual(useCase.percentOf1RM(0.4), 95.0, accuracy: 0.001)
    }

    func testPredkosciPozaZakresemTabeliSaKlamrowane() {
        XCTAssertEqual(useCase.percentOf1RM(1.8), 70.0, accuracy: 0.001)
        XCTAssertEqual(useCase.percentOf1RM(0.1), 100.0, accuracy: 0.001)
    }

    func testNieprawidloweDaneZwracajaNil() {
        XCTAssertNil(useCase.estimateFromVelocity(loadKg: 0, meanVelocityMs: 0.5))
        XCTAssertNil(useCase.estimateFromVelocity(loadKg: -10, meanVelocityMs: 0.5))
        XCTAssertNil(useCase.estimateFromVelocity(loadKg: 100, meanVelocityMs: 0))
        XCTAssertNil(useCase.estimateFromVelocity(loadKg: 100, meanVelocityMs: -0.5))
    }

    // --- Fallback Epley ---

    func testEpleyLiczyZLiczbyPowtorzenWSerii() {
        // 100 kg x 10 powtórzeń -> 100 * (1 + 10/30) = 133.33
        XCTAssertEqual(useCase.estimateEpley(loadKg: 100, repsInSet: 10), 133.333, accuracy: 0.01)
        XCTAssertEqual(useCase.estimateEpley(loadKg: 100, repsInSet: 1), 103.333, accuracy: 0.01)
        XCTAssertEqual(useCase.estimateEpley(loadKg: 100, repsInSet: 0), 0.0, accuracy: 0.001)
    }

    func testEstimatePreferujePredkoscASpadaDoEpleya() {
        // Z prędkością: VBT (90 kg @ 0.5 -> 100 kg)
        XCTAssertEqual(useCase.estimate(loadKg: 90, meanVelocityMs: 0.5, repsInSet: 5)!, 100.0, accuracy: 0.01)
        // Bez prędkości: Epley z liczbą powtórzeń w serii
        XCTAssertEqual(useCase.estimate(loadKg: 100, meanVelocityMs: nil, repsInSet: 10)!, 133.333, accuracy: 0.01)
        // Prędkość nieprawidłowa -> też Epley
        XCTAssertEqual(useCase.estimate(loadKg: 100, meanVelocityMs: 0, repsInSet: 10)!, 133.333, accuracy: 0.01)
    }

    // --- Profil load-velocity (regresja) ---

    func testProfilLoadVelocityEkstrapolujeDoMvt() {
        // Idealna liniowa relacja: v = 1.0 - 0.007 * load
        // Dla mvt = 0.3: load = (1.0 - 0.3) / 0.007 = 100 kg
        let points: [(loadKg: Float, velocityMs: Float)] = [(50, 0.65), (70, 0.51), (90, 0.37)]
        let result = useCase.estimateFromProfile(dataPoints: points, mvt: 0.3)
        XCTAssertNotNil(result)
        XCTAssertEqual(result!, 100, accuracy: 0.5)
    }

    func testProfilWymagaConajmniejDwochPunktowIUjemnegoNachylenia() {
        XCTAssertNil(useCase.estimateFromProfile(dataPoints: [(100, 0.5)], mvt: 0.3))
        // Dodatnie nachylenie (prędkość rośnie z ciężarem) = dane bez sensu
        XCTAssertNil(useCase.estimateFromProfile(dataPoints: [(50, 0.3), (100, 0.8)], mvt: 0.3))
    }
}
