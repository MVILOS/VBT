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
            // `apiClient` nie jest @Observable (to zwykły serwis sieciowy, nie stan UI),
            // więc leci jako zwykła zależność przez init, a nie przez `.environment()`
            // (który w SwiftUI/iOS17 działa tylko dla typów Observable).
            RootView(apiClient: apiClient)
                .environment(authRepository)
                .environment(sessionExpiredNotifier)
                .environment(bleManager)
                .environment(heartRateManager)
                .modelContainer(PersistenceContainer.make())
                .preferredColorScheme(.dark)
        }
    }
}
