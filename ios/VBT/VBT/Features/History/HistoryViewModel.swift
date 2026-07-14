import Foundation
import Observation

/// Port z Android `HistoryViewModel.kt` - bez sekcji lokalnych sesji aktywnych/niezsynchronizowanych
/// (Room `WorkoutSessionEntity`), bo lokalna trwałość nie jest jeszcze podłączona do żadnego VM
/// (patrz komentarz w `WorkoutViewModel` i `IOS_PORT_PLAN.md` Faza 4).
@Observable
final class HistoryViewModel {
    private(set) var sessions: [WorkoutSessionDto] = []
    private(set) var athletes: [UserDto] = []
    var selectedAthleteId: Int?
    private(set) var isCoach = false
    private(set) var isLoading = false
    var error: String?

    private let apiClient: APIClient
    private let authRepository: AuthRepository

    init(apiClient: APIClient, authRepository: AuthRepository) {
        self.apiClient = apiClient
        self.authRepository = authRepository
        self.isCoach = authRepository.currentRole == .coach
    }

    @MainActor
    func onAppear() async {
        if isCoach { await loadAthletes() }
        await loadSessions()
    }

    @MainActor
    func loadAthletes() async {
        do {
            athletes = try await apiClient.send(.getAthletes())
        } catch {
            // Cicho - filtr po zawodniku jest opcjonalny.
        }
    }

    @MainActor
    func loadSessions() async {
        isLoading = true
        error = nil
        do {
            let result: [WorkoutSessionDto] = try await apiClient.send(.getSessions(athleteId: selectedAthleteId))
            sessions = result.sorted { $0.startedAt > $1.startedAt }
        } catch {
            self.error = "Błąd ładowania sesji: \(error.localizedDescription)"
        }
        isLoading = false
    }

    @MainActor
    func filterByAthlete(_ athleteId: Int?) async {
        selectedAthleteId = athleteId
        await loadSessions()
    }

    @MainActor
    func deleteSession(_ sessionId: Int) async {
        do {
            try await apiClient.sendNoContent(.deleteSession(id: sessionId))
            sessions.removeAll { $0.id == sessionId }
        } catch {
            self.error = "Nie udało się usunąć sesji: \(error.localizedDescription)"
        }
    }
}
