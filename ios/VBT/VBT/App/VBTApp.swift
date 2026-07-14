import SwiftUI

/// Composition root — odpowiednik Hilt `VbtApplication.kt` + moduły DI, ale ręczny
/// (patrz IOS_PORT_PLAN.md p.6: bez ciężkiego frameworka DI przy tej skali projektu).
@main
struct VBTApp: App {
    private let keychain = KeychainStore()
    private let userPreferences = UserPreferences()
    private let sessionExpiredNotifier = SessionExpiredNotifier()
    private let apiClient: APIClient
    private let authRepository: AuthRepository
    private let bleManager = VbtBleManager()
    private let heartRateManager = HeartRateManager()

    init() {
        let keychain = self.keychain
        apiClient = APIClient(keychain: keychain, sessionExpiredNotifier: sessionExpiredNotifier)
        authRepository = AuthRepository(api: apiClient, keychain: keychain, prefs: userPreferences)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(authRepository)
                .environment(sessionExpiredNotifier)
                .environment(bleManager)
                .environment(heartRateManager)
                .environment(apiClient)
                .preferredColorScheme(.dark)
        }
    }
}
