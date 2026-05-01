package com.vbt.app.ui.screen.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.remote.ApiService
import com.vbt.app.data.remote.CalendarEntryDto
import com.vbt.app.data.repository.ExerciseRepository
import com.vbt.app.data.repository.TrainingPlanRepository
import com.vbt.app.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalendarUiState(
    val entries: List<CalendarEntryDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val syncError: String? = null,
    val isSyncing: Boolean = false
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val api: ApiService,
    private val workoutRepository: WorkoutRepository,
    private val planRepository: TrainingPlanRepository,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState

    init { loadCalendar() }

    fun loadCalendar() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = api.getCalendarEntries()
                if (response.isSuccessful) {
                    _uiState.value = CalendarUiState(entries = response.body() ?: emptyList())
                } else {
                    _uiState.value = CalendarUiState(error = "Failed to load calendar (${response.code()})")
                }
            } catch (e: Exception) {
                _uiState.value = CalendarUiState(error = "Connection error: ${e.message}")
            }
        }
    }

    fun startWorkout(entry: CalendarEntryDto, onSessionCreated: (Long) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncError = null)
            val localPlanId: Long? = if (entry.planId != null) {
                syncServerPlan(entry.planId)
            } else null
            _uiState.value = _uiState.value.copy(isSyncing = false)

            val sessionId = workoutRepository.createSession(planId = localPlanId)
            onSessionCreated(sessionId)
        }
    }

    private suspend fun syncServerPlan(serverPlanId: Int): Long? {
        return try {
            val response = api.getPlan(serverPlanId)
            if (!response.isSuccessful) return null
            val remotePlan = response.body() ?: return null

            val localPlanId = planRepository.createPlan(
                name = remotePlan.name,
                notes = remotePlan.description
            )

            remotePlan.exercises
                .sortedBy { it.orderIndex }
                .forEachIndexed { idx, remoteEx ->
                    val localExId = exerciseRepository.findOrCreateByName(
                        name = remoteEx.exercise?.name ?: "Unknown",
                        category = remoteEx.exercise?.category
                    )

                    val planExId = planRepository.addExerciseToPlan(
                        planId = localPlanId,
                        exerciseId = localExId,
                        orderIndex = idx
                    )

                    remoteEx.sets.sortedBy { it.setNumber }.forEach { remoteSet ->
                        planRepository.addSetToExercise(
                            planExerciseId = planExId,
                            setNumber = remoteSet.setNumber,
                            loadKg = remoteSet.loadKg,
                            targetReps = remoteSet.reps
                        )
                    }
                }

            localPlanId
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(syncError = "Plan sync failed: ${e.message}")
            null
        }
    }
}
