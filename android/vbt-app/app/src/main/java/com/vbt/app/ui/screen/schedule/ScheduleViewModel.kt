package com.vbt.app.ui.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.remote.ApiService
import com.vbt.app.data.remote.CalendarEntryDto
import com.vbt.app.data.remote.CreateCalendarEntryRequest
import com.vbt.app.data.remote.TrainingPlanDto
import com.vbt.app.data.remote.UpdateCalendarEntryRequest
import com.vbt.app.data.remote.UserDto
import com.vbt.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ScheduleUiState(
    val entries: List<CalendarEntryDto> = emptyList(),
    val athletes: List<UserDto> = emptyList(),
    val plans: List<TrainingPlanDto> = emptyList(),
    val isCoach: Boolean = false,
    val weekStart: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY),
    val filterAthleteId: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddModal: Boolean = false,
    val editingEntryId: Int? = null,
    val formDate: String = "",
    val formAthleteId: Int? = null,
    val formPlanId: Int? = null,
    val formTitle: String = "",
    val formTimeSlot: String = "",
    val formNotes: String = "",
    val formStatus: String = "scheduled"
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val apiService: ApiService,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val isCoach = authRepository.getCurrentRole().first() == "coach"

                val currentWeekStart = _uiState.value.weekStart
                val weekEnd = currentWeekStart.plusDays(6)
                val dateStart = currentWeekStart.format(DateTimeFormatter.ISO_DATE)
                val dateEnd = weekEnd.format(DateTimeFormatter.ISO_DATE)

                // GET /calendar
                val entriesResponse = apiService.getCalendarEntries(
                    athleteId = if (isCoach) null else null,
                    dateStart = dateStart,
                    dateEnd = dateEnd
                )

                val entries = if (entriesResponse.isSuccessful) {
                    entriesResponse.body() ?: emptyList()
                } else {
                    emptyList()
                }

                // GET /users/athletes (for coach)
                val athletes = if (isCoach) {
                    val athletesResponse = apiService.getAthletes()
                    if (athletesResponse.isSuccessful) {
                        athletesResponse.body() ?: emptyList()
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                // GET /plans
                val plansResponse = apiService.getPlans()
                val plans = if (plansResponse.isSuccessful) {
                    plansResponse.body() ?: emptyList()
                } else {
                    emptyList()
                }

                _uiState.value = _uiState.value.copy(
                    entries = entries,
                    athletes = athletes,
                    plans = plans,
                    isCoach = isCoach,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Connection error: ${e.message}"
                )
            }
        }
    }

    fun prevWeek() {
        _uiState.value = _uiState.value.copy(
            weekStart = _uiState.value.weekStart.minusWeeks(1)
        )
        loadData()
    }

    fun nextWeek() {
        _uiState.value = _uiState.value.copy(
            weekStart = _uiState.value.weekStart.plusWeeks(1)
        )
        loadData()
    }

    fun goToToday() {
        _uiState.value = _uiState.value.copy(
            weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
        )
        loadData()
    }

    fun setAthleteFilter(athleteId: Int?) {
        _uiState.value = _uiState.value.copy(filterAthleteId = athleteId)
    }

    fun openAddModal(date: String) {
        _uiState.value = _uiState.value.copy(
            showAddModal = true,
            editingEntryId = null,
            formDate = date,
            formAthleteId = if (_uiState.value.isCoach) null else null,
            formPlanId = null,
            formTitle = "",
            formTimeSlot = "",
            formNotes = "",
            formStatus = "scheduled"
        )
    }

    fun openEditModal(entry: CalendarEntryDto) {
        _uiState.value = _uiState.value.copy(
            showAddModal = true,
            editingEntryId = entry.id,
            formDate = entry.date,
            formAthleteId = entry.athleteId,
            formPlanId = entry.planId,
            formTitle = entry.title,
            formTimeSlot = entry.timeSlot ?: "",
            formNotes = entry.notes ?: "",
            formStatus = entry.status
        )
    }

    fun closeModal() {
        _uiState.value = _uiState.value.copy(showAddModal = false)
    }

    fun updateFormDate(date: String) {
        _uiState.value = _uiState.value.copy(formDate = date)
    }

    fun updateFormAthleteId(athleteId: Int?) {
        _uiState.value = _uiState.value.copy(formAthleteId = athleteId)
    }

    fun updateFormPlanId(planId: Int?) {
        _uiState.value = _uiState.value.copy(formPlanId = planId)
    }

    fun updateFormTitle(title: String) {
        _uiState.value = _uiState.value.copy(formTitle = title)
    }

    fun updateFormTimeSlot(timeSlot: String) {
        _uiState.value = _uiState.value.copy(formTimeSlot = timeSlot)
    }

    fun updateFormNotes(notes: String) {
        _uiState.value = _uiState.value.copy(formNotes = notes)
    }

    fun updateFormStatus(status: String) {
        _uiState.value = _uiState.value.copy(formStatus = status)
    }

    fun saveEntry() {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val editingId = state.editingEntryId

                if (editingId != null) {
                    // PUT /calendar/{id}
                    val updateRequest = UpdateCalendarEntryRequest(
                        title = state.formTitle,
                        notes = state.formNotes.ifBlank { null },
                        date = state.formDate,
                        timeSlot = state.formTimeSlot.ifBlank { null },
                        status = state.formStatus,
                        planId = state.formPlanId
                    )
                    val response = apiService.updateCalendarEntry(editingId, updateRequest)
                    if (response.isSuccessful) {
                        _uiState.value = _uiState.value.copy(showAddModal = false)
                        loadData()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to update entry (${response.code()})"
                        )
                    }
                } else {
                    // POST /calendar
                    val createRequest = CreateCalendarEntryRequest(
                        athleteId = state.formAthleteId,
                        planId = state.formPlanId,
                        date = state.formDate,
                        timeSlot = state.formTimeSlot.ifBlank { null },
                        title = state.formTitle,
                        notes = state.formNotes.ifBlank { null }
                    )
                    val response = apiService.createCalendarEntry(createRequest)
                    if (response.isSuccessful) {
                        _uiState.value = _uiState.value.copy(showAddModal = false)
                        loadData()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to create entry (${response.code()})"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun deleteEntry(id: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteCalendarEntry(id)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(showAddModal = false)
                    loadData()
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to delete entry (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun markStatus(id: Int, status: String) {
        viewModelScope.launch {
            try {
                val updateRequest = UpdateCalendarEntryRequest(
                    status = status,
                    title = null,
                    notes = null,
                    date = null,
                    timeSlot = null,
                    planId = null
                )
                val response = apiService.updateCalendarEntry(id, updateRequest)
                if (response.isSuccessful) {
                    loadData()
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to update status (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error: ${e.message}"
                )
            }
        }
    }
}
