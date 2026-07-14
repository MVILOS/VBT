import SwiftUI

/// Odpowiednik `ui/theme/Type.kt` — grube nagłówki (styl Vitruve), zwykły tekst dla reszty.
enum VbtFont {
    static let displayLarge = Font.system(size: 57, weight: .bold, design: .rounded)
    static let headline = Font.system(size: 24, weight: .bold)
    static let title = Font.system(size: 20, weight: .semibold)
    static let body = Font.system(size: 16, weight: .regular)
    static let bodyBold = Font.system(size: 16, weight: .semibold)
    static let caption = Font.system(size: 12, weight: .medium)

    /// Duży wskaźnik prędkości na WorkoutScreen — cyfry monospaced, żeby nie "skakały" przy zmianie wartości.
    static let velocityDigit = Font.system(size: 96, weight: .heavy, design: .rounded).monospacedDigit()
}

enum VbtTheme {
    static func apply() {
        UINavigationBar.appearance().largeTitleTextAttributes = [
            .foregroundColor: UIColor(VbtColor.textPrimary)
        ]
    }
}
