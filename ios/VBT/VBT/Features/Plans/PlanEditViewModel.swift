import Foundation
import Observation

/// Port 1:1 z Android `PlanEditViewModel.kt`.
struct PlanExerciseState: Identifiable {
    let id = UUID()
    var exerciseId = 0
    var exerciseName = ""
    var orderIndex = 0
    var notes = ""
    var sets: [PlanSetState] = [PlanSetState()]
}

struct PlanSetState: Identifiable {
    let id = UUID()
    var setNumber = 1
    var reps = 5
    var loadKg: Float = 100
    var restSeconds = 180
    var targetVelMin: Float?
    var targetVelMax: Float?
}

@Observable
final class PlanEditViewModel {
    private(set) var planId: Int?
    var name = ""
    var description = ""
    var assignedToId: Int?
    var isTemplate = false
    private(set) var exercises: [PlanExerciseState] = []
    private(set) var availableExercises: [ExerciseDto] = []
    private(set) var availableAthletes: [UserDto] = []
    private(set) var isLoading = false
    private(set) var isSaved = false
    var error: String?

    private let apiClient: APIClient

    init(apiClient: APIClient, planId: Int?) {
        self.apiClient = apiClient
        self.planId = planId
    }

    @MainActor
    func onAppear() async {
        await loadExercisesAndAthletes()
        if let planId {
            await loadPlan(planId)
        }
    }

    @MainActor
    func loadPlan(_ planId: Int) async {
        isLoading = true
        error = nil
        do {
            let plan: TrainingPlanDto = try await apiClient.send(.getPlan(id: planId))
            self.planId = plan.id
            name = plan.name
            description = plan.description ?? ""
            assignedToId = plan.assignedTo
            isTemplate = plan.isTemplate
            exercises = plan.exercises.map { pe in
                PlanExerciseState(
                    exerciseId: pe.exerciseId,
                    exerciseName: pe.exercise?.name ?? "",
                    orderIndex: pe.orderIndex,
                    notes: pe.notes ?? "",
                    sets: pe.sets.map { s in
                        PlanSetState(setNumber: s.setNumber, reps: s.reps, loadKg: s.loadKg, restSeconds: s.restSeconds, targetVelMin: s.targetVelocityMin, targetVelMax: s.targetVelocityMax)
                    }
                )
            }
        } catch {
            self.error = "Nie udało się wczytać planu"
        }
        isLoading = false
    }

    @MainActor
    func loadExercisesAndAthletes() async {
        do {
            availableExercises = try await apiClient.send(.getExercises())
        } catch { /* opcjonalne */ }
        do {
            availableAthletes = try await apiClient.send(.getAthletes())
        } catch { /* opcjonalne, niektórzy usera nie są coachem */ }
    }

    func addExercise() {
        exercises.append(PlanExerciseState(orderIndex: exercises.count))
    }

    func removeExercise(at index: Int) {
        guard exercises.indices.contains(index) else { return }
        exercises.remove(at: index)
        for i in exercises.indices { exercises[i].orderIndex = i }
    }

    func updateExercise(at index: Int, exerciseId: Int, exerciseName: String) {
        guard exercises.indices.contains(index) else { return }
        exercises[index].exerciseId = exerciseId
        exercises[index].exerciseName = exerciseName
    }

    func addSet(exerciseIndex: Int) {
        guard exercises.indices.contains(exerciseIndex) else { return }
        let newSetNumber = (exercises[exerciseIndex].sets.map(\.setNumber).max() ?? 0) + 1
        exercises[exerciseIndex].sets.append(PlanSetState(setNumber: newSetNumber))
    }

    func removeSet(exerciseIndex: Int, setIndex: Int) {
        guard exercises.indices.contains(exerciseIndex), exercises[exerciseIndex].sets.indices.contains(setIndex) else { return }
        exercises[exerciseIndex].sets.remove(at: setIndex)
        for i in exercises[exerciseIndex].sets.indices { exercises[exerciseIndex].sets[i].setNumber = i + 1 }
    }

    @MainActor
    func createNewExercise(name: String, category: String) async {
        do {
            let request = CreateExerciseRequest(name: name, category: category, mvt: nil, description: nil)
            let newExercise: ExerciseDto = try await apiClient.send(.createExercise(request))
            availableExercises.append(newExercise)
        } catch {
            self.error = "Nie udało się utworzyć ćwiczenia"
        }
    }

    @MainActor
    func savePlan() async -> Bool {
        isLoading = true
        error = nil
        let planExerciseDtos = exercises.map { exercise in
            PlanExerciseDto(
                id: nil,
                exerciseId: exercise.exerciseId,
                orderIndex: exercise.orderIndex,
                notes: exercise.notes.isEmpty ? nil : exercise.notes,
                sets: exercise.sets.map { set in
                    PlanSetDto(id: nil, setNumber: set.setNumber, reps: set.reps, loadKg: set.loadKg, loadPercent1rm: nil, targetVelocityMin: set.targetVelMin, targetVelocityMax: set.targetVelMax, restSeconds: set.restSeconds)
                },
                exercise: nil
            )
        }
        let request = CreatePlanRequest(
            name: name.isEmpty ? "New Plan" : name,
            description: description.isEmpty ? nil : description,
            assignedTo: assignedToId,
            isTemplate: isTemplate,
            exercises: planExerciseDtos
        )

        do {
            let saved: TrainingPlanDto
            if let planId {
                saved = try await apiClient.send(.updatePlan(id: planId, request))
            } else {
                saved = try await apiClient.send(.createPlan(request))
            }
            planId = saved.id
            isSaved = true
            isLoading = false
            return true
        } catch {
            self.error = "Nie udało się zapisać planu"
            isLoading = false
            return false
        }
    }
}
