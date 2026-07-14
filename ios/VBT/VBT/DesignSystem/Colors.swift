import SwiftUI

/// Paleta 1:1 z Android `ui/theme/Color.kt` (dark + teal).
enum VbtColor {
    // Background
    static let background = Color(hex: 0x0F0F0F)
    static let surface = Color(hex: 0x1A1A1A)
    static let surfaceVariant = Color(hex: 0x242424)

    // Accent
    static let teal = Color(hex: 0x4ECDC4)
    static let purple = Color(hex: 0x7C3AED)

    // Text
    static let textPrimary = Color(hex: 0xFFFFFF)
    static let textSecondary = Color(hex: 0x9E9E9E)
    static let textDisabled = Color(hex: 0x4A4A4A)

    // Semantic
    static let error = Color(hex: 0xFF5252)
    static let success = Color(hex: 0x4CAF50)
}

extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}
