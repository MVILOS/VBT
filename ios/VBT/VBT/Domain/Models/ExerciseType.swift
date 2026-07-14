import Foundation

/// Port 1:1 z Android `domain/model/ExerciseType.kt`.
enum ExerciseType: String, CaseIterable {
    case backSquat, frontSquat, benchPress, overheadPress, deadlift, romanianDeadlift, custom

    struct Defaults {
        let displayName: String
        let category: String
        let minLiftVelocity: Float
        let endLiftVelocity: Float
        let minRepDistance: Float
        /// Minimum Velocity Threshold — używane do estymacji 1RM.
        let mvt: Float
    }

    var defaults: Defaults {
        switch self {
        case .backSquat:
            return Defaults(displayName: "Back Squat", category: "squat", minLiftVelocity: 0.08, endLiftVelocity: 0.04, minRepDistance: 0.20, mvt: 0.30)
        case .frontSquat:
            return Defaults(displayName: "Front Squat", category: "squat", minLiftVelocity: 0.08, endLiftVelocity: 0.04, minRepDistance: 0.20, mvt: 0.30)
        case .benchPress:
            return Defaults(displayName: "Bench Press", category: "bench", minLiftVelocity: 0.12, endLiftVelocity: 0.05, minRepDistance: 0.15, mvt: 0.17)
        case .overheadPress:
            return Defaults(displayName: "Overhead Press", category: "ohp", minLiftVelocity: 0.10, endLiftVelocity: 0.05, minRepDistance: 0.20, mvt: 0.20)
        case .deadlift:
            return Defaults(displayName: "Deadlift", category: "deadlift", minLiftVelocity: 0.08, endLiftVelocity: 0.04, minRepDistance: 0.30, mvt: 0.15)
        case .romanianDeadlift:
            return Defaults(displayName: "Romanian Deadlift", category: "deadlift", minLiftVelocity: 0.06, endLiftVelocity: 0.03, minRepDistance: 0.20, mvt: 0.15)
        case .custom:
            return Defaults(displayName: "Custom", category: "custom", minLiftVelocity: 0.10, endLiftVelocity: 0.05, minRepDistance: 0.10, mvt: 0.25)
        }
    }
}
