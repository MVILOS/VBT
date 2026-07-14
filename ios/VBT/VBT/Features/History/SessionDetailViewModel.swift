import Foundation
import Observation

/// Port 1:1 z Android `SessionDetailViewModel.kt`.
@Observable
final class SessionDetailViewModel {
    private(set) var session: WorkoutSessionDto?
    private(set) var isLoading = false
    private(set) var isSaving = false
    var error: String?

    private let apiClient: APIClient
    private let estimate1RM = Estimate1RMUseCase()
    private let sessionId: Int

    init(apiClient: APIClient, sessionId: Int) {
        self.apiClient = apiClient
        self.sessionId = sessionId
    }

    @MainActor
    func loadSession() async {
        isLoading = true
        error = nil
        do {
            session = try await apiClient.send(.getSession(id: sessionId))
        } catch {
            self.error = "Błąd ładowania szczegółów sesji: \(error.localizedDescription)"
        }
        isLoading = false
    }

    /// Poprawia ciężar dla wszystkich powtórzeń danej serii (np. źle wpisany kg).
    @MainActor
    func updateSetWeight(setNumber: Int, newLoadKg: Double) async {
        guard let reps = session?.reps?.filter({ $0.setNumber == setNumber }) else { return }
        isSaving = true
        do {
            for rep in reps {
                guard let repId = rep.id else { continue }
                let new1rm = estimate1RM.estimateFromVelocity(loadKg: Float(newLoadKg), meanVelocityMs: Float(rep.meanVelocity))
                let request = UpdateRepRequest(loadKg: newLoadKg, setNumber: nil, repNumber: nil, estimated1rm: new1rm)
                try await apiClient.sendNoContent(.updateRep(sessionId: sessionId, repId: repId, request))
            }
            await refreshAfterEdit()
        } catch {
            isSaving = false
            self.error = "Błąd zapisu ciężaru: \(error.localizedDescription)"
        }
    }

    /// Usuwa pojedyncze błędne powtórzenie (np. fałszywy rep od pociągnięcia linki).
    @MainActor
    func deleteRep(_ repId: Int) async {
        isSaving = true
        do {
            try await apiClient.sendNoContent(.deleteRep(sessionId: sessionId, repId: repId))
            await refreshAfterEdit()
        } catch {
            isSaving = false
            self.error = "Błąd usuwania powtórzenia: \(error.localizedDescription)"
        }
    }

    /// Scala serię `setNumber` z poprzednią (np. gdy zapomniano nacisnąć "kolejna seria") -
    /// przenumerowuje jej powtórzenia pod poprzednią serię, kontynuując numerację rep_number.
    @MainActor
    func mergeSetWithPrevious(setNumber: Int) async {
        guard setNumber > 1, let reps = session?.reps else { return }
        let targetSet = setNumber - 1
        let repsToMove = reps.filter { $0.setNumber == setNumber }.sorted { $0.repNumber < $1.repNumber }
        guard !repsToMove.isEmpty else { return }
        let startingRepNumber = (reps.filter { $0.setNumber == targetSet }.map(\.repNumber).max() ?? 0) + 1

        isSaving = true
        do {
            for (index, rep) in repsToMove.enumerated() {
                guard let repId = rep.id else { continue }
                let request = UpdateRepRequest(loadKg: nil, setNumber: targetSet, repNumber: startingRepNumber + index, estimated1rm: nil)
                try await apiClient.sendNoContent(.updateRep(sessionId: sessionId, repId: repId, request))
            }
            await refreshAfterEdit()
        } catch {
            isSaving = false
            self.error = "Błąd scalania serii: \(error.localizedDescription)"
        }
    }

    @MainActor
    private func refreshAfterEdit() async {
        do {
            session = try await apiClient.send(.getSession(id: sessionId))
            isSaving = false
            error = nil
        } catch {
            isSaving = false
            self.error = "Błąd odświeżania: \(error.localizedDescription)"
        }
    }
}
