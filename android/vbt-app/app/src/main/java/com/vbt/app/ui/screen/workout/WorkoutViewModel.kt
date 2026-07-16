package com.vbt.app.ui.screen.workout

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.ble.BleConnectionState
import com.vbt.app.data.ble.HeartRateManager
import com.vbt.app.data.ble.RepFromDevice
import com.vbt.app.data.ble.VbtBleManager
import com.vbt.app.data.local.entity.*
import com.vbt.app.data.remote.ApiService
import com.vbt.app.data.remote.ExerciseDto
import com.vbt.app.data.remote.RepResultDto
import com.vbt.app.data.remote.TrainingPlanDto
import com.vbt.app.data.remote.UserDto
import com.vbt.app.data.repository.AuthRepository
import com.vbt.app.data.repository.ExerciseRepository
import com.vbt.app.data.repository.TrainingPlanRepository
import com.vbt.app.data.repository.WorkoutRepository
import com.vbt.app.data.sync.SessionSyncWorker
import com.vbt.app.data.sync.WorkoutSyncManager
import com.vbt.app.domain.model.VelocityZone
import com.vbt.app.domain.usecase.CalculatePowerUseCase
import com.vbt.app.domain.usecase.Estimate1RMUseCase
import com.vbt.app.service.WorkoutForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ==================== Data Classes ====================

enum class WorkoutMode {
    IDLE,              // Waiting for user action
    SESSION_SELECT,    // Coach selecting athlete
    EXERCISE_PICKER,   // User selecting exercise (freestyle or from plan)
    ACTIVE,            // Recording reps
    FINISHED           // Session completed
}

// Sentinel "pseudo-exercise" pokazywany na górze listy w pickerze - pozwala
// nagrać powtórzenia z czujnika BLE bez przypisywania ich do konkretnego
// ćwiczenia z bazy (np. szybki test czujnika albo ruch spoza bazy MVT).
// id = -1 oznacza "brak ćwiczenia serwerowego jeszcze nieutworzonego" -
// przy pierwszej synchronizacji z internetem apka utworzy odpowiadające mu
// ćwiczenie na serwerze przez istniejący (niezmieniany) endpoint POST /api/exercises.
val FREE_MEASUREMENT_EXERCISE = ExerciseDto(
    id = -1,
    name = "Wolny pomiar (bez ćwiczenia)",
    category = "freeform",
    mvt = null,
    description = "Swobodny pomiar prędkości/mocy bez przypisania do ćwiczenia z bazy."
)

data class VelocityPoint(
    val timestampMs: Long,
    val velocityMs: Float
)

data class CompletedSetSnapshot(
    val setNumber: Int,
    val exerciseName: String,
    val loadKg: Float,
    val reps: List<RepResultEntity>
)

data class WorkoutExerciseState(
    val exercise: ExerciseDto,
    val sets: List<com.vbt.app.data.remote.PlanSetDto>,
    val completedSets: Int = 0,
    val additionalSets: Int = 0
)

data class WorkoutUiState(
    // Session context (coach can record for athlete)
    val sessionAthleteId: Int? = null,
    val sessionAthleteName: String? = null,

    // Workout mode
    val mode: WorkoutMode = WorkoutMode.IDLE,

    // Plan (if training with plan)
    val selectedPlan: TrainingPlanDto? = null,
    val availablePlans: List<TrainingPlanDto> = emptyList(),
    val planExercises: List<WorkoutExerciseState> = emptyList(),
    val currentExerciseIndex: Int = 0,
    val currentSetIndex: Int = 0,

    // Active set
    val currentExerciseName: String = "",
    val currentLoadKg: Float = 0f,
    val targetReps: Int = 0,
    val completedRepsInSet: List<RepResultEntity> = emptyList(),

    // Live BLE data
    val currentVelocity: Float = 0f,
    val peakVelocity: Float = 0f,
    val velocityZone: VelocityZone = VelocityZone.ABSOLUTE_STRENGTH,
    val isRepInProgress: Boolean = false,
    val heartRate: Int? = null,

    // Set state
    val isPaused: Boolean = false,
    val setFinished: Boolean = false,
    // Odliczanie (sekundy) do automatycznego zamknięcia serii; null = nieaktywne
    val autoFinishCountdown: Int? = null,

    // All reps history (all sets)
    val allReps: List<RepResultEntity> = emptyList(),

    // Completed sets with reps (for server sync)
    val completedSets: List<CompletedSetSnapshot> = emptyList(),

    // Advanced velocity data (coach only)
    val currentRepVelocityBuffer: List<VelocityPoint> = emptyList(),

    // UI & Connection
    val isLoading: Boolean = false,
    val error: String? = null,
    val availableExercises: List<ExerciseDto> = emptyList(),
    val availableAthletes: List<UserDto> = emptyList(),
    val isCoach: Boolean = false,
    val connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val startTime: Long = 0L,

    // Exercise change overlay
    val showChangeExercise: Boolean = false
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val workoutRepository: WorkoutRepository,
    private val planRepository: TrainingPlanRepository,
    private val exerciseRepository: ExerciseRepository,
    private val authRepository: AuthRepository,
    private val bleManager: VbtBleManager,
    private val heartRateManager: HeartRateManager,
    private val calculatePower: CalculatePowerUseCase,
    private val estimate1RM: Estimate1RMUseCase,
    private val syncManager: WorkoutSyncManager,
    private val api: ApiService
) : ViewModel() {

    companion object {
        private const val TAG = "WorkoutViewModel"
        // Czas (s) od osiągnięcia targetReps do automatycznego zamknięcia serii
        private const val AUTO_FINISH_SECONDS = 10
    }

    // Local Room session (always created at workout start for crash safety)
    private var localSessionId: Long = 0
    // Current active set ID in Room
    private var currentSetId: Long = 0
    // Local exercise definition ID for current exercise
    private var currentLocalExerciseId: Long = 0
    // Server-assigned session ID (null when offline)
    private var serverSessionId: Int? = null

    // Statystyki tętna bieżącej sesji (zapisywane do Room przy finishWorkout)
    private var hrSum: Long = 0
    private var hrCount: Int = 0
    private var hrMax: Int = 0

    // Numery powtórzeń bieżącej serii, które trafiły już do kolejki live-sync
    // (onRepReceived). finishWorkout dokolejkowuje tylko pozostałe - bez tego
    // ostatnia seria szłaby na serwer podwójnie. Czyszczone na granicy serii.
    private val queuedRepNumbersInSet = mutableSetOf<Int>()

    // Resume: when navigated from History with an existing session
    private val resumeSessionId: Long = savedStateHandle.get<Long>("resumeSessionId") ?: 0L

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private val _currentVelocityBuffer = MutableStateFlow<MutableList<VelocityPoint>>(mutableListOf())
    private var peakVelocity: Float = 0f

    private var autoFinishJob: Job? = null
    private var repCollectionJob: Job? = null

    init {
        viewModelScope.launch {
            // Offline-first: od razu pokaż lokalną bazę ćwiczeń z Room (zawsze
            // dostępna, wgrana przy pierwszym starcie apki - patrz DatabaseModule),
            // żeby trening dało się zacząć natychmiast nawet bez sieci.
            val localExercises = try {
                exerciseRepository.getAllExercises().first().map { it.toExerciseDto() }
            } catch (e: Exception) {
                Log.w(TAG, "Nie można wczytać lokalnej bazy ćwiczeń", e)
                emptyList()
            }
            _uiState.update { it.copy(availableExercises = listOf(FREE_MEASUREMENT_EXERCISE) + localExercises) }

            // Następnie spróbuj odświeżyć z serwera - jeśli offline, zostajemy
            // przy lokalnej bazie zamiast pustej listy / błędu.
            try {
                val dtoExercises = api.getExercises().body() ?: emptyList()
                _uiState.update { it.copy(availableExercises = listOf(FREE_MEASUREMENT_EXERCISE) + dtoExercises) }
            } catch (e: Exception) {
                Log.w(TAG, "Nie można odświeżyć ćwiczeń z serwera", e)
                if (localExercises.isEmpty()) {
                    _uiState.update { it.copy(error = "Brak połączenia z serwerem - dostępna tylko lokalna baza ćwiczeń") }
                }
            }
        }

        viewModelScope.launch {
            authRepository.getCurrentRole().collect { role ->
                val isCoach = role == "coach"
                _uiState.update { it.copy(isCoach = isCoach) }
                if (isCoach) {
                    try {
                        val athletes = api.getAthletes().body() ?: emptyList()
                        _uiState.update { it.copy(availableAthletes = athletes) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Nie można pobrać listy zawodników", e)
                    }
                }
            }
        }

        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }

        viewModelScope.launch {
            bleManager.liveVelocity.collect { velocity ->
                _uiState.update { state ->
                    state.copy(
                        currentVelocity = velocity,
                        velocityZone = VelocityZone.fromVelocity(velocity),
                        peakVelocity = maxOf(state.peakVelocity, velocity)
                    )
                }
            }
        }

        viewModelScope.launch {
            bleManager.isLifting.collect { lifting ->
                _uiState.update { it.copy(isRepInProgress = lifting) }
            }
        }

        // Tętno ze współdzielonego HeartRateManager (połączenie zarządzane
        // z ekranu Connect) - płynie do UI treningu i statystyk sesji.
        viewModelScope.launch {
            heartRateManager.heartRate.collect { hr ->
                _uiState.update { it.copy(heartRate = hr) }
                if (hr != null && hr > 0 && _uiState.value.mode == WorkoutMode.ACTIVE) {
                    hrSum += hr
                    hrCount++
                    if (hr > hrMax) hrMax = hr
                }
            }
        }

        // Foreground service utrzymujący BLE przy życiu podczas aktywnego treningu
        viewModelScope.launch {
            _uiState
                .map { Triple(it.mode, it.currentExerciseName, it.completedRepsInSet.size) }
                .distinctUntilChanged()
                .collect { (mode, exerciseName, repCount) ->
                    when (mode) {
                        WorkoutMode.ACTIVE ->
                            WorkoutForegroundService.start(appContext, exerciseName, repCount)
                        WorkoutMode.FINISHED, WorkoutMode.IDLE ->
                            WorkoutForegroundService.stop(appContext)
                        else -> Unit
                    }
                }
        }

        repCollectionJob = viewModelScope.launch {
            bleManager.repResult.collect { rep ->
                onRepReceived(rep)
            }
        }

        // Resume existing session
        if (resumeSessionId > 0) {
            viewModelScope.launch {
                resumeSession(resumeSessionId)
            }
        }

        // Auto-start if navigated from Schedule (calendarEntryId > 0) or with planId
        val planId = savedStateHandle.get<Int>("planId") ?: -1
        val calendarEntryId = savedStateHandle.get<Int>("calendarEntryId") ?: -1
        val scheduleAthleteId = savedStateHandle.get<Int>("athleteId") ?: -1

        val fromSchedule = calendarEntryId > 0
        if (fromSchedule || planId > 0) {
            _uiState.update { it.copy(isLoading = true) }
        }

        viewModelScope.launch {
            if (fromSchedule || planId > 0) {
                try {
                    if (scheduleAthleteId > 0) {
                        val athletes = try { api.getAthletes().body() ?: emptyList() } catch (_: Exception) { emptyList() }
                        val athlete = athletes.find { it.id == scheduleAthleteId }
                        _uiState.update { it.copy(
                            sessionAthleteId = scheduleAthleteId,
                            sessionAthleteName = athlete?.username ?: "Zawodnik #$scheduleAthleteId"
                        ) }
                    }

                    if (planId > 0) {
                        val response = api.getPlan(planId)
                        if (response.isSuccessful) {
                            response.body()?.let { plan ->
                                startPlanWorkout(plan)
                            } ?: _uiState.update { it.copy(isLoading = false) }
                        } else {
                            _uiState.update { it.copy(isLoading = false, error = "Nie można załadować planu") }
                        }
                    } else {
                        _uiState.update { it.copy(
                            mode = WorkoutMode.EXERCISE_PICKER,
                            isLoading = false,
                            startTime = System.currentTimeMillis()
                        ) }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoading = false, error = "Błąd: ${e.message}") }
                }
            }
        }
    }

    // ==================== Session Resume ====================

    private suspend fun resumeSession(sessionId: Long) {
        val session = workoutRepository.getSession(sessionId) ?: return
        if (session.status != "active") return

        localSessionId = sessionId
        serverSessionId = session.serverSessionId

        // Load completed sets from Room
        val sets = workoutRepository.getSetsForSessionOnce(sessionId)
        val completedSnapshots = mutableListOf<CompletedSetSnapshot>()

        for (set in sets) {
            if (set.isCompleted) {
                val exercise = exerciseRepository.getById(set.exerciseId)
                val reps = workoutRepository.getRepsForSetOnce(set.id)
                completedSnapshots.add(CompletedSetSnapshot(
                    setNumber = set.setNumber,
                    exerciseName = exercise?.name ?: "Ćwiczenie",
                    loadKg = set.actualLoadKg,
                    reps = reps
                ))
            } else {
                // The last (active) set – continue from here
                currentSetId = set.id
                currentLocalExerciseId = set.exerciseId
                val exercise = exerciseRepository.getById(set.exerciseId)
                val existingReps = workoutRepository.getRepsForSetOnce(set.id)
                _uiState.update { state ->
                    state.copy(
                        mode = WorkoutMode.ACTIVE,
                        currentExerciseName = exercise?.name ?: "Ćwiczenie",
                        currentLoadKg = set.actualLoadKg,
                        currentSetIndex = set.setNumber - 1,
                        completedRepsInSet = existingReps,
                        completedSets = completedSnapshots,
                        startTime = session.startedAt
                    )
                }
            }
        }

        if (sets.isEmpty()) {
            // Empty resumed session – go to exercise picker
            _uiState.update { it.copy(
                mode = WorkoutMode.EXERCISE_PICKER,
                startTime = session.startedAt
            ) }
        }
    }

    // ==================== Session Management ====================

    fun startSession() {
        viewModelScope.launch {
            val role = authRepository.getCurrentRole().firstOrNull()
            if (role == "coach") {
                _uiState.update { it.copy(mode = WorkoutMode.SESSION_SELECT, startTime = System.currentTimeMillis()) }
            } else {
                selectSessionAthlete(null)
            }
        }
    }

    fun selectSessionAthlete(athlete: UserDto?) {
        _uiState.update {
            it.copy(
                sessionAthleteId = athlete?.id,
                sessionAthleteName = athlete?.username ?: "You",
                mode = WorkoutMode.EXERCISE_PICKER,
                startTime = System.currentTimeMillis()
            )
        }
    }

    // ==================== Freestyle & Plan ====================

    fun startFreestyle(exercise: ExerciseDto, loadKg: Float) {
        viewModelScope.launch {
            _currentVelocityBuffer.value.clear()
            peakVelocity = 0f
            resetHeartRateStats()
            queuedRepNumbersInSet.clear()

            val athleteId = _uiState.value.sessionAthleteId

            // 1. Create local session for crash-safety
            localSessionId = workoutRepository.createActiveSession(null, athleteId)

            // 2. Resolve local exercise ID
            currentLocalExerciseId = exerciseRepository.findOrCreateByName(exercise.name, exercise.category)

            // 3. Create first set in Room
            currentSetId = workoutRepository.createSet(localSessionId, currentLocalExerciseId, 1, loadKg)

            // 4. Try to start server session (non-blocking, fire-and-forget)
            launchLiveSessionStart(null, athleteId)

            _uiState.update { state ->
                state.copy(
                    mode = WorkoutMode.ACTIVE,
                    currentExerciseName = exercise.name,
                    currentLoadKg = loadKg,
                    targetReps = 0,
                    completedRepsInSet = emptyList(),
                    peakVelocity = 0f,
                    currentVelocity = 0f,
                    startTime = System.currentTimeMillis()
                )
            }

            sendExerciseParams(exercise.mvt)
        }
    }

    // Progi detekcji powtórzenia wysyłane do urządzenia: krytyczne minimum
    // prędkości i rozwinięcia linki per-ćwiczenie (edytowalne na liście
    // ćwiczeń), poniżej których ruch traktowany jest jako szum - np.
    // wyciągnięcie linki podczas odkładania sztangi na stojaki po serii.
    // Fallback: wartości wyprowadzone z MVT ćwiczenia lub bezpieczne stałe.
    private suspend fun sendExerciseParams(mvt: Float?) {
        val local = if (currentLocalExerciseId > 0) exerciseRepository.getById(currentLocalExerciseId) else null
        bleManager.setExerciseParams(
            minLiftVel = local?.defaultMinLiftVelocity?.takeIf { it > 0f } ?: mvt?.times(0.5f) ?: 0.3f,
            endLiftVel = local?.defaultEndLiftVelocity?.takeIf { it > 0f } ?: mvt?.times(0.7f) ?: 0.6f,
            minRepDist = local?.defaultMinRepDistance?.takeIf { it > 0f } ?: 0.15f
        )
    }

    fun startPlanWorkout(plan: TrainingPlanDto) {
        viewModelScope.launch {
            try {
                val athleteId = _uiState.value.sessionAthleteId
                resetHeartRateStats()

                // Create local session for crash-safety
                localSessionId = workoutRepository.createActiveSession(null, athleteId)

                // Try to start server session
                launchLiveSessionStart(plan.id, athleteId)

                _uiState.update { state ->
                    state.copy(
                        selectedPlan = plan,
                        mode = WorkoutMode.ACTIVE,
                        currentExerciseIndex = 0,
                        currentSetIndex = 0,
                        startTime = System.currentTimeMillis()
                    )
                }

                if (plan.exercises.isNotEmpty()) {
                    selectExerciseFromPlan(0)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun selectExerciseFromPlan(exerciseIndex: Int) {
        viewModelScope.launch {
            val plan = _uiState.value.selectedPlan ?: return@launch
            if (exerciseIndex >= plan.exercises.size) return@launch

            val planExercise = plan.exercises[exerciseIndex]
            val exercise = planExercise.exercise ?: return@launch
            val firstSet = planExercise.sets.firstOrNull() ?: return@launch

            _currentVelocityBuffer.value.clear()
            peakVelocity = 0f
            queuedRepNumbersInSet.clear()

            // Create/find local exercise and set if session is active
            if (localSessionId > 0) {
                currentLocalExerciseId = exerciseRepository.findOrCreateByName(exercise.name, exercise.category)
                val state = _uiState.value
                val newSetNumber = state.currentSetIndex + 1
                currentSetId = workoutRepository.createSet(localSessionId, currentLocalExerciseId, newSetNumber, firstSet.loadKg)
            }

            _uiState.update { state ->
                state.copy(
                    mode = WorkoutMode.ACTIVE,
                    currentExerciseIndex = exerciseIndex,
                    currentSetIndex = 0,
                    currentExerciseName = exercise.name,
                    currentLoadKg = firstSet.loadKg,
                    targetReps = firstSet.reps,
                    completedRepsInSet = emptyList(),
                    peakVelocity = 0f,
                    currentVelocity = 0f,
                    startTime = System.currentTimeMillis()
                )
            }

            sendExerciseParams(exercise.mvt)
        }
    }

    // ==================== Rep Processing ====================

    fun onRepReceived(rep: RepFromDevice) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isPaused || state.mode != WorkoutMode.ACTIVE) return@launch

            // Moc = siła * ŚREDNIA prędkość koncentryczna (konwencja VBT dla "mean power");
            // peak velocity służy tylko do stref/wyświetlania, nie do mocy.
            val power = calculatePower.calculateMeanPower(state.currentLoadKg, rep.meanVelocityMs)
            val repNumber = state.completedRepsInSet.size + 1

            val repEntity = RepResultEntity(
                id = 0,
                sessionSetId = currentSetId,
                repNumber = repNumber,
                maxVelocityMs = rep.meanVelocityMs,
                peakVelocityMs = rep.peakVelocityMs,
                distanceM = rep.distanceM,
                durationMs = rep.durationMs,
                powerW = power,
                deviceRepIndex = rep.repIndex,
                deviceTimestamp = rep.deviceTimestamp,
                recordedAt = System.currentTimeMillis(),
                isDeleted = false
            )

            // Save to Room immediately (crash-safe)
            if (currentSetId > 0) {
                workoutRepository.addRep(
                    sessionSetId = currentSetId,
                    repNumber = repNumber,
                    meanVelocityMs = rep.meanVelocityMs,
                    peakVelocityMs = rep.peakVelocityMs,
                    distanceM = rep.distanceM,
                    durationMs = rep.durationMs,
                    powerW = power,
                    deviceRepIndex = rep.repIndex,
                    deviceTimestamp = rep.deviceTimestamp
                )
            }

            // Queue for live server push (resolve/auto-create matching server exercise)
            val servId = serverSessionId
            if (servId != null) {
                val serverEx = syncManager.resolveServerExercise(state.currentExerciseName, category = null)
                if (serverEx != null) {
                    syncManager.queueRep(RepResultDto(
                        id = null,
                        sessionId = servId,
                        exerciseId = serverEx.id,
                        setNumber = state.currentSetIndex + 1,
                        repNumber = repNumber,
                        meanVelocity = rep.meanVelocityMs.toDouble(),
                        peakVelocity = rep.peakVelocityMs.toDouble(),
                        loadKg = state.currentLoadKg.toDouble(),
                        powerWatts = power.toDouble(),
                        estimated1rm = estimate1RM.estimate(state.currentLoadKg, rep.meanVelocityMs, repNumber),
                        timestamp = null
                    ), deviceTimestamp = rep.deviceTimestamp)
                    queuedRepNumbersInSet.add(repNumber)
                }
            }

            _uiState.update { current ->
                current.copy(
                    completedRepsInSet = current.completedRepsInSet + repEntity,
                    peakVelocity = maxOf(current.peakVelocity, rep.peakVelocityMs),
                    allReps = current.allReps + repEntity
                )
            }

            val newState = _uiState.value
            if (newState.targetReps > 0 && newState.completedRepsInSet.size >= newState.targetReps) {
                resetAutoFinishTimer()
            }
        }
    }

    fun onVelocityUpdate(velocity: Float) {
        _uiState.update { it.copy(currentVelocity = velocity) }
    }

    fun addVelocityPoint(timestampMs: Long, velocityMs: Float) {
        _currentVelocityBuffer.value.add(VelocityPoint(timestampMs, velocityMs))
        _uiState.update {
            it.copy(currentRepVelocityBuffer = _currentVelocityBuffer.value.toList())
        }
    }

    // ==================== Load & Set Editing ====================

    fun editCurrentLoad(newKg: Float) {
        _uiState.update { it.copy(currentLoadKg = newKg) }
        viewModelScope.launch {
            // Utrwal nowy ciężar w Room - bez tego offline-sync i Historia
            // widziałyby ciężar z momentu utworzenia serii.
            if (currentSetId > 0) {
                workoutRepository.updateSetLoad(currentSetId, newKg)
            }

            // Edycja w trakcie serii to korekta pomyłki przy wpisywaniu -
            // przelicz moc powtórzeń już zarejestrowanych w tej serii.
            if (_uiState.value.completedRepsInSet.isNotEmpty()) {
                if (currentSetId > 0) {
                    workoutRepository.getRepsForSetOnce(currentSetId).forEach { rep ->
                        workoutRepository.updateRep(
                            rep.copy(powerW = calculatePower.calculateMeanPower(newKg, rep.maxVelocityMs))
                        )
                    }
                }
                _uiState.update { state ->
                    val recalc = { rep: RepResultEntity ->
                        if (rep.sessionSetId == currentSetId)
                            rep.copy(powerW = calculatePower.calculateMeanPower(newKg, rep.maxVelocityMs))
                        else rep
                    }
                    state.copy(
                        completedRepsInSet = state.completedRepsInSet.map(recalc),
                        allReps = state.allReps.map(recalc)
                    )
                }
                // Popraw też powtórzenia czekające w kolejce live-sync
                serverSessionId?.let { servId ->
                    syncManager.updatePendingReps(servId, _uiState.value.currentSetIndex + 1) { dto ->
                        dto.copy(
                            loadKg = newKg.toDouble(),
                            powerWatts = calculatePower.calculateMeanPower(newKg, dto.meanVelocity.toFloat()).toDouble(),
                            estimated1rm = estimate1RM.estimate(newKg, dto.meanVelocity.toFloat(), dto.repNumber)
                        )
                    }
                }
            }
        }
    }

    fun addExtraSet() {
        viewModelScope.launch {
            _uiState.update { state ->
                val currentIndex = state.currentExerciseIndex
                val exercises = state.planExercises.toMutableList()
                if (currentIndex < exercises.size) {
                    val updated = exercises[currentIndex].copy(
                        additionalSets = exercises[currentIndex].additionalSets + 1
                    )
                    exercises[currentIndex] = updated
                    state.copy(planExercises = exercises)
                } else {
                    state
                }
            }
        }
    }

    // ==================== Set/Workout Control ====================

    fun togglePause() {
        val wasPaused = _uiState.value.isPaused
        if (!wasPaused) {
            cancelAutoFinish()
        }
        _uiState.update { it.copy(isPaused = !wasPaused) }
    }

    fun finishSet() {
        cancelAutoFinish()
        queuedRepNumbersInSet.clear()
        val state = _uiState.value
        val snapshot = CompletedSetSnapshot(
            setNumber = state.currentSetIndex + 1,
            exerciseName = state.currentExerciseName,
            loadKg = state.currentLoadKg,
            reps = state.completedRepsInSet
        )

        viewModelScope.launch {
            // Finish current set in Room
            if (currentSetId > 0) {
                workoutRepository.finishSet(currentSetId)
            }

            // Push pending reps to server live
            serverSessionId?.let { servId ->
                syncManager.flushPendingReps(servId)
            }

            // Create next set in Room
            val newSetNumber = state.currentSetIndex + 2
            if (localSessionId > 0) {
                currentSetId = workoutRepository.createSet(localSessionId, currentLocalExerciseId, newSetNumber, state.currentLoadKg)
            }
        }

        _uiState.update {
            it.copy(
                completedSets = it.completedSets + snapshot,
                completedRepsInSet = emptyList(),
                peakVelocity = 0f,
                setFinished = true,
                autoFinishCountdown = null,
                currentSetIndex = it.currentSetIndex + 1
            )
        }
    }

    fun finishWorkout() {
        cancelAutoFinish()
        // Ekran nawiguje wstecz od razu po finishWorkout(), co kasuje ViewModel
        // i anuluje viewModelScope - bez NonCancellable finalizacja urywała się
        // po pierwszym suspend i ostatnia seria nie trafiała na serwer.
        // Dispatchers.Main.immediate gwarantuje, że blok wystartuje (i wejdzie
        // w NonCancellable) jeszcze przed obsłużeniem nawigacji.
        viewModelScope.launch {
            withContext(NonCancellable) {
                val state = _uiState.value

                // Ostatnia (niedomknięta) seria do podsumowania na ekranie FINISHED
                if (state.completedRepsInSet.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            completedSets = it.completedSets + CompletedSetSnapshot(
                                setNumber = state.currentSetIndex + 1,
                                exerciseName = state.currentExerciseName,
                                loadKg = state.currentLoadKg,
                                reps = state.completedRepsInSet
                            )
                        )
                    }
                }

                // Finish current set in Room (if has reps)
                if (currentSetId > 0 && state.completedRepsInSet.isNotEmpty()) {
                    workoutRepository.finishSet(currentSetId)
                }

                // Update session status to finished in Room
                if (localSessionId > 0) {
                    workoutRepository.finishSession(localSessionId)
                    // Zapisz statystyki tętna (tylko lokalnie - backend nie ma pól HR)
                    if (hrCount > 0) {
                        workoutRepository.updateSessionHeartRate(
                            sessionId = localSessionId,
                            avgHeartRate = (hrSum / hrCount).toInt(),
                            maxHeartRate = hrMax
                        )
                    }
                }

                // Finalize server session
                val servId = serverSessionId
                if (servId != null) {
                    // Dokolejkuj TYLKO powtórzenia, które nie trafiły do kolejki na
                    // bieżąco w onRepReceived (np. serverSessionId pojawił się w
                    // trakcie serii) - reszta już tam czeka i poszłaby podwójnie.
                    val unsentReps = state.completedRepsInSet.filter { it.repNumber !in queuedRepNumbersInSet }
                    if (unsentReps.isNotEmpty()) {
                        val serverEx = syncManager.resolveServerExercise(state.currentExerciseName, category = null)
                        if (serverEx != null) {
                            unsentReps.forEach { rep ->
                                syncManager.queueRep(RepResultDto(
                                    id = null, sessionId = servId,
                                    exerciseId = serverEx.id,
                                    setNumber = state.currentSetIndex + 1,
                                    repNumber = rep.repNumber,
                                    meanVelocity = rep.maxVelocityMs.toDouble(),
                                    peakVelocity = effectivePeak(rep).toDouble(),
                                    loadKg = state.currentLoadKg.toDouble(),
                                    powerWatts = rep.powerW.toDouble(),
                                    estimated1rm = estimate1RM.estimate(
                                        state.currentLoadKg,
                                        rep.maxVelocityMs,
                                        state.completedRepsInSet.size
                                    ),
                                    timestamp = null
                                ), deviceTimestamp = rep.deviceTimestamp)
                            }
                        }
                    }
                    // Push final reps + mark session as finished
                    val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                    val flushed = syncManager.flushPendingReps(servId, finishedAt = now)
                    if (!flushed) {
                        Log.w(TAG, "finishWorkout: nie udało się domknąć sesji live $servId na serwerze")
                        _uiState.update { it.copy(error = "Nie udało się wysłać końcówki treningu - dane są zapisane lokalnie") }
                    }
                } else {
                    // No live session on server – do a full sync now (from Room)
                    val synced = localSessionId > 0 && syncManager.syncSession(localSessionId)
                    if (!synced) {
                        Log.w(TAG, "finishWorkout: synchronizacja offline nieudana - kolejkuję SessionSyncWorker")
                        SessionSyncWorker.enqueue(appContext)
                        _uiState.update {
                            it.copy(error = "Brak połączenia - trening zapisany lokalnie, zostanie zsynchronizowany automatycznie")
                        }
                    }
                }

                _uiState.update { it.copy(mode = WorkoutMode.FINISHED) }
            }
        }
    }

    fun requestExerciseChange() {
        cancelAutoFinish()
        _uiState.update { it.copy(showChangeExercise = true, isPaused = true) }
    }

    fun changeExercise(exercise: ExerciseDto, loadKg: Float) {
        viewModelScope.launch {
            // Finish current set in Room before switching
            if (currentSetId > 0 && _uiState.value.completedRepsInSet.isNotEmpty()) {
                workoutRepository.finishSet(currentSetId)
            }

            _currentVelocityBuffer.value.clear()
            peakVelocity = 0f
            queuedRepNumbersInSet.clear()

            // Create new set in Room for the new exercise
            if (localSessionId > 0) {
                currentLocalExerciseId = exerciseRepository.findOrCreateByName(exercise.name, exercise.category)
                val newSetNumber = _uiState.value.currentSetIndex + 2
                currentSetId = workoutRepository.createSet(localSessionId, currentLocalExerciseId, newSetNumber, loadKg)
            }

            _uiState.update { state ->
                state.copy(
                    showChangeExercise = false,
                    isPaused = false,
                    currentExerciseName = exercise.name,
                    currentLoadKg = loadKg,
                    targetReps = 0,
                    completedRepsInSet = emptyList(),
                    peakVelocity = 0f,
                    currentVelocity = 0f,
                    currentSetIndex = state.currentSetIndex + 1
                )
            }
            exercise.mvt?.let { mvt ->
                bleManager.setExerciseParams(
                    minLiftVel = mvt * 0.5f,
                    endLiftVel = mvt * 0.7f,
                    minRepDist = 0.15f
                )
            }
        }
    }

    fun cancelExerciseChange() {
        _uiState.update { it.copy(showChangeExercise = false, isPaused = false) }
    }

    fun reconnectBle() {
        bleManager.reconnect()
    }

    // ==================== Data Loading ====================

    fun loadAvailableExercises() {
        viewModelScope.launch {
            try {
                val exercises = api.getExercises().body() ?: emptyList()
                _uiState.update { it.copy(availableExercises = listOf(FREE_MEASUREMENT_EXERCISE) + exercises) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Brak połączenia z serwerem - lista ćwiczeń mogła nie zostać odświeżona") }
            }
        }
    }

    fun loadAvailablePlans() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val plans = api.getPlans().body() ?: emptyList()
                _uiState.update { it.copy(availablePlans = plans, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    // ==================== Server Sync ====================

    private fun launchLiveSessionStart(planId: Int?, athleteId: Int?) {
        viewModelScope.launch {
            serverSessionId = syncManager.startLiveSession(localSessionId, planId, athleteId)
        }
    }

    // ==================== Helpers ====================

    // Rekordy sprzed peak velocity (stary firmware / stara baza) mają
    // peakVelocityMs == 0 - wtedy najlepszym przybliżeniem jest mean velocity.
    private fun effectivePeak(rep: RepResultEntity): Float =
        if (rep.peakVelocityMs > 0f) rep.peakVelocityMs else rep.maxVelocityMs

    private fun resetHeartRateStats() {
        hrSum = 0
        hrCount = 0
        hrMax = 0
    }

    // Mapuje lokalną encję Room na ExerciseDto (sam kształt UI używa), żeby
    // exercise picker mógł działać w pełni offline. id jest ujemne (-localId)
    // aby oznaczyć "brak potwierdzonego ID serwera" - nie jest używane do
    // wysyłki powtórzeń (patrz WorkoutSyncManager.resolveServerExercise),
    // więc kolizje nie mają znaczenia.
    private fun ExerciseDefinitionEntity.toExerciseDto(): ExerciseDto = ExerciseDto(
        id = -id.toInt(),
        name = name,
        category = category,
        mvt = null,
        description = null
    )

    // Auto-zamykanie serii: po osiągnięciu targetReps odlicza AUTO_FINISH_SECONDS
    // sekund (widoczne w UI z przyciskiem "Anuluj"); nowe powtórzenie resetuje
    // odliczanie (ponowne wywołanie), a cancelAutoFinish() je przerywa.
    private fun resetAutoFinishTimer() {
        autoFinishJob?.cancel()
        autoFinishJob = viewModelScope.launch {
            for (remaining in AUTO_FINISH_SECONDS downTo 1) {
                _uiState.update { it.copy(autoFinishCountdown = remaining) }
                delay(1000)
            }
            _uiState.update { it.copy(autoFinishCountdown = null) }
            finishSet()
        }
    }

    fun cancelAutoFinish() {
        autoFinishJob?.cancel()
        autoFinishJob = null
        _uiState.update { it.copy(autoFinishCountdown = null) }
    }

    override fun onCleared() {
        super.onCleared()
        autoFinishJob?.cancel()
        repCollectionJob?.cancel()
        WorkoutForegroundService.stop(appContext)
    }
}
