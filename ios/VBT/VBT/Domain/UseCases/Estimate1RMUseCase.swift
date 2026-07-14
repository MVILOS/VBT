import Foundation

/// Port 1:1 z Android `domain/usecase/Estimate1RMUseCase.kt`.
struct Estimate1RMUseCase {
    /// Klasyczna relacja mean velocity -> %1RM wg González-Badillo (przysiad / wyciskanie
    /// leżąc). Punkty posortowane malejąco po prędkości; interpolacja liniowa pomiędzy.
    ///   >= 1.00 m/s -> ~70% 1RM, 0.75 -> ~80%, 0.50 -> ~90%, <= 0.30 -> ~100%
    private static let velocityToPercent1RM: [(velocity: Float, percent: Double)] = [
        (1.00, 70), (0.75, 80), (0.50, 90), (0.30, 100)
    ]

    /// Estymacja 1RM metodą VBT: z ciężaru i średniej prędkości koncentrycznej powtórzenia.
    /// `nil` gdy dane nie pozwalają na sensowną estymację.
    func estimateFromVelocity(loadKg: Float, meanVelocityMs: Float) -> Double? {
        guard loadKg > 0, meanVelocityMs > 0 else { return nil }
        let percent = percentOf1RM(meanVelocityMs)
        return Double(loadKg) / (percent / 100.0)
    }

    /// %1RM odpowiadający średniej prędkości (interpolacja liniowa, z klamrą na końcach).
    func percentOf1RM(_ meanVelocityMs: Float) -> Double {
        let points = Self.velocityToPercent1RM
        if meanVelocityMs >= points.first!.velocity { return points.first!.percent }
        if meanVelocityMs <= points.last!.velocity { return points.last!.percent }
        for i in 0..<(points.count - 1) {
            let (vHigh, pctHigh) = points[i]
            let (vLow, pctLow) = points[i + 1]
            if meanVelocityMs <= vHigh && meanVelocityMs >= vLow {
                let t = Double((meanVelocityMs - vLow) / (vHigh - vLow))
                return pctLow + t * (pctHigh - pctLow)
            }
        }
        return points.last!.percent
    }

    /// Fallback bez danych prędkości: formuła Epleya z LICZBĄ POWTÓRZEŃ W SERII
    /// (nie z numerem powtórzenia!).
    func estimateEpley(loadKg: Float, repsInSet: Int) -> Double {
        guard loadKg > 0, repsInSet > 0 else { return 0 }
        return Double(loadKg) * (1 + Double(repsInSet) / 30.0)
    }

    /// Preferuje estymację z prędkości; gdy niedostępna, spada do Epleya.
    func estimate(loadKg: Float, meanVelocityMs: Float?, repsInSet: Int) -> Double? {
        if let v = meanVelocityMs, let fromVelocity = estimateFromVelocity(loadKg: loadKg, meanVelocityMs: v) {
            return fromVelocity
        }
        let epley = estimateEpley(loadKg: loadKg, repsInSet: repsInSet)
        return epley > 0 ? epley : nil
    }

    /// Estymacja z profilu load-velocity (regresja liniowa po wielu punktach (ciężar,
    /// prędkość) i ekstrapolacja do MVT ćwiczenia). Wymaga min. dwóch punktów o różnych ciężarach.
    func estimateFromProfile(dataPoints: [(loadKg: Float, velocityMs: Float)], mvt: Float) -> Float? {
        guard dataPoints.count >= 2 else { return nil }

        let n = Double(dataPoints.count)
        let sumX = dataPoints.reduce(0.0) { $0 + Double($1.loadKg) }
        let sumY = dataPoints.reduce(0.0) { $0 + Double($1.velocityMs) }
        let sumXY = dataPoints.reduce(0.0) { $0 + Double($1.loadKg * $1.velocityMs) }
        let sumX2 = dataPoints.reduce(0.0) { $0 + Double($1.loadKg * $1.loadKg) }

        let denominator = n * sumX2 - sumX * sumX
        guard denominator != 0 else { return nil }

        let slope = (n * sumXY - sumX * sumY) / denominator
        let intercept = (sumY - slope * sumX) / n

        guard slope < 0 else { return nil }

        let estimated1RM = Float((mvt.toDouble() - intercept) / slope)
        return estimated1RM > 0 ? estimated1RM : nil
    }
}

private extension Float {
    func toDouble() -> Double { Double(self) }
}
