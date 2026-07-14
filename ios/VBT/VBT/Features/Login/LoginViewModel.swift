import Foundation
import Observation

/// Port 1:1 z Android `LoginViewModel.kt`.
@Observable
final class LoginViewModel {
    var username = ""
    var password = ""
    var isLoading = false
    var error: String?

    private let authRepository: AuthRepository

    init(authRepository: AuthRepository) {
        self.authRepository = authRepository
    }

    @MainActor
    func login() async -> Bool {
        guard !username.trimmingCharacters(in: .whitespaces).isEmpty,
              !password.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Uzupełnij wszystkie pola"
            return false
        }

        isLoading = true
        error = nil

        let result = await authRepository.login(username: username, password: password)
        isLoading = false

        switch result {
        case .success:
            username = ""
            password = ""
            return true
        case .failure(let err):
            error = err.localizedDescription
            return false
        }
    }
}
