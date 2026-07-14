import Foundation

/// Port 1:1 z Android `WorkoutViewModel.kt` (sekcja "Data Classes").
enum WorkoutMode {
    case idle
    /// Coach wybiera zawodnika, dla którego nagrywa sesję.
    case sessionSelect
    /// Wybór ćwiczenia (freestyle) lub startu planu.
    case exercisePicker
    case active
    case finished
}

/// Sentinel "pseudo-ćwiczenie" na górze listy w pickerze - pozwala nagrać powtórzenia
/// z czujnika BLE bez przypisywania ich do konkretnego ćwiczenia z bazy. id = -1
/// (podobnie jak w Androidzie) sygnalizuje "brak ćwiczenia serwerowego".
let freeMeasurementExercise = ExerciseDto(
    id: -1,
    name: "Wolny pomiar (bez ćwiczenia)",
    category: "freeform",
    mvt: nil,
    description: "Swobodny pomiar prędkości/mocy bez przypisania do ćwiczenia z bazy."
)

struct CompletedSetSnapshot: Identifiable {
    let id = UUID()
    let setNumber: Int
    let exerciseName: String
    let loadKg: Float
    let reps: [RepResultDto]
}

struct WorkoutExerciseState {
    let exercise: ExerciseDto
    let sets: [PlanSetDto]
    var completedSets: Int = 0
    var additionalSets: Int = 0
}
