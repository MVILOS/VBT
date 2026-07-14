import SwiftData

/// Pojedynczy `ModelContainer` dla całej appki - odpowiednik `VbtDatabase.kt` (Room).
enum PersistenceContainer {
    static let schema = Schema([
        ExerciseDefinitionModel.self,
        TrainingPlanModel.self,
        PlanExerciseModel.self,
        PlanSetModel.self,
        WorkoutSessionModel.self,
        SessionSetModel.self,
        RepResultModel.self
    ])

    static func make() -> ModelContainer {
        // swiftlint:disable:next force_try - brak sensownego fallbacku: bez kontenera appka nie działa,
        // identycznie jak crash Room przy błędnej migracji na Androidzie.
        try! ModelContainer(for: schema)
    }
}
