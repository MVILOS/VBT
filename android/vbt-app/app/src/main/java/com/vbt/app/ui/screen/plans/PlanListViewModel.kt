package com.vbt.app.ui.screen.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.remote.ApiService
import com.vbt.app.data.remote.TrainingPlanDto
import com.vbt.app.data.remote.UserDto
import com.vbt.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanListUiState(
    val plans: List<TrainingPlanDto> = emptyList(),
    val athletes: List<UserDto> = emptyList(),
    val isCoach: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PlanListViewModel @Inject constructor(
    private val apiService: ApiService,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanListUiState())
    val uiState: StateFlow<PlanListUiState> = _uiState.asStateFlow()

    init {
        loadPlans()
    }

    fun loadPlans() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                authRepository.getCurrentRole().collect { role ->
                    val isCoach = role == "coach"
                    _uiState.value = _uiState.value.copy(isCoach = isCoach)

                    val plansResponse = apiService.getPlans()
                    if (plansResponse.isSuccessful) {
                        val plans = plansResponse.body() ?: emptyList()

                        if (isCoach) {
                            val athletesResponse = apiService.getAthletes()
                            val athletes = if (athletesResponse.isSuccessful) {
                                athletesResponse.body() ?: emptyList()
                            } else {
                                emptyList()
                            }
                            _uiState.value = _uiState.value.copy(
                                plans = plans,
                                athletes = athletes,
                                isLoading = false
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                plans = plans,
                                isLoading = false
                            )
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to load plans: ${plansResponse.code()}",
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun deletePlan(planId: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.deletePlan(planId)
                if (response.isSuccessful) {
                    loadPlans()
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to delete plan: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error deleting plan: ${e.message}"
                )
            }
        }
    }

    fun assignPlan(planId: Int, athleteId: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.assignPlanToAthlete(planId, athleteId)
                if (response.isSuccessful) {
                    loadPlans()
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to assign plan: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error assigning plan: ${e.message}"
                )
            }
        }
    }
}
