import Foundation

// Port 1:1 z Android `data/remote/ApiModels.kt`. Nazwy pól po stronie Swift są camelCase,
// mapowanie na snake_case JSON-a serwera idzie przez CodingKeys (tak jak @SerializedName w Gson).

// MARK: - Authentication

struct LoginRequest: Encodable {
    var username: String?
    var email: String?
    var password: String
}

struct RegisterRequest: Encodable {
    var username: String
    var password: String
}

struct LoginResponse: Decodable {
    let accessToken: String
    let tokenType: String
    let user: UserDto

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case tokenType = "token_type"
        case user
    }
}

// MARK: - User / Athlete

struct UserDto: Codable, Identifiable {
    let id: Int
    let email: String?
    let username: String
    let role: String
    let isActive: Bool
    let coachId: Int?

    enum CodingKeys: String, CodingKey {
        case id, email, username, role
        case isActive = "is_active"
        case coachId = "coach_id"
    }
}

// MARK: - Exercise

struct ExerciseDto: Codable, Identifiable {
    let id: Int
    let name: String
    let category: String?
    let mvt: Float?
    let description: String?
}

struct CreateExerciseRequest: Encodable {
    var name: String
    var category: String?
    var mvt: Float?
    var description: String?
}

// MARK: - Training Plan

struct TrainingPlanDto: Codable, Identifiable {
    let id: Int
    let name: String
    let description: String?
    let ownerId: Int?
    let assignedTo: Int?
    let isTemplate: Bool
    let exercises: [PlanExerciseDto]

    enum CodingKeys: String, CodingKey {
        case id, name, description, exercises
        case ownerId = "owner_id"
        case assignedTo = "assigned_to"
        case isTemplate = "is_template"
    }
}

struct CreatePlanRequest: Encodable {
    var name: String
    var description: String?
    var assignedTo: Int?
    var isTemplate: Bool
    var exercises: [PlanExerciseDto]

    enum CodingKeys: String, CodingKey {
        case name, description, exercises
        case assignedTo = "assigned_to"
        case isTemplate = "is_template"
    }
}

struct PlanExerciseDto: Codable, Identifiable {
    var id: Int?
    let exerciseId: Int
    let orderIndex: Int
    let notes: String?
    let sets: [PlanSetDto]
    let exercise: ExerciseDto?

    enum CodingKeys: String, CodingKey {
        case id, notes, sets, exercise
        case exerciseId = "exercise_id"
        case orderIndex = "order_index"
    }
}

struct PlanSetDto: Codable, Identifiable {
    var id: Int?
    let setNumber: Int
    let reps: Int
    let loadKg: Float
    let loadPercent1rm: Float?
    let targetVelocityMin: Float?
    let targetVelocityMax: Float?
    let restSeconds: Int

    enum CodingKeys: String, CodingKey {
        case id, reps
        case setNumber = "set_number"
        case loadKg = "load_kg"
        case loadPercent1rm = "load_percent_1rm"
        case targetVelocityMin = "target_velocity_min"
        case targetVelocityMax = "target_velocity_max"
        case restSeconds = "rest_seconds"
    }
}

// MARK: - Calendar

struct CalendarEntryDto: Codable, Identifiable {
    let id: Int
    let athleteId: Int
    let planId: Int?
    let date: String
    let timeSlot: String?
    let title: String
    let notes: String?
    let status: String

    enum CodingKeys: String, CodingKey {
        case id, date, title, notes, status
        case athleteId = "athlete_id"
        case planId = "plan_id"
        case timeSlot = "time_slot"
    }
}

struct CreateCalendarEntryRequest: Encodable {
    var athleteId: Int?
    var planId: Int?
    var date: String
    var timeSlot: String?
    var title: String
    var notes: String?

    enum CodingKeys: String, CodingKey {
        case date, title, notes
        case athleteId = "athlete_id"
        case planId = "plan_id"
        case timeSlot = "time_slot"
    }
}

struct UpdateCalendarEntryRequest: Encodable {
    var title: String?
    var notes: String?
    var date: String?
    var timeSlot: String?
    var status: String?
    var planId: Int?

    enum CodingKeys: String, CodingKey {
        case title, notes, date, status
        case timeSlot = "time_slot"
        case planId = "plan_id"
    }
}

// MARK: - Workout Session

struct WorkoutSessionDto: Codable, Identifiable {
    let id: Int
    let athleteId: Int
    let planId: Int?
    let calendarEntryId: Int?
    let startedAt: String
    let finishedAt: String?
    let durationSeconds: Int?
    let notes: String?
    let reps: [RepResultDto]?

    enum CodingKeys: String, CodingKey {
        case id, notes, reps
        case athleteId = "athlete_id"
        case planId = "plan_id"
        case calendarEntryId = "calendar_entry_id"
        case startedAt = "started_at"
        case finishedAt = "finished_at"
        case durationSeconds = "duration_seconds"
    }
}

struct CreateSessionRequest: Encodable {
    var athleteId: Int
    var planId: Int?
    var calendarEntryId: Int?
    var notes: String?
    var reps: [RepResultDto]

    enum CodingKeys: String, CodingKey {
        case notes, reps
        case athleteId = "athlete_id"
        case planId = "plan_id"
        case calendarEntryId = "calendar_entry_id"
    }
}

struct StartLiveSessionRequest: Encodable {
    var athleteId: Int?
    var planId: Int?
    var notes: String?

    enum CodingKeys: String, CodingKey {
        case notes
        case athleteId = "athlete_id"
        case planId = "plan_id"
    }
}

struct AppendRepsRequest: Encodable {
    var reps: [RepResultDto]
    var finishedAt: String?

    enum CodingKeys: String, CodingKey {
        case reps
        case finishedAt = "finished_at"
    }
}

// MARK: - Rep Result

struct RepResultDto: Codable, Identifiable {
    var id: Int?
    var sessionId: Int?
    let exerciseId: Int
    let setNumber: Int
    let repNumber: Int
    let meanVelocity: Double
    let peakVelocity: Double
    let loadKg: Double
    let powerWatts: Double?
    let estimated1rm: Double?
    let timestamp: String?

    enum CodingKeys: String, CodingKey {
        case id, timestamp
        case sessionId = "session_id"
        case exerciseId = "exercise_id"
        case setNumber = "set_number"
        case repNumber = "rep_number"
        case meanVelocity = "mean_velocity"
        case peakVelocity = "peak_velocity"
        case loadKg = "load_kg"
        case powerWatts = "power_watts"
        case estimated1rm = "estimated_1rm"
    }
}

struct UpdateRepRequest: Encodable {
    var loadKg: Double?
    var setNumber: Int?
    var repNumber: Int?
    var estimated1rm: Double?

    enum CodingKeys: String, CodingKey {
        case loadKg = "load_kg"
        case setNumber = "set_number"
        case repNumber = "rep_number"
        case estimated1rm = "estimated_1rm"
    }
}

// MARK: - Velocity Trace

struct VelocityPointDto: Codable {
    let timestampMs: Int64
    let velocityMs: Float

    enum CodingKeys: String, CodingKey {
        case timestampMs = "timestamp_ms"
        case velocityMs = "velocity_ms"
    }
}

struct VelocityTraceRequest: Encodable {
    var points: [VelocityPointDto]
}

// MARK: - Analytics

struct VelocityTrendPointDto: Decodable {
    let date: String
    let meanVelocity: Double
    let loadKg: Double
    let exerciseName: String

    enum CodingKeys: String, CodingKey {
        case date
        case meanVelocity = "mean_velocity"
        case loadKg = "load_kg"
        case exerciseName = "exercise_name"
    }
}

struct OneRmProgressPointDto: Decodable {
    let date: String
    let estimated1rm: Double
    let loadKg: Double

    enum CodingKeys: String, CodingKey {
        case date
        case estimated1rm = "estimated_1rm"
        case loadKg = "load_kg"
    }
}

struct FatigueSetDto: Decodable {
    let setNumber: Int
    let reps: Int
    let meanVelocity: Double
    let peakVelocity: Double
    let loadKg: Double

    enum CodingKeys: String, CodingKey {
        case reps
        case setNumber = "set_number"
        case meanVelocity = "mean_velocity"
        case peakVelocity = "peak_velocity"
        case loadKg = "load_kg"
    }
}

struct FatigueIndexDto: Decodable {
    let exerciseId: Int
    let exerciseName: String
    let sets: [FatigueSetDto]
    let fatigueIndexPct: Double
    let velocityDropMs: Double
    let bestSet: Int
    /// optimal / moderate / high / overreached
    let readinessZone: String

    enum CodingKeys: String, CodingKey {
        case sets
        case exerciseId = "exercise_id"
        case exerciseName = "exercise_name"
        case fatigueIndexPct = "fatigue_index_pct"
        case velocityDropMs = "velocity_drop_ms"
        case bestSet = "best_set"
        case readinessZone = "readiness_zone"
    }
}

struct WeeklyDayDto: Decodable {
    let date: String
    let sessions: Int
    let totalReps: Int
    let meanVelocity: Double
    let totalVolumeKg: Double

    enum CodingKeys: String, CodingKey {
        case date, sessions
        case totalReps = "total_reps"
        case meanVelocity = "mean_velocity"
        case totalVolumeKg = "total_volume_kg"
    }
}

struct WeeklyLoadDto: Decodable {
    let week: String
    let trainingDays: Int
    let weekMeanVelocity: Double
    let weekTotalReps: Int
    let weekTotalVolumeKg: Double
    let weeklyFatiguePct: Double
    let days: [WeeklyDayDto]

    enum CodingKeys: String, CodingKey {
        case week, days
        case trainingDays = "training_days"
        case weekMeanVelocity = "week_mean_velocity"
        case weekTotalReps = "week_total_reps"
        case weekTotalVolumeKg = "week_total_volume_kg"
        case weeklyFatiguePct = "weekly_fatigue_pct"
    }
}

struct WeekComparisonDto: Decodable {
    let week: String
    let sessions: Int
    let totalReps: Int
    let meanVelocity: Double
    let maxVelocity: Double
    let meanPeakVelocity: Double
    let meanLoadKg: Double
    let maxLoadKg: Double
    let totalVolumeKg: Double
    let bestEstimated1rm: Double?

    enum CodingKeys: String, CodingKey {
        case week, sessions
        case totalReps = "total_reps"
        case meanVelocity = "mean_velocity"
        case maxVelocity = "max_velocity"
        case meanPeakVelocity = "mean_peak_velocity"
        case meanLoadKg = "mean_load_kg"
        case maxLoadKg = "max_load_kg"
        case totalVolumeKg = "total_volume_kg"
        case bestEstimated1rm = "best_estimated_1rm"
    }
}

// MARK: - User management

struct CreateAthleteRequest: Encodable {
    var username: String
    var email: String?
    var password: String
    var role: String = "athlete"
}

struct AssignByUsernameRequest: Encodable {
    var username: String
}

struct AdminAssignRequest: Encodable {
    var athleteUsername: String
    var coachUsername: String

    enum CodingKeys: String, CodingKey {
        case athleteUsername = "athlete_username"
        case coachUsername = "coach_username"
    }
}

// MARK: - Dashboard

struct DashboardStatsDto: Decodable {
    let totalAthletes: Int
    let activePlans: Int
    let sessionsThisWeek: Int

    enum CodingKeys: String, CodingKey {
        case totalAthletes = "total_athletes"
        case activePlans = "active_plans"
        case sessionsThisWeek = "sessions_this_week"
    }
}

struct RecentSessionDto: Decodable, Identifiable {
    let id: Int
    let athleteId: Int
    let athleteName: String
    let startedAt: String
    let durationSeconds: Int?
    let repsCount: Int

    enum CodingKeys: String, CodingKey {
        case id
        case athleteId = "athlete_id"
        case athleteName = "athlete_name"
        case startedAt = "started_at"
        case durationSeconds = "duration_seconds"
        case repsCount = "reps_count"
    }
}
