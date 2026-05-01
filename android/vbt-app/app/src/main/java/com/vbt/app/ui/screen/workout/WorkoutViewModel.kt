package com.vbt.app.ui.screen.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.ble.BleConnectionState
import com.vbt.app.data.ble.RepFromDevice
import com.vbt.app.data.ble.VbtBleManager
import com.vbt.app.data.local.entity.*
import com.vbt.app.data.remote.ApiService
import com.vbt.app.data.remote.AppendRepsRequest
import com.vbt.app.data.remote.ExerciseDto
import com.vbt.app.data.remote.RepResultDto
import com.vbt.app.data.remote.StartLiveSessionRequest
import com.vbt.app.data.remote.TrainingPlanDto
import com.vbt.app.data.remote.UserDto
import com.vbt.app.data.repository.AuthRepository
import com.vbt.app.data.repository.ExerciseRepository
import com.vbt.app.data.repository.TrainingPlanRepository
import com.vbt.app.data.repository.WorkoutRepository
import com.vbt.app.domain.model.VelocityZone
import com.vbt.app.domain.usecase.CalculatePowerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val workoutRepository: WorkoutRepository,
    private val planRepository: TrainingPlanRepository,
    private val exerciseRepository: ExerciseRepository,
    private val authRepository: AuthRepository,
    private val bleManager: VbtBleManager,
    private val calculatePower: CalculatePowerUseCase,
    private val api: ApiService
) : ViewModel() {

    // Local Room session (always created at workout start for crash safety)
    private var localSessionId: Long = 0
    // Current active set ID in Room
    private var currentSetId: Long = 0
    // Local exercise definition ID for current exercise
    private var currentLocalExerciseId: Long = 0
    // Server-assigned session ID (null when offline)
    private var serverSessionId: Int? = null
    // Reps collected since last server push (pending for next finishSet or finishWorkout)
    private val pendingServerReps = mutableListOf<RepResultDto>()

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
            try {
                val dtoExercises = api.getExercises().body() ?: emptyList()
                _uiState.update { it.copy(availableExercises = dtoExercises) }
            } catch (_: Exception) {
                _uiState.update { it.copy(availableExercises = emptyList()) }
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
                    } catch (_: Exception) {}
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

            exercise.mvt?.let { mvt ->
                bleManager.setExerciseParams(
                    minLiftVel = mvt * 0.5f,
                    endLiftVel = mvt * 0.7f,
                    minRepDist = 0.15f
                )
            }
        }
    }

    fun startPlanWorkout(plan: TrainingPlanDto) {
        viewModelScope.launch {
            try {
                val athleteId = _uiState.value.sessionAthleteId

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

            bleManager.setExerciseParams(
                minLiftVel = exercise.mvt?.times(0.5f) ?: 0.3f,
                endLiftVel = exercise.mvt?.times(0.7f) ?: 0.6f,
                minRepDist = 0.15f
            )
        }
    }

    // ==================== Rep Processing ====================

    fun onRepReceived(rep: RepFromDevice) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isPaused || state.mode != WorkoutMode.ACTIVE) return@launch

            val power = calculatePower.calculatePeakPower(state.currentLoadKg, rep.maxVelocityMs)

            val repEntity = RepResultEntity(
                id = 0,
                sessionSetId = currentSetId,
                repNumber = state.completedRepsInSet.size + 1,
                maxVelocityMs = rep.maxVelocityMs,
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
                    repNumber = repEntity.repNumber,
                    maxVelocityMs = rep.maxVelocityMs,
                    distanceM = rep.distanceM,
                    durationMs = rep.durationMs,
                    powerW = power,
                    deviceRepIndex = rep.repIndex,
                    deviceTimestamp = rep.deviceTimestamp
                )
            }

            // Queue for live server push (use cached exercises - no API call needed)
            val servId = serverSessionId
            if (servId != null) {
                val serverEx = state.availableExercises.find { it.name.lowercase() == state.currentExerciseName.lowercase() }
                if (serverEx != null) {
                    pendingServerReps.add(RepResultDto(
                        id = null,
                        sessionId = servId,
                        exerciseId = serverEx.id,
                        setNumber = state.currentSetIndex + 1,
                        repNumber = repEntity.repNumber,
                        meanVelocity = rep.maxVelocityMs.toDouble(),
                        peakVelocity = rep.maxVelocityMs.toDouble(),
                        loadKg = state.currentLoadKg.toDouble(),
                        powerWatts = power.toDouble(),
                        estimated1rm = calculateEstimated1RM(state.currentLoadKg, repEntity.repNumber),
                        timestamp = null
                    ))
                }
            }

            _uiState.update { current ->
                current.copy(
                    completedRepsInSet = current.completedRepsInSet + repEntity,
                    peakVelocity = maxOf(current.peakVelocity, rep.maxVelocityMs),
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
            autoFinishJob?.cancel()
        }
        _uiState.update { it.copy(isPaused = !wasPaused) }
    }

    fun finishSet() {
        autoFinishJob?.cancel()
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
            val servId = serverSessionId
            if (servId != null && pendingServerReps.isNotEmpty()) {
                pushRepsToServer(servId, pendingServerReps.toList())
                pendingServerReps.clear()
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
                currentSetIndex = it.currentSetIndex + 1
            )
        }
    }

    fun finishWorkout() {
        autoFinishJob?.cancel()
        viewModelScope.launch {
            val state = _uiState.value

            // Finish current set in Room (if has reps)
            if (currentSetId > 0 && state.completedRepsInSet.isNotEmpty()) {
                workoutRepository.finishSet(currentSetId)
            }

            // Update session status to finished in Room
            if (localSessionId > 0) {
                workoutRepository.finishSession(localSessionId)
            }

            // Finalize server session
            val servId = serverSessionId
            if (servId != null) {
                // Collect any remaining in-progress reps (use cached exercises)
                val serverEx = state.availableExercises.find { it.name.lowercase() == state.currentExerciseName.lowercase() }
                if (serverEx != null && state.completedRepsInSet.isNotEmpty()) {
                    state.completedRepsInSet.forEach { rep ->
                        pendingServerReps.add(RepResultDto(
                            id = null, sessionId = servId,
                            exerciseId = serverEx.id,
                            setNumber = state.currentSetIndex + 1,
                            repNumber = rep.repNumber,
                            meanVelocity = rep.maxVelocityMs.toDouble(),
                            peakVelocity = rep.maxVelocityMs.toDouble(),
                            loadKg = state.currentLoadKg.toDouble(),
                            powerWatts = rep.powerW.toDouble(),
                            estimated1rm = calculateEstimated1RM(state.currentLoadKg, rep.repNumber),
                            timestamp = null
                        ))
                    }
                }
                // Push final reps + mark session as finished
                val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                pushRepsToServer(servId, pendingServerReps.toList(), finishedAt = now)
                pendingServerReps.clear()
            } else {
                // No live session on server – do a full sync now
                syncToServer()
            }

            _uiState.update { it.copy(mode = WorkoutMode.FINISHED) }
        }
    }

    fun discardWorkout() {
        autoFinishJob?.cancel()
        viewModelScope.launch {
            // Delete from Room
            if (localSessionId > 0) {
                workoutRepository.deleteSession(localSessionId)
                localSessionId = 0
            }
            // Delete from server
            serverSessionId?.let { servId ->
                try { api.deleteSession(servId) } catch (_: Exception) {}
                serverSessionId = null
            }
            pendingServerReps.clear()
        }
        _uiState.update { it.copy(mode = WorkoutMode.FINISHED) }
    }

    fun requestExerciseChange() {
        autoFinishJob?.cancel()
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
                _uiState.update { it.copy(availableExercises = exercises) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
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
            try {
                val request = StartLiveSessionRequest(
                    athleteId = athleteId,
                    planId = planId,
                    notes = null
                )
                val response = api.startLiveSession(request)
                if (response.isSuccessful) {
                    val servId = response.body()?.id
                    if (servId != null) {
                        serverSessionId = servId
                        workoutRepository.updateServerSessionId(localSessionId, servId)
                    }
                }
            } catch (_: Exception) {
                // Offline – session will be synced on finishWorkout
            }
        }
    }

    private suspend fun pushRepsToServer(servId: Int, reps: List<RepResultDto>, finishedAt: String? = null) {
        if (reps.isEmpty() && finishedAt == null) return
        try {
            api.appendReps(servId, AppendRepsRequest(reps = reps, finishedAt = finishedAt))
        } catch (_: Exception) {
            // Non-critical; reps are saved locally
        }
    }

    private suspend fun syncToServer() {
        try {
            val state = _uiState.value
            val serverExercises = api.getExercises().body() ?: emptyList()
            val serverExByName = serverExercises.associateBy { it.name.lowercase() }

            val allReps = mutableListOf<RepResultDto>()

            for (snapshot in state.completedSets) {
                val serverEx = serverExByName[snapshot.exerciseName.lowercase()] ?: continue
                snapshot.reps.forEach { rep ->
                    allReps.add(RepResultDto(
                        id = null, sessionId = null,
                        exerciseId = serverEx.id,
                        setNumber = snapshot.setNumber,
                        repNumber = rep.repNumber,
                        meanVelocity = rep.maxVelocityMs.toDouble(),
                        peakVelocity = rep.maxVelocityMs.toDouble(),
                        loadKg = snapshot.loadKg.toDouble(),
                        powerWatts = rep.powerW.toDouble(),
                        estimated1rm = calculateEstimated1RM(snapshot.loadKg, rep.repNumber),
                        timestamp = null
                    ))
                }
            }

            if (state.completedRepsInSet.isNotEmpty()) {
                val serverEx = serverExByName[state.currentExerciseName.lowercase()]
                if (serverEx != null) {
                    state.completedRepsInSet.forEach { rep ->
                        allReps.add(RepResultDto(
                            id = null, sessionId = null,
                            exerciseId = serverEx.id,
                            setNumber = state.currentSetIndex + 1,
                            repNumber = rep.repNumber,
                            meanVelocity = rep.maxVelocityMs.toDouble(),
                            peakVelocity = rep.maxVelocityMs.toDouble(),
                            loadKg = state.currentLoadKg.toDouble(),
                            powerWatts = rep.powerW.toDouble(),
                            estimated1rm = calculateEstimated1RM(state.currentLoadKg, rep.repNumber),
                            timestamp = null
                        ))
                    }
                }
            }

            if (allReps.isNotEmpty()) {
                val payload = com.vbt.app.data.remote.CreateSessionRequest(
                    athleteId = state.sessionAthleteId ?: 0,
                    planId = state.selectedPlan?.id,
                    calendarEntryId = null,
                    notes = state.selectedPlan?.name,
                    reps = allReps
                )
                api.createSession(payload)
            }
        } catch (_: Exception) {}
    }

    private fun calculateEstimated1RM(weight: Float, reps: Int): Double {
        return (weight * (1 + reps / 30.0)).toDouble()
    }

    // ==================== Helpers ====================

    private fun resetAutoFinishTimer() {
        autoFinishJob?.cancel()
        autoFinishJob = viewModelScope.launch {
            delay(2000)
            finishSet()
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoFinishJob?.cancel()
        repCollectionJob?.cancel()
    }
}
