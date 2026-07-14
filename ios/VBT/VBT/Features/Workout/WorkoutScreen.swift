import SwiftUI

/// Port (uproszczony pod względem trwałości - patrz komentarz w `WorkoutViewModel`) z
/// Android `WorkoutScreen.kt`: SessionSelect (coach) -> ExercisePicker (freestyle/plan) ->
/// Active (live velocity + rep list + kontrolki) -> Finished.
struct WorkoutScreen: View {
    let apiClient: APIClient

    @Environment(AuthRepository.self) private var authRepository
    @Environment(VbtBleManager.self) private var bleManager
    @Environment(HeartRateManager.self) private var heartRateManager
    @State private var viewModel: WorkoutViewModel?

    var body: some View {
        Group {
            if let viewModel {
                WorkoutContent(viewModel: viewModel)
            } else {
                VbtColor.background.ignoresSafeArea()
            }
        }
        .onAppear {
            if viewModel == nil {
                let vm = WorkoutViewModel(apiClient: apiClient, authRepository: authRepository, bleManager: bleManager)
                viewModel = vm
                vm.startSession()
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .background(VbtColor.background)
    }
}

private struct WorkoutContent: View {
    let viewModel: WorkoutViewModel
    @Environment(VbtBleManager.self) private var bleManager
    @Environment(HeartRateManager.self) private var heartRateManager

    var body: some View {
        Group {
            switch viewModel.mode {
            case .idle:
                ProgressView().tint(VbtColor.teal)
            case .sessionSelect:
                SessionSelectView(viewModel: viewModel)
            case .exercisePicker:
                ExercisePickerView(viewModel: viewModel)
            case .active:
                ActiveWorkoutView(viewModel: viewModel)
            case .finished:
                FinishedView(viewModel: viewModel)
            }
        }
        .onChange(of: heartRateManager.heartRate) { _, hr in
            viewModel.recordHeartRateSample(hr)
        }
        .alert("Błąd", isPresented: Binding(get: { viewModel.error != nil }, set: { if !$0 { viewModel.error = nil } })) {
            Button("OK", role: .cancel) { viewModel.error = nil }
        } message: {
            Text(viewModel.error ?? "")
        }
    }
}

// MARK: - Session select (coach)

private struct SessionSelectView: View {
    let viewModel: WorkoutViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Dla kogo trening?")
                    .font(VbtFont.headline)
                    .foregroundStyle(VbtColor.textPrimary)

                Button {
                    viewModel.selectSessionAthlete(nil)
                } label: {
                    Text("Dla siebie")
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                }
                .buttonStyle(.borderedProminent)
                .tint(VbtColor.teal)
                .foregroundStyle(VbtColor.background)

                Text("Zawodnicy")
                    .font(VbtFont.caption)
                    .foregroundStyle(VbtColor.textSecondary)
                    .padding(.top, 8)

                ForEach(viewModel.availableAthletes) { athlete in
                    Button {
                        viewModel.selectSessionAthlete(athlete)
                    } label: {
                        Text(athlete.username)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(VbtColor.surfaceVariant)
                            .foregroundStyle(VbtColor.textPrimary)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                }
            }
            .padding(16)
        }
    }
}

// MARK: - Exercise picker (freestyle + plan)

private struct ExercisePickerView: View {
    let viewModel: WorkoutViewModel
    @State private var selectedExercise: ExerciseDto?
    @State private var loadKg: Float = 20

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let plan = viewModel.selectedPlan {
                    Text("Plan: \(plan.name)")
                        .font(VbtFont.headline)
                        .foregroundStyle(VbtColor.textPrimary)
                }

                Text("Wolny pomiar / ćwiczenie")
                    .font(VbtFont.title)
                    .foregroundStyle(VbtColor.textPrimary)

                ForEach(viewModel.availableExercises, id: \.id) { exercise in
                    Button {
                        selectedExercise = exercise
                    } label: {
                        HStack {
                            Text(exercise.name)
                                .foregroundStyle(VbtColor.textPrimary)
                            Spacer()
                            if selectedExercise?.id == exercise.id {
                                Image(systemName: "checkmark").foregroundStyle(VbtColor.teal)
                            }
                        }
                        .padding(12)
                        .background(VbtColor.surfaceVariant)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                }

                if selectedExercise != nil {
                    LoadStepper(loadKg: $loadKg)

                    Button {
                        if let ex = selectedExercise {
                            viewModel.startFreestyle(exercise: ex, loadKg: loadKg)
                        }
                    } label: {
                        Text("START")
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(VbtColor.teal)
                    .foregroundStyle(VbtColor.background)
                }
            }
            .padding(16)
        }
        .task { await viewModel.loadAvailableExercises() }
    }
}

/// Numpad-styl kg z Vitruve: duże przyciski +2.5/+5/+10/+20/+25.
private struct LoadStepper: View {
    @Binding var loadKg: Float
    private let increments: [Float] = [2.5, 5, 10, 20, 25]

    var body: some View {
        VStack(spacing: 12) {
            Text("\(loadKg, specifier: "%.1f") kg")
                .font(VbtFont.headline)
                .foregroundStyle(VbtColor.teal)

            HStack(spacing: 8) {
                ForEach(increments, id: \.self) { inc in
                    Button("+\(inc, specifier: "%.1g")") {
                        loadKg += inc
                    }
                    .buttonStyle(.bordered)
                    .tint(VbtColor.teal)
                }
                Button("Reset") { loadKg = 0 }
                    .buttonStyle(.bordered)
                    .tint(VbtColor.error)
            }
        }
        .padding(12)
        .background(VbtColor.surfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - Active workout

private struct ActiveWorkoutView: View {
    let viewModel: WorkoutViewModel
    @Environment(VbtBleManager.self) private var bleManager
    @Environment(HeartRateManager.self) private var heartRateManager

    private var velocityZone: VelocityZone { GetVelocityZoneUseCase()(bleManager.liveVelocity) }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                connectionBanner

                Text(viewModel.currentExerciseName)
                    .font(VbtFont.title)
                    .foregroundStyle(VbtColor.textPrimary)
                Text("\(viewModel.currentLoadKg, specifier: "%.1f") kg")
                    .font(VbtFont.body)
                    .foregroundStyle(VbtColor.textSecondary)

                VStack(spacing: 4) {
                    Text(String(format: "%.2f", bleManager.liveVelocity))
                        .font(VbtFont.velocityDigit)
                        .foregroundStyle(bleManager.isLifting ? Color(hex: 0xFF9800) : VbtColor.teal)
                    Text("m/s")
                        .font(VbtFont.caption)
                        .foregroundStyle(VbtColor.textSecondary)
                    Text(velocityZone.label)
                        .font(VbtFont.bodyBold)
                        .foregroundStyle(velocityZone.color)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)
                .background(VbtColor.surface)
                .clipShape(RoundedRectangle(cornerRadius: 16))

                if let hr = heartRateManager.heartRate {
                    Label("\(hr) BPM", systemImage: "heart.fill")
                        .foregroundStyle(VbtColor.error)
                }

                if let countdown = viewModel.autoFinishCountdown {
                    HStack {
                        Text("Seria zakończy się za \(countdown)s")
                            .foregroundStyle(VbtColor.textSecondary)
                        Button("Anuluj") { viewModel.cancelAutoFinish() }
                            .buttonStyle(.bordered)
                    }
                }

                if !viewModel.completedRepsInSet.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("POWTÓRZENIA")
                            .font(VbtFont.caption)
                            .foregroundStyle(VbtColor.textSecondary)
                        ForEach(Array(viewModel.completedRepsInSet.enumerated()), id: \.element.id) { index, rep in
                            HStack {
                                Text("#\(index + 1)")
                                    .foregroundStyle(VbtColor.textSecondary)
                                Spacer()
                                Text(String(format: "mean %.2f m/s", rep.dto.meanVelocity))
                                Spacer()
                                Text(String(format: "peak %.2f m/s", rep.dto.peakVelocity))
                                    .foregroundStyle(VbtColor.teal)
                            }
                            .font(VbtFont.caption)
                            .foregroundStyle(VbtColor.textPrimary)
                            .padding(8)
                            .background(VbtColor.surfaceVariant)
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                        }
                    }
                }

                if viewModel.isPaused {
                    Text("Zmień obciążenie")
                        .font(VbtFont.bodyBold)
                        .foregroundStyle(VbtColor.background)
                        .frame(maxWidth: .infinity)
                        .padding(12)
                        .background(VbtColor.purple)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }

                VStack(spacing: 8) {
                    HStack(spacing: 8) {
                        Button(viewModel.isPaused ? "Wznów" : "Pauza") {
                            viewModel.togglePause()
                        }
                        .buttonStyle(.bordered)
                        .frame(maxWidth: .infinity)

                        Button("Zmień ćwiczenie") {
                            viewModel.requestExerciseChange()
                        }
                        .buttonStyle(.bordered)
                        .frame(maxWidth: .infinity)
                    }

                    Button("Zakończ serię") {
                        viewModel.finishSet()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(VbtColor.teal)
                    .foregroundStyle(VbtColor.background)
                    .frame(maxWidth: .infinity)

                    Button("ZAKOŃCZ TRENING", role: .destructive) {
                        viewModel.finishWorkout()
                    }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(16)
        }
        .sheet(isPresented: Binding(get: { viewModel.showChangeExercise }, set: { if !$0 { viewModel.cancelExerciseChange() } })) {
            ChangeExerciseSheet(viewModel: viewModel)
        }
    }

    @ViewBuilder
    private var connectionBanner: some View {
        if bleManager.connectionState != .connected {
            HStack {
                Text(bannerLabel)
                    .font(VbtFont.caption)
                    .foregroundStyle(VbtColor.error)
                Spacer()
                if bleManager.connectionState == .disconnected {
                    Button("Połącz ponownie") { viewModel.reconnectBle() }
                        .font(VbtFont.caption)
                }
            }
            .padding(8)
            .background(VbtColor.error.opacity(0.15))
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }

    private var bannerLabel: String {
        switch bleManager.connectionState {
        case .reconnecting: return "Łączenie ponowne z czujnikiem..."
        case .disconnected: return "Czujnik rozłączony"
        default: return "Czujnik: \(bleManager.connectionState)"
        }
    }
}

private struct ChangeExerciseSheet: View {
    let viewModel: WorkoutViewModel
    @State private var selectedExercise: ExerciseDto?
    @State private var loadKg: Float = 20

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(viewModel.availableExercises, id: \.id) { exercise in
                        Button {
                            selectedExercise = exercise
                        } label: {
                            Text(exercise.name)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(12)
                                .background(VbtColor.surfaceVariant)
                                .foregroundStyle(VbtColor.textPrimary)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                    }
                    if selectedExercise != nil {
                        LoadStepper(loadKg: $loadKg)
                    }
                }
                .padding(16)
            }
            .background(VbtColor.background)
            .navigationTitle("Zmień ćwiczenie")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Anuluj") { viewModel.cancelExerciseChange() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Zmień") {
                        if let ex = selectedExercise {
                            viewModel.changeExercise(ex, loadKg: loadKg)
                        }
                    }
                    .disabled(selectedExercise == nil)
                }
            }
        }
    }
}

// MARK: - Finished

private struct FinishedView: View {
    let viewModel: WorkoutViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(VbtColor.success)
            Text("Trening zakończony")
                .font(VbtFont.headline)
                .foregroundStyle(VbtColor.textPrimary)

            Text("\(viewModel.completedSets.count) serii, \(viewModel.allReps.count) powtórzeń")
                .font(VbtFont.body)
                .foregroundStyle(VbtColor.textSecondary)

            if let avg = viewModel.averageHeartRate, let max = viewModel.maxHeartRate {
                Text("Śr. tętno \(avg) BPM, max \(max) BPM")
                    .font(VbtFont.caption)
                    .foregroundStyle(VbtColor.textSecondary)
            }

            Button("Zakończ") { dismiss() }
                .buttonStyle(.borderedProminent)
                .tint(VbtColor.teal)
                .foregroundStyle(VbtColor.background)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(VbtColor.background)
    }
}
