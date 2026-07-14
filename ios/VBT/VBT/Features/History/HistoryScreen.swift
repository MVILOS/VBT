import SwiftUI

/// Port z Android `HistoryScreen.kt` (uproszczony - patrz `HistoryViewModel`): lista sesji
/// (athlete widzi swoje, coach widzi wszystkie + filtr po zawodniku), tap -> szczegóły.
struct HistoryScreen: View {
    let apiClient: APIClient

    @Environment(AuthRepository.self) private var authRepository
    @State private var viewModel: HistoryViewModel?

    var body: some View {
        Group {
            if let viewModel {
                HistoryContent(viewModel: viewModel, apiClient: apiClient)
            } else {
                VbtColor.background.ignoresSafeArea()
            }
        }
        .task {
            if viewModel == nil {
                let vm = HistoryViewModel(apiClient: apiClient, authRepository: authRepository)
                viewModel = vm
                await vm.onAppear()
            }
        }
        .navigationTitle("Historia")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct HistoryContent: View {
    @Bindable var viewModel: HistoryViewModel
    let apiClient: APIClient

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if viewModel.isCoach && !viewModel.athletes.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            FilterChip(label: "Wszyscy", isSelected: viewModel.selectedAthleteId == nil) {
                                Task { await viewModel.filterByAthlete(nil) }
                            }
                            ForEach(viewModel.athletes) { athlete in
                                FilterChip(label: athlete.username, isSelected: viewModel.selectedAthleteId == athlete.id) {
                                    Task { await viewModel.filterByAthlete(athlete.id) }
                                }
                            }
                        }
                    }
                }

                if viewModel.isLoading {
                    ProgressView().tint(VbtColor.teal).frame(maxWidth: .infinity)
                } else if viewModel.sessions.isEmpty {
                    Text("Brak sesji treningowych.")
                        .font(VbtFont.body)
                        .foregroundStyle(VbtColor.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 48)
                } else {
                    ForEach(viewModel.sessions) { session in
                        NavigationLink {
                            SessionDetailScreen(apiClient: apiClient, sessionId: session.id)
                        } label: {
                            SessionRow(session: session)
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button("Usuń", role: .destructive) {
                                Task { await viewModel.deleteSession(session.id) }
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(VbtColor.background)
        .refreshable { await viewModel.loadSessions() }
    }
}

private struct SessionRow: View {
    let session: WorkoutSessionDto

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(String(session.startedAt.prefix(10)))
                    .font(VbtFont.bodyBold)
                    .foregroundStyle(VbtColor.textPrimary)
                if let duration = session.durationSeconds {
                    Text("\(duration / 60) min")
                        .font(VbtFont.caption)
                        .foregroundStyle(VbtColor.textSecondary)
                }
            }
            Spacer()
            Text("\(session.reps?.count ?? 0) reps")
                .font(VbtFont.caption)
                .foregroundStyle(VbtColor.teal)
            Image(systemName: "chevron.right")
                .foregroundStyle(VbtColor.textSecondary)
        }
        .padding(12)
        .background(VbtColor.surfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

private struct FilterChip: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(VbtFont.caption)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isSelected ? VbtColor.teal : VbtColor.surfaceVariant)
                .foregroundStyle(isSelected ? VbtColor.background : VbtColor.textPrimary)
                .clipShape(Capsule())
        }
    }
}
