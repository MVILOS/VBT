import Foundation
import Observation

/// Port (tydzień jako lista dni, nie grid - patrz komentarz w `AthleteProfileViewModel`)
/// z Android `ScheduleViewModel.kt`: widok tygodniowy wszystkich zawodników (coach) z
/// filtrem po zawodniku.
@Observable
final class ScheduleViewModel {
    private(set) var entries: [CalendarEntryDto] = []
    private(set) var athletes: [UserDto] = []
    private(set) var plans: [TrainingPlanDto] = []
    private(set) var isCoach = false
    private(set) var weekStart: Date
    var filterAthleteId: Int?
    private(set) var isLoading = false
    var error: String?

    private let apiClient: APIClient
    private let authRepository: AuthRepository

    init(apiClient: APIClient, authRepository: AuthRepository) {
        self.apiClient = apiClient
        self.authRepository = authRepository
        self.weekStart = Self.mondayOfCurrentWeek()
    }

    private static func mondayOfCurrentWeek(from date: Date = .now) -> Date {
        var calendar = Calendar(identifier: .iso8601)
        calendar.firstWeekday = 2 // poniedziałek
        let components = calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: date)
        return calendar.date(from: components) ?? date
    }

    private static let isoDate: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.calendar = Calendar(identifier: .iso8601)
        return f
    }()

    var weekDays: [Date] {
        (0..<7).compactMap { Calendar.current.date(byAdding: .day, value: $0, to: weekStart) }
    }

    func entries(for day: Date) -> [CalendarEntryDto] {
        let dayString = Self.isoDate.string(from: day)
        return entries
            .filter { $0.date == dayString }
            .filter { filterAthleteId == nil || $0.athleteId == filterAthleteId }
    }

    @MainActor
    func loadData() async {
        isLoading = true
        error = nil
        isCoach = authRepository.currentRole == .coach

        let weekEnd = Calendar.current.date(byAdding: .day, value: 6, to: weekStart) ?? weekStart
        let dateStart = Self.isoDate.string(from: weekStart)
        let dateEnd = Self.isoDate.string(from: weekEnd)

        do {
            entries = try await apiClient.send(.getCalendarEntries(dateStart: dateStart, dateEnd: dateEnd))
            if isCoach {
                athletes = (try? await apiClient.send(.getAthletes())) ?? []
            }
            plans = (try? await apiClient.send(.getPlans())) ?? []
        } catch {
            self.error = "Błąd połączenia: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func prevWeek() { weekStart = Calendar.current.date(byAdding: .day, value: -7, to: weekStart) ?? weekStart }
    func nextWeek() { weekStart = Calendar.current.date(byAdding: .day, value: 7, to: weekStart) ?? weekStart }
    func goToToday() { weekStart = Self.mondayOfCurrentWeek() }

    @MainActor
    func addEntry(date: Date, athleteId: Int?, planId: Int?, title: String, timeSlot: String, notes: String) async {
        let dateString = Self.isoDate.string(from: date)
        do {
            let request = CreateCalendarEntryRequest(athleteId: athleteId, planId: planId, date: dateString, timeSlot: timeSlot.isEmpty ? nil : timeSlot, title: title, notes: notes.isEmpty ? nil : notes)
            let _: CalendarEntryDto = try await apiClient.send(.createCalendarEntry(request))
            await loadData()
        } catch {
            self.error = "Nie udało się utworzyć wpisu: \(error.localizedDescription)"
        }
    }

    @MainActor
    func deleteEntry(_ id: Int) async {
        do {
            try await apiClient.sendNoContent(.deleteCalendarEntry(id: id))
            await loadData()
        } catch {
            self.error = "Nie udało się usunąć wpisu: \(error.localizedDescription)"
        }
    }

    @MainActor
    func markStatus(_ id: Int, status: String) async {
        do {
            let request = UpdateCalendarEntryRequest(title: nil, notes: nil, date: nil, timeSlot: nil, status: status, planId: nil)
            let _: CalendarEntryDto = try await apiClient.send(.updateCalendarEntry(id: id, request))
            await loadData()
        } catch {
            self.error = "Nie udało się zaktualizować statusu: \(error.localizedDescription)"
        }
    }
}
