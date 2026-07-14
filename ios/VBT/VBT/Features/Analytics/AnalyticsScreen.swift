import SwiftUI
import Charts

/// Port (uproszczony - bez zakładek zmęczenia/porównania tygodni) z Android
/// `AnalyticsScreen.kt`: wybór ćwiczenia (+ zawodnika dla coacha), trend prędkości
/// (wykres), progres 1RM, podsumowanie tygodniowego obciążenia.
struct AnalyticsScreen: View {
    let apiClient: APIClient

    @Environment(AuthRepository.self) private var authRepository
    @State private var viewModel: AnalyticsViewModel?

    var body: some View {
        Group {
            if let viewModel {
                AnalyticsContent(viewModel: viewModel)
            } else {
                VbtColor.background.ignoresSafeArea()
            }
        }
        .task {
            if viewModel == nil {
                let vm = AnalyticsViewModel(apiClient: apiClient, authRepository: authRepository)
                viewModel = vm
                await vm.loadInitialData()
            }
        }
        .navigationTitle("Analityka")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct AnalyticsContent: View {
    @Bindable var viewModel: AnalyticsViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if viewModel.isCoach && !viewModel.athletes.isEmpty {
                    Picker("Zawodnik", selection: Binding(
                        get: { viewModel.selectedAthleteId },
                        set: { if let id = $0 { Task { await viewModel.selectAthlete(id) } } }
                    )) {
                        ForEach(viewModel.athletes) { athlete in
                            Text(athlete.username).tag(Int?.some(athlete.id))
                        }
                    }
                    .tint(VbtColor.teal)
                }

                if !viewModel.weeklyLoad.isEmpty {
                    weeklyLoadSummary
                }

                Text("Ćwiczenie")
                    .font(VbtFont.caption)
                    .foregroundStyle(VbtColor.textSecondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(viewModel.exercises) { exercise in
                            Button(exercise.name) {
                                Task { await viewModel.selectExercise(exercise.id) }
                            }
                            .font(VbtFont.caption)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(viewModel.selectedExerciseId == exercise.id ? VbtColor.teal : VbtColor.surfaceVariant)
                            .foregroundStyle(viewModel.selectedExerciseId == exercise.id ? VbtColor.background : VbtColor.textPrimary)
                            .clipShape(Capsule())
                        }
                    }
                }

                if viewModel.isLoading {
                    ProgressView().tint(VbtColor.teal).frame(maxWidth: .infinity)
                }

                if !viewModel.velocityTrend.isEmpty {
                    velocityChart
                }

                if !viewModel.oneRmProgress.isEmpty {
                    oneRmSection
                }
            }
            .padding(16)
        }
        .background(VbtColor.background)
    }

    private var weeklyLoadSummary: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("OSTATNIE 8 TYGODNI")
                .font(VbtFont.caption)
                .foregroundStyle(VbtColor.textSecondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(viewModel.weeklyLoad, id: \.week) { week in
                        VStack {
                            Text(week.week).font(VbtFont.caption).foregroundStyle(VbtColor.textSecondary)
                            Text(String(format: "%.2f m/s", week.weekMeanVelocity))
                                .font(VbtFont.caption)
                                .foregroundStyle(VbtColor.teal)
                            Text("\(week.weekTotalReps) reps")
                                .font(VbtFont.caption)
                                .foregroundStyle(VbtColor.textSecondary)
                        }
                        .padding(8)
                        .background(VbtColor.surfaceVariant)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                }
            }
        }
    }

    private var velocityChart: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("TREND PRĘDKOŚCI")
                .font(VbtFont.caption)
                .foregroundStyle(VbtColor.textSecondary)
            Chart(viewModel.velocityTrend, id: \.date) { point in
                LineMark(x: .value("Data", point.date), y: .value("m/s", point.meanVelocity))
                    .foregroundStyle(VbtColor.teal)
                PointMark(x: .value("Data", point.date), y: .value("m/s", point.meanVelocity))
                    .foregroundStyle(VbtColor.teal)
            }
            .frame(height: 200)
            .chartXAxis(.hidden)
        }
    }

    private var oneRmSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("PROGRES 1RM")
                .font(VbtFont.caption)
                .foregroundStyle(VbtColor.textSecondary)
            ForEach(viewModel.oneRmProgress, id: \.date) { point in
                HStack {
                    Text(point.date).font(VbtFont.caption).foregroundStyle(VbtColor.textSecondary)
                    Spacer()
                    Text(String(format: "%.1f kg est. 1RM", point.estimated1rm))
                        .font(VbtFont.caption)
                        .foregroundStyle(VbtColor.purple)
                }
                .padding(8)
                .background(VbtColor.surfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 6))
            }
        }
    }
}
