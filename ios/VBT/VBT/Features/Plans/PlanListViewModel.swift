import Foundation
import Observation

/// Port 1:1 z Android `PlanListViewModel.kt`.
@Observable
final class PlanListViewModel {
    private(set) var plans: [TrainingPlanDto] = []
    private(set) var athletes: [UserDto] = []
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
    func loadPlans() async {
        isLoading = true
        error = nil
        do {
            plans = try await apiClient.send(.getPlans())
            if isCoach {
                athletes = (try? await apiClient.send(.getAthletes())) ?? []
            }
        } catch {
            self.error = "Nie udało się wczytać planów: \(error.localizedDescription)"
        }
        isLoading = false
    }

    @MainActor
    func deletePlan(_ planId: Int) async {
        do {
            try await apiClient.sendNoContent(.deletePlan(id: planId))
            await loadPlans()
        } catch {
            self.error = "Nie udało się usunąć planu: \(error.localizedDescription)"
        }
    }

    @MainActor
    func assignPlan(_ planId: Int, to athleteId: Int) async {
        do {
            let _: TrainingPlanDto = try await apiClient.send(.assignPlan(id: planId, athleteId: athleteId))
            await loadPlans()
        } catch {
            self.error = "Nie udało się przypisać planu: \(error.localizedDescription)"
        }
    }
}
