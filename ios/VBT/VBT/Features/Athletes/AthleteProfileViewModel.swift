import Foundation
import Observation

enum AthleteProfileTab {
    case calendar, plans, sessions
}

/// Port z Android `AthleteProfileViewModel.kt`. Kalendarz jest tu prostą listą wpisów
/// (nie tygodniowym gridem jak na Webie/Androidzie) - patrz `ScheduleScreen` po pełny,
/// wielo-zawodnikowy widok tygodniowy.
@Observable
final class AthleteProfileViewModel {
    let athleteId: Int
    private(set) var athlete: UserDto?
    private(set) var calendarEntries: [CalendarEntryDto] = []
    private(set) var assignedPlans: [TrainingPlanDto] = []
    private(set) var allPlans: [TrainingPlanDto] = []
    private(set) var sessions: [WorkoutSessionDto] = []
    var selectedTab: AthleteProfileTab = .calendar
    private(set) var isLoading = false
    var error: String?

    private let apiClient: APIClient

    init(apiClient: APIClient, athleteId: Int) {
        self.apiClient = apiClient
        self.athleteId = athleteId
    }

    @MainActor
    func loadAll() async {
        isLoading = true
        error = nil
        do {
            async let athleteReq: UserDto = apiClient.send(.getAthlete(id: athleteId))
            async let plansReq: [TrainingPlanDto] = apiClient.send(.getPlans())
            async let sessionsReq: [WorkoutSessionDto] = apiClient.send(.getSessions(athleteId: athleteId))
            async let calendarReq: [CalendarEntryDto] = apiClient.send(.getCalendarEntries(athleteId: athleteId))

            let (athlete, allPlans, sessions, calendarEntries) = try await (athleteReq, plansReq, sessionsReq, calendarReq)
            self.athlete = athlete
            self.allPlans = allPlans
            self.assignedPlans = allPlans.filter { $0.assignedTo == athleteId }
            self.sessions = sessions
            self.calendarEntries = calendarEntries
        } catch {
            self.error = "Nie udało się załadować danych: \(error.localizedDescription)"
        }
        isLoading = false
    }

    @MainActor
    func addCalendarEntry(date: String, title: String, planId: Int?, timeSlot: String, notes: String) async {
        guard !date.isEmpty, !title.isEmpty else {
            error = "Data i tytuł są wymagane"
            return
        }
        do {
            let request = CreateCalendarEntryRequest(athleteId: athleteId, planId: planId, date: date, timeSlot: timeSlot.isEmpty ? nil : timeSlot, title: title, notes: notes.isEmpty ? nil : notes)
            let _: CalendarEntryDto = try await apiClient.send(.createCalendarEntry(request))
            await loadAll()
        } catch {
            self.error = "Nie udało się utworzyć wpisu: \(error.localizedDescription)"
        }
    }

    @MainActor
    func deleteCalendarEntry(_ id: Int) async {
        do {
            try await apiClient.sendNoContent(.deleteCalendarEntry(id: id))
            await loadAll()
        } catch {
            self.error = "Nie udało się usunąć wpisu: \(error.localizedDescription)"
        }
    }

    @MainActor
    func assignPlan(_ planId: Int) async {
        do {
            let _: TrainingPlanDto = try await apiClient.send(.assignPlan(id: planId, athleteId: athleteId))
            await loadAll()
        } catch {
            self.error = "Nie udało się przypisać planu: \(error.localizedDescription)"
        }
    }
}
