import Foundation

/// Port 1:1 z Android `data/ble/RepFromDevice.kt`.
struct RepFromDevice: Equatable {
    /// Średnia prędkość koncentryczna (bajty 0-1 pakietu) — podstawowa metryka VBT.
    let meanVelocityMs: Float
    /// Prędkość szczytowa (bajty 12-13 pakietu); stary firmware wysyła 0,
    /// wtedy parser podstawia meanVelocityMs jako fallback.
    let peakVelocityMs: Float
    let distanceM: Float
    let durationMs: Int
    let deviceTimestamp: UInt32
    let repIndex: Int
}
