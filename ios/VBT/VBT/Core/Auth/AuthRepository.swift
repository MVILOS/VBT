import Foundation

enum UserRole: String {
    case coach, athlete
}

/// Port 1:1 z Android `data/repository/AuthRepository.kt`. `@Observable`, żeby
/// `RootView` mogło reagować na zmianę stanu zalogowania bez ręcznego bindowania.
@Observable
final class AuthRepository {
    private let api: APIClient
    private let keychain: KeychainStore
    private let prefs: UserPreferences

    private(set) var currentUser: UserDto?

    var isLoggedIn: Bool { keychain.readToken() != nil && currentUser != nil }
    var currentRole: UserRole? { prefs.role.flatMap(UserRole.init) }

    init(api: APIClient, keychain: KeychainStore, prefs: UserPreferences) {
        self.api = api
        self.keychain = keychain
        self.prefs = prefs
        restoreSession()
    }

    /// Po starcie aplikacji: jeśli mamy token i dane usera z poprzedniej sesji, odtwórz stan
    /// bez ponownego logowania (nie ma osobnego endpointu /users/me, więc bazujemy na cache).
    private func restoreSession() {
        guard keychain.readToken() != nil,
              let id = prefs.userId,
              let username = prefs.username,
              let role = prefs.role else { return }
        currentUser = UserDto(id: id, email: nil, username: username, role: role, isActive: true, coachId: prefs.coachId)
    }

    func login(username: String, password: String) async -> Result<UserDto, Error> {
        do {
            let response: LoginResponse = try await api.send(.login(LoginRequest(username: username, password: password)))
            persist(response)
            return .success(response.user)
        } catch {
            return .failure(error)
        }
    }

    func register(username: String, password: String) async -> Result<UserDto, Error> {
        do {
            let response: LoginResponse = try await api.send(.register(RegisterRequest(username: username, password: password)))
            persist(response)
            return .success(response.user)
        } catch {
            return .failure(error)
        }
    }

    func logout() {
        keychain.clear()
        prefs.clear()
        currentUser = nil
    }

    private func persist(_ response: LoginResponse) {
        keychain.saveToken(response.accessToken)
        prefs.save(userId: response.user.id, username: response.user.username, role: response.user.role, coachId: response.user.coachId)
        currentUser = response.user
    }
}
