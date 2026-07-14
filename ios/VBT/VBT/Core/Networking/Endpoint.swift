import Foundation

/// Odpowiednik `ApiService.kt` — jeden opis żądania na endpoint serwera.
/// Ścieżki i metody 1:1 z Retrofit interface po stronie Androida.
struct Endpoint {
    enum Method: String {
        case get = "GET", post = "POST", put = "PUT", patch = "PATCH", delete = "DELETE"
    }

    let method: Method
    let path: String
    var query: [String: String?] = [:]
    var body: Data?

    private static func encode<T: Encodable>(_ value: T) -> Data? {
        try? JSONEncoder().encode(value)
    }

    // MARK: Auth
    static func login(_ req: LoginRequest) -> Endpoint { Endpoint(method: .post, path: "auth/login", body: encode(req)) }
    static func register(_ req: RegisterRequest) -> Endpoint { Endpoint(method: .post, path: "auth/register", body: encode(req)) }

    // MARK: Exercises
    static func getExercises() -> Endpoint { Endpoint(method: .get, path: "exercises") }
    static func createExercise(_ req: CreateExerciseRequest) -> Endpoint { Endpoint(method: .post, path: "exercises", body: encode(req)) }

    // MARK: Plans
    static func getPlans() -> Endpoint { Endpoint(method: .get, path: "plans") }
    static func createPlan(_ req: CreatePlanRequest) -> Endpoint { Endpoint(method: .post, path: "plans", body: encode(req)) }
    static func getPlan(id: Int) -> Endpoint { Endpoint(method: .get, path: "plans/\(id)") }
    static func updatePlan(id: Int, _ req: CreatePlanRequest) -> Endpoint { Endpoint(method: .put, path: "plans/\(id)", body: encode(req)) }
    static func deletePlan(id: Int) -> Endpoint { Endpoint(method: .delete, path: "plans/\(id)") }
    static func assignPlan(id: Int, athleteId: Int) -> Endpoint { Endpoint(method: .post, path: "plans/\(id)/assign/\(athleteId)") }

    // MARK: Calendar
    static func getCalendarEntries(athleteId: Int? = nil, dateStart: String? = nil, dateEnd: String? = nil) -> Endpoint {
        Endpoint(method: .get, path: "calendar", query: [
            "athlete_id": athleteId.map(String.init),
            "date_start": dateStart,
            "date_end": dateEnd
        ])
    }
    static func createCalendarEntry(_ req: CreateCalendarEntryRequest) -> Endpoint { Endpoint(method: .post, path: "calendar", body: encode(req)) }
    static func updateCalendarEntry(id: Int, _ req: UpdateCalendarEntryRequest) -> Endpoint { Endpoint(method: .put, path: "calendar/\(id)", body: encode(req)) }
    static func deleteCalendarEntry(id: Int) -> Endpoint { Endpoint(method: .delete, path: "calendar/\(id)") }

    // MARK: Workout Sessions
    static func getSessions(athleteId: Int? = nil) -> Endpoint {
        Endpoint(method: .get, path: "sessions", query: ["athlete_id": athleteId.map(String.init)])
    }
    static func createSession(_ req: CreateSessionRequest) -> Endpoint { Endpoint(method: .post, path: "sessions", body: encode(req)) }
    static func startLiveSession(_ req: StartLiveSessionRequest) -> Endpoint { Endpoint(method: .post, path: "sessions/live", body: encode(req)) }
    static func appendReps(sessionId: Int, _ req: AppendRepsRequest) -> Endpoint { Endpoint(method: .post, path: "sessions/\(sessionId)/reps", body: encode(req)) }
    static func deleteSession(id: Int) -> Endpoint { Endpoint(method: .delete, path: "sessions/\(id)") }
    static func getSession(id: Int) -> Endpoint { Endpoint(method: .get, path: "sessions/\(id)") }
    static func updateRep(sessionId: Int, repId: Int, _ req: UpdateRepRequest) -> Endpoint { Endpoint(method: .patch, path: "sessions/\(sessionId)/reps/\(repId)", body: encode(req)) }
    static func deleteRep(sessionId: Int, repId: Int) -> Endpoint { Endpoint(method: .delete, path: "sessions/\(sessionId)/reps/\(repId)") }

    // MARK: Athletes / Users
    static func getAthletes() -> Endpoint { Endpoint(method: .get, path: "users/athletes") }
    static func getAthlete(id: Int) -> Endpoint { Endpoint(method: .get, path: "users/athletes/\(id)") }
    static func createAthlete(_ req: CreateAthleteRequest) -> Endpoint { Endpoint(method: .post, path: "users/athletes", body: encode(req)) }
    static func assignUserByUsername(_ req: AssignByUsernameRequest) -> Endpoint { Endpoint(method: .post, path: "users/assign-by-username", body: encode(req)) }
    static func adminAssignToCoach(_ req: AdminAssignRequest) -> Endpoint { Endpoint(method: .post, path: "admin/assign-to-coach", body: encode(req)) }
    static func adminUnassignFromCoach(_ req: AdminAssignRequest) -> Endpoint { Endpoint(method: .delete, path: "admin/unassign-from-coach", body: encode(req)) }
    static func unassignAthlete(id: Int) -> Endpoint { Endpoint(method: .delete, path: "users/athletes/\(id)/unassign") }

    // MARK: Velocity Trace
    static func uploadVelocityTrace(sessionId: Int, repId: Int, _ req: VelocityTraceRequest) -> Endpoint {
        Endpoint(method: .post, path: "sessions/\(sessionId)/reps/\(repId)/velocity-trace", body: encode(req))
    }

    // MARK: Analytics
    static func velocityTrend(athleteId: Int? = nil, exerciseId: Int? = nil, days: Int = 30) -> Endpoint {
        Endpoint(method: .get, path: "analytics/velocity-trend", query: [
            "athlete_id": athleteId.map(String.init),
            "exercise_id": exerciseId.map(String.init),
            "days": String(days)
        ])
    }
    static func oneRmProgress(athleteId: Int? = nil, exerciseId: Int) -> Endpoint {
        Endpoint(method: .get, path: "analytics/1rm-progress", query: [
            "athlete_id": athleteId.map(String.init),
            "exercise_id": String(exerciseId)
        ])
    }
    static func fatigueIndex(sessionId: Int, athleteId: Int? = nil, exerciseId: Int? = nil) -> Endpoint {
        Endpoint(method: .get, path: "analytics/fatigue-index", query: [
            "session_id": String(sessionId),
            "athlete_id": athleteId.map(String.init),
            "exercise_id": exerciseId.map(String.init)
        ])
    }
    static func weeklyLoad(athleteId: Int? = nil, weeks: Int = 6, exerciseId: Int? = nil) -> Endpoint {
        Endpoint(method: .get, path: "analytics/weekly-load", query: [
            "athlete_id": athleteId.map(String.init),
            "weeks": String(weeks),
            "exercise_id": exerciseId.map(String.init)
        ])
    }
    static func weekComparison(exerciseId: Int, athleteId: Int? = nil, weeks: Int = 8) -> Endpoint {
        Endpoint(method: .get, path: "analytics/week-comparison", query: [
            "exercise_id": String(exerciseId),
            "athlete_id": athleteId.map(String.init),
            "weeks": String(weeks)
        ])
    }

    // MARK: Dashboard
    static func dashboardStats() -> Endpoint { Endpoint(method: .get, path: "dashboard/stats") }
    static func recentSessions(limit: Int = 5) -> Endpoint {
        Endpoint(method: .get, path: "dashboard/recent-sessions", query: ["limit": String(limit)])
    }
}
