import Foundation

/// Port 1:1 z Android `HomeViewModel.kt`.
@Observable
final class HomeViewModel {
    private(set) var username = ""
    private(set) var role = "athlete"
    var isBleConnected = false
    private(set) var todayEntries: [CalendarEntryDto] = []
    private(set) var dashboardStats: DashboardStatsDto?
    private(set) var recentSessions: [RecentSessionDto] = []
    private(set) var isLoading = false
    /// Ustawiane, gdy którekolwiek zapytanie do serwera nie powiodło się z powodu braku
    /// sieci — dashboard i tak pokazuje to, co udało się wczytać wcześniej, ale
    /// użytkownik musi wiedzieć, że dane mogą być nieaktualne.
    private(set) var offlineNotice: String?

    var isCoach: Bool { role == "coach" }

    private let authRepository: AuthRepository
    private let apiClient: APIClient

    init(authRepository: AuthRepository, apiClient: APIClient) {
        self.authRepository = authRepository
        self.apiClient = apiClient
        if let user = authRepository.currentUser {
            username = user.username
            role = user.role
        }
    }

    @MainActor
    func loadData() async {
        isLoading = true
        var anyNetworkFailure = false

        let today = Self.dateOnlyFormatter.string(from: Date())

        do {
            todayEntries = try await apiClient.send(.getCalendarEntries(dateStart: today, dateEnd: today))
        } catch {
            anyNetworkFailure = true
        }

        do {
            dashboardStats = try await apiClient.send(.dashboardStats())
        } catch {
            anyNetworkFailure = true
        }

        do {
            recentSessions = try await apiClient.send(.recentSessions(limit: 5))
        } catch {
            anyNetworkFailure = true
        }

        isLoading = false
        offlineNotice = anyNetworkFailure ? "Brak połączenia z serwerem - dane mogą być nieaktualne" : nil
    }

    func logout() {
        authRepository.logout()
    }
}

private extension ISO8601DateFormatter {
    static let dateOnly: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.calendar = Calendar(identifier: .iso8601)
        return formatter
    }()
}
