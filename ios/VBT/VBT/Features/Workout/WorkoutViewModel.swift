import Foundation
import Observation

/// Port z Android `WorkoutViewModel.kt`, ze świadomym uproszczeniem: Android trzyma
/// sesję w lokalnej bazie Room (crash-safe, offline-first, resume po restarcie appki) i
/// dopiero potem synchronizuje z serwerem w tle (`WorkoutSyncManager`/`SessionSyncWorker`).
/// Ten SwiftData-owy odpowiednik jeszcze nie istnieje (patrz IOS_PORT_PLAN.md Faza 4),
/// więc na razie ten VM trzyma trening w pamięci i wysyła na serwer na bieżąco / przy
/// zakończeniu. Efekt: brak dziś odporności na crash appki w trakcie treningu i brak
/// wznawiania przerwanej sesji - to świadomy dług do spłacenia razem z Fazą 4.
@Observable
final class WorkoutViewModel {
    private static let autoFinishSeconds = 10

    // Kontekst sesji (coach może nagrywać dla zawodnika)
    private(set) var sessionAthleteId: Int?
    private(set) var sessionAthleteName: String?

    private(set) var mode: WorkoutMode = .idle

    // Plan
    private(set) var selectedPlan: TrainingPlanDto?
    private(set) var availablePlans: [TrainingPlanDto] = []
    private(set) var currentExerciseIndex = 0
    private(set) var currentSetIndex = 0

    // Aktywna seria
    private(set) var currentExercise: ExerciseDto?
    private(set) var currentExerciseName = ""
    private(set) var currentLoadKg: Float = 0
    private(set) var targetReps = 0
    private(set) var completedRepsInSet: [WorkoutRep] = []

    // Stan serii
    private(set) var isPaused = false
    private(set) var autoFinishCountdown: Int?

    private(set) var allReps: [WorkoutRep] = []
    private(set) var completedSets: [CompletedSetSnapshot] = []
    /// Szczytowa prędkość zaobserwowana w bieżącej serii (resetowana przy nowej serii).
    private(set) var peakVelocityInSet: Float = 0

    private(set) var isLoading = false
    var error: String?
    private(set) var availableExercises: [ExerciseDto] = []
    private(set) var availableAthletes: [UserDto] = []
    private(set) var isCoach = false
    private(set) var startTime = Date()

    var showChangeExercise = false

    private let apiClient: APIClient
    private let authRepository: AuthRepository
    private let bleManager: VbtBleManager
    private let calculatePower = CalculatePowerUseCase()
    private let estimate1RM = Estimate1RMUseCase()

    /// ID sesji na serwerze (POST /sessions/live) - nil dopóki nie uda się jej założyć
    /// (np. offline). Bez lokalnej bazy nie ma dziś kolejki retry - jeśli to zawiedzie,
    /// `finishWorkout` spróbuje jednorazowo `POST /sessions` z całością na końcu.
    private var serverSessionId: Int?
    private var hrSum = 0
    private var hrCount = 0
    private var hrMax = 0
    private var autoFinishTask: Task<Void, Never>?

    init(apiClient: APIClient, authRepository: AuthRepository, bleManager: VbtBleManager) {
        self.apiClient = apiClient
        self.authRepository = authRepository
        self.bleManager = bleManager
        self.isCoach = authRepository.currentRole == .coach

        bleManager.onRepResult = { [weak self] rep in
            self?.onRepReceived(rep)
        }

        Task { await loadAvailableExercises() }
        if isCoach {
            Task { await loadAvailableAthletes() }
        }
    }

    deinit {
        bleManager.onRepResult = nil
    }

    // MARK: - Session management

    func startSession() {
        if isCoach {
            mode = .sessionSelect
            startTime = Date()
        } else {
            selectSessionAthlete(nil)
        }
    }

    func selectSessionAthlete(_ athlete: UserDto?) {
        sessionAthleteId = athlete?.id
        sessionAthleteName = athlete?.username ?? "You"
        mode = .exercisePicker
        startTime = Date()
    }

    // MARK: - Freestyle & plan

    func startFreestyle(exercise: ExerciseDto, loadKg: Float) {
        resetHeartRateStats()
        currentExercise = exercise
        currentExerciseName = exercise.name
        currentLoadKg = loadKg
        targetReps = 0
        completedRepsInSet = []
        peakVelocityInSet = 0
        mode = .active
        startTime = Date()

        if let mvt = exercise.mvt {
            bleManager.setExerciseParams(minLiftVel: mvt * 0.5, endLiftVel: mvt * 0.7, minRepDist: 0.15)
        }

        Task { await startLiveSessionOnServer(planId: nil) }
    }

    func startPlanWorkout(_ plan: TrainingPlanDto) {
        resetHeartRateStats()
        selectedPlan = plan
        mode = .active
        currentExerciseIndex = 0
        currentSetIndex = 0
        startTime = Date()

        Task { await startLiveSessionOnServer(planId: plan.id) }

        if !plan.exercises.isEmpty {
            selectExerciseFromPlan(0)
        }
    }

    func selectExerciseFromPlan(_ exerciseIndex: Int) {
        guard let plan = selectedPlan, exerciseIndex < plan.exercises.count else { return }
        let planExercise = plan.exercises[exerciseIndex]
        guard let exercise = planExercise.exercise, let firstSet = planExercise.sets.first else { return }

        currentExercise = exercise
        currentExerciseIndex = exerciseIndex
        currentSetIndex = 0
        currentExerciseName = exercise.name
        currentLoadKg = firstSet.loadKg
        targetReps = firstSet.reps
        completedRepsInSet = []
        peakVelocityInSet = 0
        mode = .active
        startTime = Date()

        bleManager.setExerciseParams(
            minLiftVel: exercise.mvt.map { $0 * 0.5 } ?? 0.3,
            endLiftVel: exercise.mvt.map { $0 * 0.7 } ?? 0.6,
            minRepDist: 0.15
        )
    }

    // MARK: - Rep processing

    func onRepReceived(_ rep: RepFromDevice) {
        guard !isPaused, mode == .active else { return }

        let power = calculatePower.peakPower(loadKg: currentLoadKg, peakVelocityMs: rep.peakVelocityMs)
        let repNumber = completedRepsInSet.count + 1

        let dto = RepResultDto(
            id: nil,
            sessionId: serverSessionId,
            exerciseId: currentExercise?.id ?? freeMeasurementExercise.id,
            setNumber: currentSetIndex + 1,
            repNumber: repNumber,
            meanVelocity: Double(rep.meanVelocityMs),
            peakVelocity: Double(rep.peakVelocityMs),
            loadKg: Double(currentLoadKg),
            powerWatts: Double(power),
            estimated1rm: estimate1RM.estimate(loadKg: currentLoadKg, meanVelocityMs: rep.meanVelocityMs, repsInSet: repNumber),
            timestamp: nil
        )
        let workoutRep = WorkoutRep(dto: dto)

        completedRepsInSet.append(workoutRep)
        allReps.append(workoutRep)
        peakVelocityInSet = max(peakVelocityInSet, rep.peakVelocityMs)

        // Push na żywo, jeśli sesja na serwerze istnieje - bez lokalnej kolejki retry
        // (patrz komentarz przy `serverSessionId`), więc błąd tu tylko loguje.
        if let servId = serverSessionId {
            Task { try? await apiClient.sendNoContent(.appendReps(sessionId: servId, AppendRepsRequest(reps: [dto]))) }
        }

        if targetReps > 0 && completedRepsInSet.count >= targetReps {
            resetAutoFinishTimer()
        }
    }

    // MARK: - Load & set editing

    func editCurrentLoad(_ newKg: Float) {
        currentLoadKg = newKg
    }

    // MARK: - Set / workout control

    func togglePause() {
        if !isPaused { cancelAutoFinish() }
        isPaused.toggle()
    }

    func finishSet() {
        cancelAutoFinish()
        let snapshot = CompletedSetSnapshot(
            setNumber: currentSetIndex + 1,
            exerciseName: currentExerciseName,
            loadKg: currentLoadKg,
            reps: completedRepsInSet
        )
        completedSets.append(snapshot)
        completedRepsInSet = []
        peakVelocityInSet = 0
        currentSetIndex += 1
    }

    func finishWorkout() {
        cancelAutoFinish()
        if !completedRepsInSet.isEmpty {
            completedSets.append(CompletedSetSnapshot(
                setNumber: currentSetIndex + 1,
                exerciseName: currentExerciseName,
                loadKg: currentLoadKg,
                reps: completedRepsInSet
            ))
        }

        Task {
            if let servId = serverSessionId {
                let finishedAt = ISO8601DateFormatter().string(from: Date())
                do {
                    try await apiClient.sendNoContent(.appendReps(sessionId: servId, AppendRepsRequest(reps: [], finishedAt: finishedAt)))
                } catch {
                    self.error = "Nie udało się wysłać końcówki treningu - spróbuj zsynchronizować później"
                }
            } else if let athleteId = sessionAthleteId ?? authRepository.currentUser?.id {
                // Brak sesji live (offline na starcie) - próba jednorazowego pełnego uploadu.
                let allDtos = completedSets.flatMap { $0.reps.map(\.dto) }
                do {
                    let request = CreateSessionRequest(athleteId: athleteId, planId: selectedPlan?.id, calendarEntryId: nil, notes: nil, reps: allDtos)
                    let _: WorkoutSessionDto = try await apiClient.send(.createSession(request))
                } catch {
                    self.error = "Brak połączenia - trening NIE został zapisany (offline sync przyjdzie w Fazie 4)"
                }
            }
            mode = .finished
        }
    }

    func requestExerciseChange() {
        cancelAutoFinish()
        showChangeExercise = true
        isPaused = true
    }

    func changeExercise(_ exercise: ExerciseDto, loadKg: Float) {
        if !completedRepsInSet.isEmpty {
            completedSets.append(CompletedSetSnapshot(
                setNumber: currentSetIndex + 1,
                exerciseName: currentExerciseName,
                loadKg: currentLoadKg,
                reps: completedRepsInSet
            ))
        }

        currentExercise = exercise
        showChangeExercise = false
        isPaused = false
        currentExerciseName = exercise.name
        currentLoadKg = loadKg
        targetReps = 0
        completedRepsInSet = []
        peakVelocityInSet = 0
        currentSetIndex += 1

        if let mvt = exercise.mvt {
            bleManager.setExerciseParams(minLiftVel: mvt * 0.5, endLiftVel: mvt * 0.7, minRepDist: 0.15)
        }
    }

    func cancelExerciseChange() {
        showChangeExercise = false
        isPaused = false
    }

    func reconnectBle() {
        bleManager.reconnect()
    }

    // MARK: - Heart rate (wołane z View przez .onChange(of: heartRateManager.heartRate))

    func recordHeartRateSample(_ hr: Int?) {
        guard let hr, hr > 0, mode == .active else { return }
        hrSum += hr
        hrCount += 1
        hrMax = max(hrMax, hr)
    }

    var averageHeartRate: Int? { hrCount > 0 ? hrSum / hrCount : nil }
    var maxHeartRate: Int? { hrCount > 0 ? hrMax : nil }

    // MARK: - Data loading

    @MainActor
    func loadAvailableExercises() async {
        do {
            let exercises: [ExerciseDto] = try await apiClient.send(.getExercises())
            availableExercises = [freeMeasurementExercise] + exercises
        } catch {
            self.error = "Brak połączenia z serwerem - lista ćwiczeń mogła nie zostać odświeżona"
            if availableExercises.isEmpty { availableExercises = [freeMeasurementExercise] }
        }
    }

    @MainActor
    func loadAvailableAthletes() async {
        do {
            availableAthletes = try await apiClient.send(.getAthletes())
        } catch {
            // Cicho - lista zawodników jest opcjonalna dla sekcji SESSION_SELECT.
        }
    }

    @MainActor
    func loadAvailablePlans() async {
        isLoading = true
        do {
            availablePlans = try await apiClient.send(.getPlans())
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    // MARK: - Server sync

    @MainActor
    private func startLiveSessionOnServer(planId: Int?) async {
        do {
            let request = StartLiveSessionRequest(athleteId: sessionAthleteId, planId: planId, notes: nil)
            let session: WorkoutSessionDto = try await apiClient.send(.startLiveSession(request))
            serverSessionId = session.id
        } catch {
            // Offline na starcie - `finishWorkout` spróbuje jednorazowego pełnego uploadu.
            serverSessionId = nil
        }
    }

    // MARK: - Helpers

    private func resetHeartRateStats() {
        hrSum = 0
        hrCount = 0
        hrMax = 0
    }

    /// Auto-zamykanie serii: po osiągnięciu targetReps odlicza `autoFinishSeconds`
    /// sekund (widoczne w UI z przyciskiem "Anuluj"); nowe powtórzenie resetuje
    /// odliczanie, a `cancelAutoFinish()` je przerywa.
    private func resetAutoFinishTimer() {
        autoFinishTask?.cancel()
        autoFinishTask = Task { [weak self] in
            guard let self else { return }
            for remaining in stride(from: Self.autoFinishSeconds, through: 1, by: -1) {
                self.autoFinishCountdown = remaining
                try? await Task.sleep(for: .seconds(1))
                if Task.isCancelled { return }
            }
            self.autoFinishCountdown = nil
            self.finishSet()
        }
    }

    func cancelAutoFinish() {
        autoFinishTask?.cancel()
        autoFinishTask = nil
        autoFinishCountdown = nil
    }
}
