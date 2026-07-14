import SwiftUI

/// Port 1:1 z Android `domain/model/VelocityZone.kt`.
enum VelocityZone: String, CaseIterable {
    case absoluteStrength, strengthSpeed, power, speedStrength, speed, ballistic

    var label: String {
        switch self {
        case .absoluteStrength: return "Strength"
        case .strengthSpeed: return "Str-Speed"
        case .power: return "Power"
        case .speedStrength: return "Spd-Str"
        case .speed: return "Speed"
        case .ballistic: return "Ballistic"
        }
    }

    var minVelocity: Float {
        switch self {
        case .absoluteStrength: return 0.0
        case .strengthSpeed: return 0.35
        case .power: return 0.6
        case .speedStrength: return 0.9
        case .speed: return 1.15
        case .ballistic: return 1.5
        }
    }

    var maxVelocity: Float {
        switch self {
        case .absoluteStrength: return 0.35
        case .strengthSpeed: return 0.6
        case .power: return 0.9
        case .speedStrength: return 1.15
        case .speed: return 1.5
        case .ballistic: return 5.0
        }
    }

    var color: Color {
        switch self {
        case .absoluteStrength: return Color(hex: 0xFF0000)
        case .strengthSpeed: return Color(hex: 0xFF8800)
        case .power: return Color(hex: 0xFFCC00)
        case .speedStrength: return Color(hex: 0x00CC00)
        case .speed: return Color(hex: 0x0088FF)
        case .ballistic: return Color(hex: 0x8800FF)
        }
    }

    /// `entries` w kolejności rosnącej prędkości — jak `entries.lastOrNull { velocity >= it.minVelocity }` w Kotlinie.
    private static let ordered: [VelocityZone] = [.absoluteStrength, .strengthSpeed, .power, .speedStrength, .speed, .ballistic]

    static func from(velocity: Float) -> VelocityZone {
        ordered.last { velocity >= $0.minVelocity } ?? .absoluteStrength
    }
}
