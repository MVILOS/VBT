import SwiftUI

/// Odpowiednik `VbtBottomNavBarLayout` + `VbtBottomNavBar` z `VbtNavGraph.kt`.
/// Każdy tab niesie własny `NavigationStack` (odpowiednik osobnego back stacku per
/// dolna zakładka w Compose Navigation).
struct MainTabView: View {
    let apiClient: APIClient

    @Environment(AuthRepository.self) private var auth

    var body: some View {
        TabView {
            NavigationStack {
                HomeScreen(apiClient: apiClient)
            }
            .tabItem { Label("Home", systemImage: "house.fill") }

            NavigationStack {
                WorkoutScreen(apiClient: apiClient)
            }
            .tabItem { Label("Workout", systemImage: "figure.strengthtraining.traditional") }

            NavigationStack {
                PlaceholderScreen(title: "Plany", subtitle: "Faza 5 planu portu — ekran w budowie.")
            }
            .tabItem { Label("Plans", systemImage: "chart.bar.fill") }

            NavigationStack {
                PlaceholderScreen(title: "Harmonogram", subtitle: "Faza 5 planu portu — ekran w budowie.")
            }
            .tabItem { Label("Schedule", systemImage: "calendar") }

            NavigationStack {
                PlaceholderScreen(title: "Historia", subtitle: "Faza 5 planu portu — ekran w budowie.")
            }
            .tabItem { Label("History", systemImage: "clock.arrow.circlepath") }

            if auth.currentRole == .coach {
                NavigationStack {
                    PlaceholderScreen(title: "Zawodnicy", subtitle: "Faza 5 planu portu — ekran w budowie.")
                }
                .tabItem { Label("Athletes", systemImage: "person.2.fill") }
            }
        }
        .tint(VbtColor.teal)
    }
}

/// Tymczasowy widok dla ekranów jeszcze nie przeniesionych z Androida — trzyma nawigację
/// spójną już teraz, żeby kolejne fazy portu podłączały realną treść bez przebudowy shellu.
private struct PlaceholderScreen: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "hammer.fill")
                .font(.system(size: 40))
                .foregroundStyle(VbtColor.textSecondary)
            Text(subtitle)
                .font(VbtFont.body)
                .foregroundStyle(VbtColor.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(VbtColor.background)
        .navigationTitle(title)
    }
}
