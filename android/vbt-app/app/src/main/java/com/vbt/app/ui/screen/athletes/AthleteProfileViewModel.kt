package com.vbt.app.ui.screen.athletes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.remote.ApiService
import com.vbt.app.data.remote.CalendarEntryDto
import com.vbt.app.data.remote.CreateCalendarEntryRequest
import com.vbt.app.data.remote.TrainingPlanDto
import com.vbt.app.data.remote.UpdateCalendarEntryRequest
import com.vbt.app.data.remote.UserDto
import com.vbt.app.data.remote.WorkoutSessionDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class AthleteProfileTab {
    CALENDAR, PLANS, SESSIONS
}

data class AthleteProfileUiState(
    val athlete: UserDto? = null,
    val calendarEntries: List<CalendarEntryDto> = emptyList(),
    val assignedPlans: List<TrainingPlanDto> = emptyList(),
    val allPlans: List<TrainingPlanDto> = emptyList(),
    val sessions: List<WorkoutSessionDto> = emptyList(),
    val selectedTab: AthleteProfileTab = AthleteProfileTab.CALENDAR,
    val isLoading: Boolean = false,
    val error: String? = null,
    // Calendar entry modal
    val showEntryModal: Boolean = false,
    val editingEntry: CalendarEntryDto? = null,
    val entryDate: String = "",
    val entryTitle: String = "",
    val entryPlanId: Int? = null,
    val entryTimeSlot: String = "",
    val entryNotes: String = "",
    // Assign plan modal
    val showAssignModal: Boolean = false,
    val selectedPlanToAssign: Int? = null
)

@HiltViewModel
class AthleteProfileViewModel @Inject constructor(
    private val apiService: ApiService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val athleteId: Int = savedStateHandle.get<Int>("athleteId") ?: 0

    private val _uiState = MutableStateFlow(AthleteProfileUiState())
    val uiState: StateFlow<AthleteProfileUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Load athlete, plans, and sessions in parallel
                val athleteResponse = apiService.getAthlete(athleteId)
                val plansResponse = apiService.getPlans()
                val sessionsResponse = apiService.getSessions(athleteId)
                val calendarResponse = apiService.getCalendarEntries(athleteId = athleteId)

                if (athleteResponse.isSuccessful && plansResponse.isSuccessful &&
                    sessionsResponse.isSuccessful && calendarResponse.isSuccessful) {

                    val athlete = athleteResponse.body()
                    val allPlans = plansResponse.body() ?: emptyList()
                    val assignedPlans = allPlans.filter { it.assignedTo == athleteId }
                    val sessions = sessionsResponse.body() ?: emptyList()
                    val calendarEntries = calendarResponse.body() ?: emptyList()

                    _uiState.value = _uiState.value.copy(
                        athlete = athlete,
                        allPlans = allPlans,
                        assignedPlans = assignedPlans,
                        sessions = sessions,
                        calendarEntries = calendarEntries,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Nie udało się załadować danych",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Błąd: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun selectTab(tab: AthleteProfileTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun openAddEntry(date: String) {
        _uiState.value = _uiState.value.copy(
            showEntryModal = true,
            editingEntry = null,
            entryDate = date,
            entryTitle = "",
            entryPlanId = null,
            entryTimeSlot = "",
            entryNotes = ""
        )
    }

    fun openEditEntry(entry: CalendarEntryDto) {
        _uiState.value = _uiState.value.copy(
            showEntryModal = true,
            editingEntry = entry,
            entryDate = entry.date,
            entryTitle = entry.title,
            entryPlanId = entry.planId,
            entryTimeSlot = entry.timeSlot ?: "",
            entryNotes = entry.notes ?: ""
        )
    }

    fun closeEntryModal() {
        _uiState.value = _uiState.value.copy(
            showEntryModal = false,
            editingEntry = null,
            entryDate = "",
            entryTitle = "",
            entryPlanId = null,
            entryTimeSlot = "",
            entryNotes = ""
        )
    }

    fun updateEntryDate(date: String) {
        _uiState.value = _uiState.value.copy(entryDate = date)
    }

    fun updateEntryTitle(title: String) {
        _uiState.value = _uiState.value.copy(entryTitle = title)
    }

    fun updateEntryPlanId(planId: Int?) {
        _uiState.value = _uiState.value.copy(entryPlanId = planId)
    }

    fun updateEntryTimeSlot(timeSlot: String) {
        _uiState.value = _uiState.value.copy(entryTimeSlot = timeSlot)
    }

    fun updateEntryNotes(notes: String) {
        _uiState.value = _uiState.value.copy(entryNotes = notes)
    }

    fun saveEntry() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.entryDate.isEmpty() || state.entryTitle.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    error = "Data i tytuł są wymagane"
                )
                return@launch
            }

            try {
                val entry = state.editingEntry
                if (entry != null) {
                    // Update existing entry
                    val request = UpdateCalendarEntryRequest(
                        title = state.entryTitle,
                        notes = state.entryNotes.ifEmpty { null },
                        date = state.entryDate,
                        timeSlot = state.entryTimeSlot.ifEmpty { null },
                        status = entry.status,
                        planId = state.entryPlanId
                    )
                    val response = apiService.updateCalendarEntry(entry.id, request)
                    if (response.isSuccessful) {
                        closeEntryModal()
                        loadAll()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Nie udało się zaktualizować wpisu: ${response.code()}"
                        )
                    }
                } else {
                    // Create new entry
                    val request = CreateCalendarEntryRequest(
                        athleteId = athleteId,
                        planId = state.entryPlanId,
                        date = state.entryDate,
                        timeSlot = state.entryTimeSlot.ifEmpty { null },
                        title = state.entryTitle,
                        notes = state.entryNotes.ifEmpty { null }
                    )
                    val response = apiService.createCalendarEntry(request)
                    if (response.isSuccessful) {
                        closeEntryModal()
                        loadAll()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Nie udało się utworzyć wpisu: ${response.code()}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Błąd: ${e.message}"
                )
            }
        }
    }

    fun deleteEntry(id: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteCalendarEntry(id)
                if (response.isSuccessful) {
                    closeEntryModal()
                    loadAll()
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Nie udało się usunąć wpisu: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Błąd: ${e.message}"
                )
            }
        }
    }

    fun markStatus(id: Int, status: String) {
        viewModelScope.launch {
            try {
                val request = UpdateCalendarEntryRequest(
                    status = status
                )
                val response = apiService.updateCalendarEntry(id, request)
                if (response.isSuccessful) {
                    loadAll()
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Nie udało się zaktualizować statusu: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Błąd: ${e.message}"
                )
            }
        }
    }

    fun showAssignModal() {
        _uiState.value = _uiState.value.copy(
            showAssignModal = true,
            selectedPlanToAssign = null
        )
    }

    fun hideAssignModal() {
        _uiState.value = _uiState.value.copy(
            showAssignModal = false,
            selectedPlanToAssign = null
        )
    }

    fun selectPlanToAssign(planId: Int) {
        _uiState.value = _uiState.value.copy(selectedPlanToAssign = planId)
    }

    fun assignPlan() {
        viewModelScope.launch {
            val planId = _uiState.value.selectedPlanToAssign ?: return@launch
            try {
                val response = apiService.assignPlanToAthlete(planId, athleteId)
                if (response.isSuccessful) {
                    hideAssignModal()
                    loadAll()
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Nie udało się przypisać planu: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Błąd: ${e.message}"
                )
            }
        }
    }
}
