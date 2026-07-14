import Foundation
import Observation

/// Port z Android `AthleteListViewModel.kt` - bez sekcji "admin: przypisz do dowolnego
/// trenera" (funkcja adminowa, rzadka ścieżka, pomijana w tym pierwszym przebiegu portu).
@Observable
final class AthleteListViewModel {
    private(set) var athletes: [UserDto] = []
    private(set) var isLoading = false
    var error: String?
    var successMessage: String?

    private let apiClient: APIClient

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    @MainActor
    func loadAthletes() async {
        isLoading = true
        error = nil
        do {
            athletes = try await apiClient.send(.getAthletes())
        } catch {
            self.error = "Błąd ładowania: \(error.localizedDescription)"
        }
        isLoading = false
    }

    @MainActor
    func createAthlete(username: String, email: String, password: String) async -> Bool {
        guard !username.isEmpty, !password.isEmpty else {
            error = "Nazwa użytkownika i hasło są wymagane"
            return false
        }
        do {
            let request = CreateAthleteRequest(username: username, email: email.isEmpty ? nil : email, password: password)
            let _: UserDto = try await apiClient.send(.createAthlete(request))
            await loadAthletes()
            return true
        } catch {
            self.error = "Błąd tworzenia: \(error.localizedDescription)"
            return false
        }
    }

    @MainActor
    func assignByUsername(_ username: String) async -> Bool {
        let trimmed = username.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return false }
        isLoading = true
        do {
            let _: UserDto = try await apiClient.send(.assignUserByUsername(AssignByUsernameRequest(username: trimmed)))
            successMessage = "Użytkownik '\(trimmed)' przypisany"
            isLoading = false
            await loadAthletes()
            return true
        } catch let apiError as APIError {
            if case .http(let status, _) = apiError {
                error = status == 404 ? "Nie znaleziono użytkownika '\(trimmed)'" : (status == 400 ? "Ten użytkownik jest już na Twojej liście" : "Błąd: \(status)")
            } else {
                error = apiError.localizedDescription
            }
            isLoading = false
            return false
        } catch {
            self.error = "Błąd: \(error.localizedDescription)"
            isLoading = false
            return false
        }
    }
}
