package com.vbt.app.ui.screen.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.remote.*
import com.vbt.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyticsUiState(
    // Wspólne
    val exercises: List<ExerciseDto> = emptyList(),
    val selectedExerciseId: Int? = null,
    val athleteId: Int? = null,

    // Wybór zawodnika (tylko dla coacha/admina)
    val athletes: List<UserDto> = emptyList(),
    val selectedAthleteId: Int? = null,
    val isCoach: Boolean = false,

    // Zakładka: Prędkość
    val velocityTrend: List<VelocityTrendPointDto> = emptyList(),

    // Zakładka: 1RM
    val oneRmProgress: List<OneRmProgressPointDto> = emptyList(),

    // Zakładka: Zmęczenie (fatigue-index)
    val recentSessions: List<WorkoutSessionDto> = emptyList(),
    val selectedSessionId: Int? = null,
    val fatigueData: List<FatigueIndexDto> = emptyList(),

    // Zakładka: Tygodnie (week-comparison + weekly-load)
    val weekComparison: List<WeekComparisonDto> = emptyList(),
    val weeklyLoad: List<WeeklyLoadDto> = emptyList(),

    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val apiService: ApiService,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                val role = authRepository.getCurrentRole().firstOrNull()
                val userId = authRepository.getCurrentUserId().firstOrNull()
                val isCoach = role == "coach" || role == "admin"
                val athleteId = if (!isCoach) userId else null

                val exercisesResp = apiService.getExercises()

                if (isCoach) {
                    // Załaduj listę zawodników coacha
                    val athletesResp = apiService.getAthletes()
                    val athletes = athletesResp.body() ?: emptyList()
                    val firstAthleteId = athletes.firstOrNull()?.id

                    val sessionsResp = firstAthleteId?.let { apiService.getSessions(athleteId = it) }

                    _uiState.value = _uiState.value.copy(
                        exercises = exercisesResp.body()?.sortedWith(
                            compareBy({ categoryOrder(it.category) }, { it.name })
                        ) ?: emptyList(),
                        athletes = athletes,
                        selectedAthleteId = firstAthleteId,
                        isCoach = true,
                        recentSessions = sessionsResp?.body()
                            ?.sortedByDescending { it.startedAt }
                            ?.take(20) ?: emptyList(),
                        athleteId = null
                    )

                    loadWeeklyLoad(firstAthleteId)
                } else {
                    val sessionsResp = apiService.getSessions(athleteId = athleteId)
                    _uiState.value = _uiState.value.copy(
                        exercises = exercisesResp.body()?.sortedWith(
                            compareBy({ categoryOrder(it.category) }, { it.name })
                        ) ?: emptyList(),
                        recentSessions = sessionsResp.body()
                            ?.sortedByDescending { it.startedAt }
                            ?.take(20) ?: emptyList(),
                        athleteId = athleteId,
                        isCoach = false
                    )
                    loadWeeklyLoad(athleteId)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun selectAthlete(athleteId: Int) {
        _uiState.value = _uiState.value.copy(
            selectedAthleteId = athleteId,
            selectedExerciseId = null,
            selectedSessionId = null,
            velocityTrend = emptyList(),
            oneRmProgress = emptyList(),
            fatigueData = emptyList(),
            weekComparison = emptyList()
        )
        viewModelScope.launch {
            try {
                val sessionsResp = apiService.getSessions(athleteId = athleteId)
                _uiState.value = _uiState.value.copy(
                    recentSessions = sessionsResp.body()
                        ?.sortedByDescending { it.startedAt }
                        ?.take(20) ?: emptyList()
                )
            } catch (_: Exception) {}
        }
        loadWeeklyLoad(athleteId)
    }

    private fun categoryOrder(cat: String?): Int = when (cat) {
        "olympic" -> 0
        "strength" -> 1
        "ballistic" -> 2
        "auxiliary" -> 3
        else -> 4
    }

    fun selectExercise(exerciseId: Int) {
        _uiState.value = _uiState.value.copy(selectedExerciseId = exerciseId)
        loadVelocityAndORM(exerciseId)
        loadWeekComparison(exerciseId)
    }

    private fun effectiveAthleteId(): Int? {
        val s = _uiState.value
        return if (s.isCoach) s.selectedAthleteId else s.athleteId
    }

    private fun loadVelocityAndORM(exerciseId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val athleteId = effectiveAthleteId()
                val velResp = apiService.getVelocityTrend(athleteId = athleteId, exerciseId = exerciseId, days = 90)
                val ormResp = apiService.get1rmProgress(athleteId = athleteId, exerciseId = exerciseId)
                _uiState.value = _uiState.value.copy(
                    velocityTrend = velResp.body() ?: emptyList(),
                    oneRmProgress = ormResp.body() ?: emptyList(),
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun selectSession(sessionId: Int) {
        _uiState.value = _uiState.value.copy(selectedSessionId = sessionId)
        loadFatigueIndex(sessionId)
    }

    private fun loadFatigueIndex(sessionId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val athleteId = effectiveAthleteId()
                val resp = apiService.getFatigueIndex(
                    sessionId = sessionId,
                    athleteId = athleteId,
                    exerciseId = _uiState.value.selectedExerciseId
                )
                _uiState.value = _uiState.value.copy(
                    fatigueData = resp.body() ?: emptyList(),
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    private fun loadWeeklyLoad(athleteId: Int?) {
        viewModelScope.launch {
            try {
                val resp = apiService.getWeeklyLoad(athleteId = athleteId, weeks = 8)
                _uiState.value = _uiState.value.copy(weeklyLoad = resp.body() ?: emptyList())
            } catch (_: Exception) {}
        }
    }

    private fun loadWeekComparison(exerciseId: Int) {
        viewModelScope.launch {
            try {
                val athleteId = effectiveAthleteId()
                val resp = apiService.getWeekComparison(
                    exerciseId = exerciseId,
                    athleteId = athleteId,
                    weeks = 8
                )
                _uiState.value = _uiState.value.copy(weekComparison = resp.body() ?: emptyList())
            } catch (_: Exception) {}
        }
    }
}
