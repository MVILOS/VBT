import Foundation
import Observation

/// Port (bez zakładek zmęczenia/porównania tygodni - patrz `IOS_PORT_PLAN.md` dla
/// zakresu kolejnych faz) z Android `AnalyticsViewModel.kt`: trend prędkości + progres 1RM
/// dla wybranego ćwiczenia, podsumowanie tygodniowego obciążenia.
@Observable
final class AnalyticsViewModel {
    private(set) var exercises: [ExerciseDto] = []
    var selectedExerciseId: Int?
    private(set) var athletes: [UserDto] = []
    var selectedAthleteId: Int?
    private(set) var isCoach = false
    private var athleteId: Int?

    private(set) var velocityTrend: [VelocityTrendPointDto] = []
    private(set) var oneRmProgress: [OneRmProgressPointDto] = []
    private(set) var weeklyLoad: [WeeklyLoadDto] = []

    private(set) var isLoading = false
    var error: String?

    private let apiClient: APIClient
    private let authRepository: AuthRepository

    init(apiClient: APIClient, authRepository: AuthRepository) {
        self.apiClient = apiClient
        self.authRepository = authRepository
    }

    private var effectiveAthleteId: Int? { isCoach ? selectedAthleteId : athleteId }

    @MainActor
    func loadInitialData() async {
        let role = authRepository.currentRole
        isCoach = (role == .coach)
        athleteId = isCoach ? nil : authRepository.currentUser?.id

        do {
            let fetchedExercises: [ExerciseDto] = try await apiClient.send(.getExercises())
            exercises = fetchedExercises.sorted { categoryOrder($0.category) == categoryOrder($1.category) ? $0.name < $1.name : categoryOrder($0.category) < categoryOrder($1.category) }
        } catch {
            self.error = error.localizedDescription
        }

        if isCoach {
            athletes = (try? await apiClient.send(.getAthletes())) ?? []
            selectedAthleteId = athletes.first?.id
        }

        await loadWeeklyLoad()
    }

    private func categoryOrder(_ category: String?) -> Int {
        switch category {
        case "olympic": return 0
        case "strength": return 1
        case "ballistic": return 2
        case "auxiliary": return 3
        default: return 4
        }
    }

    @MainActor
    func selectAthlete(_ id: Int) async {
        selectedAthleteId = id
        selectedExerciseId = nil
        velocityTrend = []
        oneRmProgress = []
        await loadWeeklyLoad()
    }

    @MainActor
    func selectExercise(_ exerciseId: Int) async {
        selectedExerciseId = exerciseId
        isLoading = true
        error = nil
        do {
            async let velReq: [VelocityTrendPointDto] = apiClient.send(.velocityTrend(athleteId: effectiveAthleteId, exerciseId: exerciseId, days: 90))
            async let ormReq: [OneRmProgressPointDto] = apiClient.send(.oneRmProgress(athleteId: effectiveAthleteId, exerciseId: exerciseId))
            (velocityTrend, oneRmProgress) = try await (velReq, ormReq)
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    @MainActor
    private func loadWeeklyLoad() async {
        weeklyLoad = (try? await apiClient.send(.weeklyLoad(athleteId: effectiveAthleteId, weeks: 8))) ?? []
    }
}
