import SwiftUI

/// Odpowiednik startDestination logiki w `VbtNavGraph.kt`: Login/Register dopóki nie ma
/// sesji, potem `MainTabView`. Reaguje też na wygaśnięcie sesji (401 spoza /auth/*) —
/// tak jak `LaunchedEffect(sessionExpiredNotifier)` po stronie Androida.
struct RootView: View {
    let apiClient: APIClient

    @Environment(AuthRepository.self) private var auth
    @Environment(SessionExpiredNotifier.self) private var sessionExpiredNotifier
    @State private var showSessionExpiredAlert = false

    var body: some View {
        Group {
            if auth.isLoggedIn {
                MainTabView(apiClient: apiClient)
            } else {
                LoginScreen()
            }
        }
        .onChange(of: sessionExpiredNotifier.didExpire) { _, expired in
            guard expired else { return }
            auth.logout()
            showSessionExpiredAlert = true
            sessionExpiredNotifier.acknowledge()
        }
        .alert("Sesja wygasła", isPresented: $showSessionExpiredAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Zaloguj się ponownie.")
        }
    }
}
