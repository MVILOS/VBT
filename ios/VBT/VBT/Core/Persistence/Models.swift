import Foundation
import SwiftData

// Port 1:1 pól z Android Room `data/local/entity/*.kt`. Celowo BEZ jeszcze podłączonej
// logiki zapisu/odczytu w VM-ach (patrz WorkoutViewModel) - to jest warstwa modelu,
// okablowanie offline-first (crash-safe zapis podczas treningu + sync w tle) to
// kolejny krok, bo wymaga przeplecenia z każdym ekranem, który dziś woła API wprost.

@Model
final class ExerciseDefinitionModel {
    var name: String
    var category: String
    var defaultMinLiftVelocity: Float
    var defaultEndLiftVelocity: Float
    var defaultMinRepDistance: Float
    var isBuiltIn: Bool

    init(name: String, category: String, defaultMinLiftVelocity: Float, defaultEndLiftVelocity: Float, defaultMinRepDistance: Float, isBuiltIn: Bool = false) {
        self.name = name
        self.category = category
        self.defaultMinLiftVelocity = defaultMinLiftVelocity
        self.defaultEndLiftVelocity = defaultEndLiftVelocity
        self.defaultMinRepDistance = defaultMinRepDistance
        self.isBuiltIn = isBuiltIn
    }
}

@Model
final class TrainingPlanModel {
    var name: String
    var createdAt: Date
    var updatedAt: Date
    var notes: String?

    @Relationship(deleteRule: .cascade, inverse: \PlanExerciseModel.plan)
    var exercises: [PlanExerciseModel] = []

    init(name: String, createdAt: Date = .now, updatedAt: Date = .now, notes: String? = nil) {
        self.name = name
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.notes = notes
    }
}

@Model
final class PlanExerciseModel {
    var plan: TrainingPlanModel?
    var exercise: ExerciseDefinitionModel?
    var orderIndex: Int
    var notes: String?

    @Relationship(deleteRule: .cascade, inverse: \PlanSetModel.planExercise)
    var sets: [PlanSetModel] = []

    init(plan: TrainingPlanModel?, exercise: ExerciseDefinitionModel?, orderIndex: Int, notes: String? = nil) {
        self.plan = plan
        self.exercise = exercise
        self.orderIndex = orderIndex
        self.notes = notes
    }
}

@Model
final class PlanSetModel {
    var planExercise: PlanExerciseModel?
    var setNumber: Int
    var targetLoadKg: Float
    var targetReps: Int?
    var targetRpe: Float?
    var notes: String?

    init(planExercise: PlanExerciseModel?, setNumber: Int, targetLoadKg: Float, targetReps: Int? = nil, targetRpe: Float? = nil, notes: String? = nil) {
        self.planExercise = planExercise
        self.setNumber = setNumber
        self.targetLoadKg = targetLoadKg
        self.targetReps = targetReps
        self.targetRpe = targetRpe
        self.notes = notes
    }
}

@Model
final class WorkoutSessionModel {
    var plan: TrainingPlanModel?
    var startedAt: Date
    var finishedAt: Date?
    var athleteName: String?
    var notes: String?
    /// "active" = w toku, "finished" = zapisana, "discarded" = usunięta
    var status: String
    /// ID sesji nadane przez serwer po synchronizacji live
    var serverSessionId: Int?
    var athleteServerId: Int?
    /// Tętno z pasa/opaski HR - tylko lokalnie, backend nie ma jeszcze pól HR
    var avgHeartRate: Int?
    var maxHeartRate: Int?

    @Relationship(deleteRule: .cascade, inverse: \SessionSetModel.session)
    var sets: [SessionSetModel] = []

    init(plan: TrainingPlanModel?, startedAt: Date = .now, status: String = "active", athleteServerId: Int? = nil) {
        self.plan = plan
        self.startedAt = startedAt
        self.status = status
        self.athleteServerId = athleteServerId
    }
}

@Model
final class SessionSetModel {
    var session: WorkoutSessionModel?
    var exercise: ExerciseDefinitionModel?
    var planSet: PlanSetModel?
    var setNumber: Int
    var actualLoadKg: Float
    var startedAt: Date
    var finishedAt: Date?
    var isCompleted: Bool

    @Relationship(deleteRule: .cascade, inverse: \RepResultModel.sessionSet)
    var reps: [RepResultModel] = []

    init(session: WorkoutSessionModel?, exercise: ExerciseDefinitionModel?, planSet: PlanSetModel? = nil, setNumber: Int, actualLoadKg: Float, startedAt: Date = .now, isCompleted: Bool = false) {
        self.session = session
        self.exercise = exercise
        self.planSet = planSet
        self.setNumber = setNumber
        self.actualLoadKg = actualLoadKg
        self.startedAt = startedAt
        self.isCompleted = isCompleted
    }
}

@Model
final class RepResultModel {
    var sessionSet: SessionSetModel?
    var repNumber: Int
    /// Średnia prędkość koncentryczna (mean velocity) - nazwa pola zachowana
    /// zgodnie z historyczną kolumną Room dla spójności między platformami.
    var maxVelocityMs: Float
    /// Prędkość szczytowa (peak); 0 dla rekordów sprzed migracji / starego firmware.
    var peakVelocityMs: Float
    var distanceM: Float
    var durationMs: Int
    var powerW: Float
    var deviceRepIndex: Int
    var deviceTimestamp: Int64
    var recordedAt: Date
    var isDeleted: Bool

    init(sessionSet: SessionSetModel?, repNumber: Int, maxVelocityMs: Float, peakVelocityMs: Float = 0, distanceM: Float, durationMs: Int, powerW: Float, deviceRepIndex: Int, deviceTimestamp: Int64, recordedAt: Date = .now, isDeleted: Bool = false) {
        self.sessionSet = sessionSet
        self.repNumber = repNumber
        self.maxVelocityMs = maxVelocityMs
        self.peakVelocityMs = peakVelocityMs
        self.distanceM = distanceM
        self.durationMs = durationMs
        self.powerW = powerW
        self.deviceRepIndex = deviceRepIndex
        self.deviceTimestamp = deviceTimestamp
        self.recordedAt = recordedAt
        self.isDeleted = isDeleted
    }
}
