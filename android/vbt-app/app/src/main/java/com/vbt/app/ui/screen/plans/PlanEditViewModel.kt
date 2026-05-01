package com.vbt.app.ui.screen.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.remote.ApiService
import com.vbt.app.data.remote.ExerciseDto
import com.vbt.app.data.remote.UserDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanEditUiState(
    val planId: Int? = null,
    val name: String = "",
    val description: String = "",
    val assignedToId: Int? = null,
    val isTemplate: Boolean = false,
    val exercises: List<PlanExerciseState> = emptyList(),
    val availableExercises: List<ExerciseDto> = emptyList(),
    val availableAthletes: List<UserDto> = emptyList(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

data class PlanExerciseState(
    val exerciseId: Int = 0,
    val exerciseName: String = "",
    val orderIndex: Int = 0,
    val notes: String = "",
    val sets: List<PlanSetState> = listOf(PlanSetState())
)

data class PlanSetState(
    val setNumber: Int = 1,
    val reps: Int = 5,
    val loadKg: Float = 100f,
    val restSeconds: Int = 180,
    val targetVelMin: Float? = null,
    val targetVelMax: Float? = null
)

@HiltViewModel
class PlanEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val apiService: ApiService
) : ViewModel() {

    private val planId: Int? = savedStateHandle.get<Int>("planId")

    private val _uiState = MutableStateFlow(PlanEditUiState())
    val uiState: StateFlow<PlanEditUiState> = _uiState.asStateFlow()

    init {
        loadExercisesAndAthletes()
        if (planId != null) {
            loadPlan(planId)
        }
    }

    fun loadPlan(planId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.getPlan(planId)
                if (response.isSuccessful && response.body() != null) {
                    val plan = response.body()!!
                    val exercises = plan.exercises.map { pe ->
                        PlanExerciseState(
                            exerciseId = pe.exerciseId,
                            exerciseName = pe.exercise?.name ?: "",
                            orderIndex = pe.orderIndex,
                            notes = pe.notes ?: "",
                            sets = pe.sets.map { pse ->
                                PlanSetState(
                                    setNumber = pse.setNumber,
                                    reps = pse.reps,
                                    loadKg = pse.loadKg,
                                    restSeconds = pse.restSeconds,
                                    targetVelMin = pse.targetVelocityMin,
                                    targetVelMax = pse.targetVelocityMax
                                )
                            }
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        planId = plan.id,
                        name = plan.name,
                        description = plan.description ?: "",
                        assignedToId = plan.assignedTo,
                        isTemplate = plan.isTemplate,
                        exercises = exercises,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load plan"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun loadExercisesAndAthletes() {
        viewModelScope.launch {
            try {
                val exercisesResponse = apiService.getExercises()
                val athletesResponse = apiService.getAthletes()

                val exercises = exercisesResponse.body() ?: emptyList()
                val athletes = athletesResponse.body() ?: emptyList()

                _uiState.value = _uiState.value.copy(
                    availableExercises = exercises,
                    availableAthletes = athletes
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to load data"
                )
            }
        }
    }

    fun updateName(value: String) {
        _uiState.value = _uiState.value.copy(name = value)
    }

    fun updateDescription(value: String) {
        _uiState.value = _uiState.value.copy(description = value)
    }

    fun updateAssignedToId(athleteId: Int?) {
        _uiState.value = _uiState.value.copy(assignedToId = athleteId)
    }

    fun updateIsTemplate(isTemplate: Boolean) {
        _uiState.value = _uiState.value.copy(isTemplate = isTemplate)
    }

    fun addExercise() {
        val currentExercises = _uiState.value.exercises
        val newExercise = PlanExerciseState(
            orderIndex = currentExercises.size
        )
        _uiState.value = _uiState.value.copy(
            exercises = currentExercises + newExercise
        )
    }

    fun removeExercise(idx: Int) {
        val currentExercises = _uiState.value.exercises
        if (idx in currentExercises.indices) {
            val updated = currentExercises.filterIndexed { index, _ -> index != idx }
                .mapIndexed { index, exercise -> exercise.copy(orderIndex = index) }
            _uiState.value = _uiState.value.copy(exercises = updated)
        }
    }

    fun updateExercise(idx: Int, exerciseId: Int, exerciseName: String) {
        val currentExercises = _uiState.value.exercises
        if (idx in currentExercises.indices) {
            val updated = currentExercises.toMutableList()
            updated[idx] = updated[idx].copy(
                exerciseId = exerciseId,
                exerciseName = exerciseName
            )
            _uiState.value = _uiState.value.copy(exercises = updated)
        }
    }

    fun addSet(exerciseIdx: Int) {
        val currentExercises = _uiState.value.exercises
        if (exerciseIdx in currentExercises.indices) {
            val exercise = currentExercises[exerciseIdx]
            val newSetNumber = exercise.sets.maxOfOrNull { it.setNumber }?.plus(1) ?: 1
            val newSet = PlanSetState(setNumber = newSetNumber)
            val updated = currentExercises.toMutableList()
            updated[exerciseIdx] = exercise.copy(sets = exercise.sets + newSet)
            _uiState.value = _uiState.value.copy(exercises = updated)
        }
    }

    fun removeSet(exerciseIdx: Int, setIdx: Int) {
        val currentExercises = _uiState.value.exercises
        if (exerciseIdx in currentExercises.indices) {
            val exercise = currentExercises[exerciseIdx]
            if (setIdx in exercise.sets.indices) {
                val updated = currentExercises.toMutableList()
                val sets = exercise.sets.filterIndexed { index, _ -> index != setIdx }
                    .mapIndexed { index, set -> set.copy(setNumber = index + 1) }
                updated[exerciseIdx] = exercise.copy(sets = sets)
                _uiState.value = _uiState.value.copy(exercises = updated)
            }
        }
    }

    fun updateSet(exerciseIdx: Int, setIdx: Int, field: String, value: Any) {
        val currentExercises = _uiState.value.exercises
        if (exerciseIdx in currentExercises.indices) {
            val exercise = currentExercises[exerciseIdx]
            if (setIdx in exercise.sets.indices) {
                val set = exercise.sets[setIdx]
                val updatedSet = when (field) {
                    "reps" -> set.copy(reps = (value as? Int) ?: set.reps)
                    "loadKg" -> set.copy(loadKg = (value as? Float) ?: set.loadKg)
                    "restSeconds" -> set.copy(restSeconds = (value as? Int) ?: set.restSeconds)
                    "targetVelMin" -> set.copy(targetVelMin = (value as? Float))
                    "targetVelMax" -> set.copy(targetVelMax = (value as? Float))
                    else -> set
                }
                val updated = currentExercises.toMutableList()
                val sets = exercise.sets.toMutableList()
                sets[setIdx] = updatedSet
                updated[exerciseIdx] = exercise.copy(sets = sets)
                _uiState.value = _uiState.value.copy(exercises = updated)
            }
        }
    }

    fun createNewExercise(name: String, category: String) {
        viewModelScope.launch {
            try {
                val request = com.vbt.app.data.remote.CreateExerciseRequest(
                    name = name,
                    category = category,
                    mvt = null,
                    description = null
                )
                val response = apiService.createExercise(request)
                if (response.isSuccessful && response.body() != null) {
                    val newExercise = response.body()!!
                    val updated = _uiState.value.availableExercises + newExercise
                    _uiState.value = _uiState.value.copy(availableExercises = updated)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to create exercise")
            }
        }
    }

    fun savePlan(onSaved: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val currentState = _uiState.value
                val planExerciseDtos = currentState.exercises.map { exercise ->
                    com.vbt.app.data.remote.PlanExerciseDto(
                        id = null,
                        exerciseId = exercise.exerciseId,
                        orderIndex = exercise.orderIndex,
                        notes = exercise.notes.ifBlank { null },
                        sets = exercise.sets.map { set ->
                            com.vbt.app.data.remote.PlanSetDto(
                                id = null,
                                setNumber = set.setNumber,
                                reps = set.reps,
                                loadKg = set.loadKg,
                                loadPercent1rm = null,
                                targetVelocityMin = set.targetVelMin,
                                targetVelocityMax = set.targetVelMax,
                                restSeconds = set.restSeconds
                            )
                        },
                        exercise = null
                    )
                }

                val createPlanRequest = com.vbt.app.data.remote.CreatePlanRequest(
                    name = currentState.name.ifBlank { "New Plan" },
                    description = currentState.description.ifBlank { null },
                    assignedTo = currentState.assignedToId,
                    isTemplate = currentState.isTemplate,
                    exercises = planExerciseDtos
                )

                val response = if (currentState.planId != null) {
                    apiService.updatePlan(currentState.planId, createPlanRequest)
                } else {
                    apiService.createPlan(createPlanRequest)
                }

                if (response.isSuccessful && response.body() != null) {
                    val savedPlan = response.body()!!
                    _uiState.value = _uiState.value.copy(
                        planId = savedPlan.id,
                        isLoading = false,
                        isSaved = true,
                        error = null
                    )
                    onSaved()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to save plan"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }
}
