import SwiftUI

/// Port 1:1 z Android `HomeScreen.kt`: powitanie + chip roli, status BLE, karty statystyk,
/// ostatnie sesje, duży przycisk "NOWY TRENING", "zaplanowane dziś", kafelki menu, wylogowanie.
struct HomeScreen: View {
    let apiClient: APIClient

    @Environment(AuthRepository.self) private var authRepository
    @Environment(VbtBleManager.self) private var bleManager
    @State private var viewModel: HomeViewModel?

    var body: some View {
        Group {
            if let viewModel {
                HomeContent(viewModel: viewModel, apiClient: apiClient)
            } else {
                VbtColor.background.ignoresSafeArea()
            }
        }
        .task {
            if viewModel == nil {
                let vm = HomeViewModel(authRepository: authRepository, apiClient: apiClient)
                viewModel = vm
                await vm.loadData()
            }
        }
        .onChange(of: bleManager.connectionState) { _, state in
            viewModel?.isBleConnected = (state == .connected)
        }
    }
}

private struct HomeContent: View {
    @Bindable var viewModel: HomeViewModel
    let apiClient: APIClient

    var body: some View {
        ScrollView {
            VStack(alignment: .center, spacing: 16) {
                Spacer(minLength: 16)

                // Header: powitanie + chip roli
                HStack {
                    Text("Cześć, \(viewModel.username)")
                        .font(VbtFont.headline)
                        .foregroundStyle(VbtColor.textPrimary)
                    Spacer()
                    Text(viewModel.isCoach ? "COACH" : "ATHLETE")
                        .font(VbtFont.caption)
                        .foregroundStyle(VbtColor.textPrimary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(viewModel.isCoach ? VbtColor.purple : VbtColor.teal)
                        .clipShape(Capsule())
                }

                if let notice = viewModel.offlineNotice {
                    Text(notice)
                        .font(VbtFont.caption)
                        .foregroundStyle(Color(hex: 0xFFC107))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(Color(hex: 0x3A2A00))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }

                // BLE status
                HStack(spacing: 8) {
                    Circle()
                        .fill(viewModel.isBleConnected ? VbtColor.success : VbtColor.textSecondary)
                        .frame(width: 12, height: 12)
                    Text(viewModel.isBleConnected ? "ESP32 Connected" : "Disconnected")
                        .font(VbtFont.body)
                        .foregroundStyle(VbtColor.textSecondary)
                    Spacer()
                }

                if let stats = viewModel.dashboardStats {
                    HStack(spacing: 12) {
                        StatCard(label: "Sesje w tygodniu", value: "\(stats.sessionsThisWeek)")
                        StatCard(label: "Aktywne plany", value: "\(stats.activePlans)")
                        if viewModel.isCoach {
                            StatCard(label: "Zawodnicy", value: "\(stats.totalAthletes)")
                        }
                    }
                }

                if !viewModel.recentSessions.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("OSTATNIE SESJE")
                            .font(VbtFont.title)
                            .foregroundStyle(VbtColor.textPrimary)
                        ForEach(viewModel.recentSessions) { session in
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(session.athleteName)
                                        .font(VbtFont.bodyBold)
                                        .foregroundStyle(VbtColor.textPrimary)
                                    Text(String(session.startedAt.prefix(10)))
                                        .font(VbtFont.caption)
                                        .foregroundStyle(VbtColor.textSecondary)
                                }
                                Spacer()
                                Text("\(session.repsCount) reps")
                                    .font(VbtFont.caption)
                                    .foregroundStyle(VbtColor.teal)
                            }
                            .padding(12)
                            .background(VbtColor.surfaceVariant)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                // Duży przycisk "NOWY TRENING"
                NavigationLink {
                    WorkoutScreen(apiClient: apiClient)
                } label: {
                    VStack(spacing: 8) {
                        Image(systemName: "play.fill")
                            .font(.system(size: 32))
                            .foregroundStyle(VbtColor.teal)
                        Text("NOWY TRENING")
                            .font(VbtFont.headline)
                            .foregroundStyle(VbtColor.textPrimary)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 120)
                    .background(VbtColor.surface)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(VbtColor.teal, lineWidth: 2))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                if !viewModel.todayEntries.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("ZAPLANOWANE DZIŚ")
                            .font(VbtFont.title)
                            .foregroundStyle(VbtColor.textPrimary)
                        ForEach(viewModel.todayEntries) { entry in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(entry.title)
                                    .font(VbtFont.bodyBold)
                                    .foregroundStyle(VbtColor.textPrimary)
                                if let slot = entry.timeSlot, !slot.isEmpty {
                                    Text("Godzina: \(slot)")
                                        .font(VbtFont.caption)
                                        .foregroundStyle(VbtColor.textSecondary)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(16)
                            .background(VbtColor.surfaceVariant)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                HomeMenuTiles(apiClient: apiClient)

                Button(role: .destructive) {
                    viewModel.logout()
                } label: {
                    Label("Wyloguj się", systemImage: "rectangle.portrait.and.arrow.right")
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                }
                .buttonStyle(.bordered)
                .tint(VbtColor.teal)
                .padding(.top, 8)
            }
            .padding(24)
        }
        .background(VbtColor.background)
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct StatCard: View {
    let label: String
    let value: String

    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(VbtFont.title)
                .foregroundStyle(VbtColor.teal)
            Text(label)
                .font(VbtFont.caption)
                .foregroundStyle(VbtColor.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(12)
        .background(VbtColor.surfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

/// Odpowiednik dolnego `LazyRow` kafelków w Androidzie: Historia / Plany / Analityka /
/// Urządzenie(+Zawodnicy dla coacha). Na razie tylko "Urządzenie" prowadzi do realnego
/// ekranu (`ConnectScreen`) - reszta to placeholdery kolejnych faz portu.
private struct HomeMenuTiles: View {
    let apiClient: APIClient
    @Environment(AuthRepository.self) private var authRepository

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                NavigationLink {
                    ConnectScreen()
                } label: {
                    MenuTile(label: "Urządzenie", systemImage: "dot.radiowaves.left.and.right")
                }
                NavigationLink {
                    HistoryScreen(apiClient: apiClient)
                } label: {
                    MenuTile(label: "Historia", systemImage: "clock.arrow.circlepath")
                }
                NavigationLink {
                    PlanListScreen(apiClient: apiClient)
                } label: {
                    MenuTile(label: "Plany", systemImage: "figure.strengthtraining.traditional")
                }
                NavigationLink {
                    Text("Analityka — w budowie")
                } label: {
                    MenuTile(label: "Analityka", systemImage: "chart.bar.fill")
                }
                if authRepository.currentRole == .coach {
                    NavigationLink {
                        Text("Zawodnicy — w budowie")
                    } label: {
                        MenuTile(label: "Zawodnicy", systemImage: "person.2.fill")
                    }
                }
            }
        }
    }
}

private struct MenuTile: View {
    let label: String
    let systemImage: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: systemImage)
                .font(.system(size: 24))
                .foregroundStyle(VbtColor.teal)
            Text(label)
                .font(VbtFont.caption)
                .foregroundStyle(VbtColor.textPrimary)
        }
        .frame(width: 100, height: 90)
        .background(VbtColor.surfaceVariant)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(VbtColor.teal, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}
