import Foundation

/// Odpowiednik `data/remote/SessionExpiredNotifier.kt` — sygnalizuje UI (np. RootView),
/// że token JWT wygasł (401 poza endpointami logowania) i trzeba wrócić do ekranu logowania.
@Observable
final class SessionExpiredNotifier {
    private(set) var didExpire = false

    func notifyExpired() {
        didExpire = true
    }

    func acknowledge() {
        didExpire = false
    }
}
