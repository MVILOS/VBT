import Foundation

/// Port 1:1 z Android `domain/usecase/CalculatePowerUseCase.kt`.
struct CalculatePowerUseCase {
    func meanPower(loadKg: Float, distanceM: Float, durationMs: Int) -> Float {
        guard durationMs > 0 else { return 0 }
        let force = loadKg * 9.81
        let meanVelocity = distanceM / (Float(durationMs) / 1000)
        return force * meanVelocity
    }

    func meanPower(loadKg: Float, meanVelocityMs: Float) -> Float {
        let force = loadKg * 9.81
        return force * meanVelocityMs
    }

    func peakPower(loadKg: Float, peakVelocityMs: Float) -> Float {
        let force = loadKg * 9.81
        return force * peakVelocityMs
    }
}
