package com.vbt.app.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.local.entity.WorkoutSessionEntity
import com.vbt.app.data.remote.ApiService
import com.vbt.app.data.remote.UserDto
import com.vbt.app.data.remote.WorkoutSessionDto
import com.vbt.app.data.repository.AuthRepository
import com.vbt.app.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val sessions: List<WorkoutSessionDto> = emptyList(),
    // Local sessions that are still active (interrupted, not yet finished/discarded)
    val activeSessions: List<WorkoutSessionEntity> = emptyList(),
    // Sesje zakończone lokalnie, ale jeszcze nie wysłane na serwer
    val unsyncedSessions: List<WorkoutSessionEntity> = emptyList(),
    val athletes: List<UserDto> = emptyList(),
    val selectedAthleteId: Int? = null,
    val isCoach: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val apiService: ApiService,
    private val authRepository: AuthRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        checkUserRole()
        loadSessions()
        loadActiveSessions()
    }

    private fun checkUserRole() {
        viewModelScope.launch {
            authRepository.getCurrentRole().collect { role ->
                val isCoach = role == "coach"
                _uiState.value = _uiState.value.copy(isCoach = isCoach)
                if (isCoach) {
                    loadAthletes()
                }
            }
        }
    }

    private fun loadAthletes() {
        viewModelScope.launch {
            try {
                val response = apiService.getAthletes()
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = _uiState.value.copy(athletes = response.body() ?: emptyList())
                }
            } catch (_: Exception) {}
        }
    }

    fun loadSessions() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val response = if (_uiState.value.selectedAthleteId != null) {
                    apiService.getSessions(athleteId = _uiState.value.selectedAthleteId)
                } else {
                    // For coaches: server now returns all athletes' sessions by default
                    apiService.getSessions()
                }

                if (response.isSuccessful && response.body() != null) {
                    val sessions = response.body() ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        sessions = sessions.sortedByDescending { it.startedAt },
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Błąd: ${response.code()}",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Błąd ładowania sesji: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    private fun loadActiveSessions() {
        viewModelScope.launch {
            try {
                val active = workoutRepository.getActiveSessions()
                val unsynced = workoutRepository.getUnsyncedSessions()
                _uiState.value = _uiState.value.copy(
                    activeSessions = active,
                    unsyncedSessions = unsynced
                )
            } catch (_: Exception) {}
        }
    }

    fun filterByAthlete(athleteId: Int?) {
        _uiState.value = _uiState.value.copy(selectedAthleteId = athleteId)
        loadSessions()
    }

    fun deleteActiveSession(sessionId: Long) {
        viewModelScope.launch {
            workoutRepository.deleteSession(sessionId)
            loadActiveSessions()
        }
    }

    fun deleteSyncedSession(sessionId: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteSession(sessionId)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        sessions = _uiState.value.sessions.filterNot { it.id == sessionId }
                    )
                } else {
                    _uiState.value = _uiState.value.copy(error = "Nie udało się usunąć sesji: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Błąd usuwania sesji: ${e.message}")
            }
        }
    }
}
