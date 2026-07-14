import SwiftUI

/// Port (skondensowany, patrz Kotlin dla pełnego stylu Vitruve) z Android
/// `PlanEditScreen.kt`: nazwa/opis planu, przypisanie do zawodnika, lista ćwiczeń
/// z seriami (reps/kg/rest), dodawanie/usuwanie ćwiczeń i serii.
struct PlanEditScreen: View {
    let apiClient: APIClient
    let planId: Int?

    @State private var viewModel: PlanEditViewModel?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if let viewModel {
                PlanEditContent(viewModel: viewModel, onSaved: { dismiss() })
            } else {
                VbtColor.background.ignoresSafeArea()
            }
        }
        .task {
            if viewModel == nil {
                let vm = PlanEditViewModel(apiClient: apiClient, planId: planId)
                viewModel = vm
                await vm.onAppear()
            }
        }
        .navigationTitle(planId == nil ? "Nowy Plan" : "Edytuj Plan")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct PlanEditContent: View {
    @Bindable var viewModel: PlanEditViewModel
    let onSaved: () -> Void
    @State private var showNewExerciseSheet = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                TextField("Nazwa planu", text: $viewModel.name)
                    .padding()
                    .background(VbtColor.surfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .foregroundStyle(VbtColor.textPrimary)

                TextField("Opis", text: $viewModel.description)
                    .padding()
                    .background(VbtColor.surfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .foregroundStyle(VbtColor.textPrimary)

                if !viewModel.availableAthletes.isEmpty {
                    Picker("Przypisz do", selection: $viewModel.assignedToId) {
                        Text("Nikt (szablon)").tag(Int?.none)
                        ForEach(viewModel.availableAthletes) { athlete in
                            Text(athlete.username).tag(Int?.some(athlete.id))
                        }
                    }
                    .tint(VbtColor.teal)
                }

                Toggle("Szablon", isOn: $viewModel.isTemplate)
                    .tint(VbtColor.teal)
                    .foregroundStyle(VbtColor.textPrimary)

                Divider().background(VbtColor.textSecondary.opacity(0.3))

                ForEach(Array(viewModel.exercises.enumerated()), id: \.element.id) { index, exercise in
                    ExerciseEditCard(
                        viewModel: viewModel,
                        exerciseIndex: index,
                        exercise: exercise,
                        onNewExercise: { showNewExerciseSheet = true }
                    )
                }

                Button("+ Dodaj ćwiczenie") {
                    viewModel.addExercise()
                }
                .buttonStyle(.bordered)
                .tint(VbtColor.teal)
                .frame(maxWidth: .infinity)

                Button("ZAPISZ PLAN") {
                    Task {
                        if await viewModel.savePlan() { onSaved() }
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(VbtColor.teal)
                .foregroundStyle(VbtColor.background)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .disabled(viewModel.isLoading)
            }
            .padding(16)
        }
        .background(VbtColor.background)
        .sheet(isPresented: $showNewExerciseSheet) {
            NewExerciseSheet { name, category in
                Task { await viewModel.createNewExercise(name: name, category: category) }
                showNewExerciseSheet = false
            }
        }
    }
}

private struct ExerciseEditCard: View {
    @Bindable var viewModel: PlanEditViewModel
    let exerciseIndex: Int
    let exercise: PlanExerciseState
    let onNewExercise: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Menu(exercise.exerciseName.isEmpty ? "Wybierz ćwiczenie" : exercise.exerciseName) {
                    ForEach(viewModel.availableExercises) { ex in
                        Button(ex.name) { viewModel.updateExercise(at: exerciseIndex, exerciseId: ex.id, exerciseName: ex.name) }
                    }
                    Button("+ Nowe ćwiczenie", action: onNewExercise)
                }
                .font(VbtFont.bodyBold)
                .foregroundStyle(VbtColor.teal)

                Spacer()
                Button {
                    viewModel.removeExercise(at: exerciseIndex)
                } label: {
                    Image(systemName: "trash").foregroundStyle(VbtColor.error)
                }
            }

            ForEach(Array(exercise.sets.enumerated()), id: \.element.id) { setIndex, set in
                HStack {
                    Text("Seria \(set.setNumber)")
                        .font(VbtFont.caption)
                        .foregroundStyle(VbtColor.textSecondary)
                        .frame(width: 60, alignment: .leading)
                    Stepper("\(set.reps) reps", value: Binding(
                        get: { viewModel.exercises[exerciseIndex].sets[setIndex].reps },
                        set: { viewModel.exercises[exerciseIndex].sets[setIndex].reps = $0 }
                    ), in: 1...50)
                    .font(VbtFont.caption)

                    TextField("kg", value: Binding(
                        get: { viewModel.exercises[exerciseIndex].sets[setIndex].loadKg },
                        set: { viewModel.exercises[exerciseIndex].sets[setIndex].loadKg = $0 }
                    ), format: .number)
                    .keyboardType(.decimalPad)
                    .frame(width: 50)
                    .foregroundStyle(VbtColor.textPrimary)

                    Button {
                        viewModel.removeSet(exerciseIndex: exerciseIndex, setIndex: setIndex)
                    } label: {
                        Image(systemName: "minus.circle").foregroundStyle(VbtColor.error)
                    }
                }
                .font(VbtFont.caption)
                .foregroundStyle(VbtColor.textPrimary)
            }

            Button("+ Seria") {
                viewModel.addSet(exerciseIndex: exerciseIndex)
            }
            .font(VbtFont.caption)
        }
        .padding(12)
        .background(VbtColor.surfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

private struct NewExerciseSheet: View {
    let onCreate: (String, String) -> Void
    @State private var name = ""
    @State private var category = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                TextField("Nazwa", text: $name)
                TextField("Kategoria", text: $category)
            }
            .navigationTitle("Nowe ćwiczenie")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Anuluj") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Dodaj") {
                        onCreate(name, category)
                        dismiss()
                    }
                    .disabled(name.isEmpty)
                }
            }
        }
    }
}
