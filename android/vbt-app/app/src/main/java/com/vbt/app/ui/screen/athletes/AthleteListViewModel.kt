package com.vbt.app.ui.screen.athletes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.local.PreferencesManager
import com.vbt.app.data.remote.AdminAssignRequest
import com.vbt.app.data.remote.ApiService
import com.vbt.app.data.remote.AssignByUsernameRequest
import com.vbt.app.data.remote.CreateAthleteRequest
import com.vbt.app.data.remote.UserDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AthleteListUiState(
    val athletes: List<UserDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val userRole: String = "",
    // Tworzenie nowego zawodnika
    val showCreateDialog: Boolean = false,
    val newAthleteUsername: String = "",
    val newAthleteEmail: String = "",
    val newAthletePassword: String = "",
    // Przypisanie istniejącego użytkownika (np. innego trenera)
    val showAssignDialog: Boolean = false,
    val assignUsername: String = "",
    val assignSuccess: String? = null,
    // Admin: przypisz do konkretnego trenera
    val showAdminAssignDialog: Boolean = false,
    val adminAthleteUsername: String = "",
    val adminCoachUsername: String = ""
)

@HiltViewModel
class AthleteListViewModel @Inject constructor(
    private val apiService: ApiService,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AthleteListUiState())
    val uiState: StateFlow<AthleteListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val role = preferencesManager.getRole().first() ?: ""
            _uiState.value = _uiState.value.copy(userRole = role)
        }
        loadAthletes()
    }

    fun loadAthletes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.getAthletes()
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        athletes = response.body() ?: emptyList(),
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Błąd ładowania: ${response.code()}",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Błąd: ${e.message}", isLoading = false)
            }
        }
    }

    // ── Tworzenie nowego konta zawodnika ──────────────────────────────────────

    fun showCreateDialog() { _uiState.value = _uiState.value.copy(showCreateDialog = true) }

    fun hideCreateDialog() {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = false,
            newAthleteUsername = "", newAthleteEmail = "", newAthletePassword = ""
        )
    }

    fun updateNewAthleteUsername(v: String) { _uiState.value = _uiState.value.copy(newAthleteUsername = v) }
    fun updateNewAthleteEmail(v: String)    { _uiState.value = _uiState.value.copy(newAthleteEmail = v) }
    fun updateNewAthletePassword(v: String) { _uiState.value = _uiState.value.copy(newAthletePassword = v) }

    fun createAthlete() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.newAthleteUsername.isEmpty() || state.newAthletePassword.isEmpty()) {
                _uiState.value = state.copy(error = "Nazwa użytkownika i hasło są wymagane")
                return@launch
            }
            try {
                val response = apiService.createAthlete(
                    CreateAthleteRequest(
                        username = state.newAthleteUsername,
                        email = state.newAthleteEmail.ifBlank { null },
                        password = state.newAthletePassword
                    )
                )
                if (response.isSuccessful) {
                    _uiState.value = state.copy(
                        showCreateDialog = false,
                        newAthleteUsername = "", newAthleteEmail = "", newAthletePassword = ""
                    )
                    loadAthletes()
                } else {
                    _uiState.value = state.copy(error = "Błąd tworzenia: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Błąd: ${e.message}")
            }
        }
    }

    // ── Przypisywanie istniejącego użytkownika ────────────────────────────────

    fun showAssignDialog() { _uiState.value = _uiState.value.copy(showAssignDialog = true, assignUsername = "") }

    fun hideAssignDialog() {
        _uiState.value = _uiState.value.copy(showAssignDialog = false, assignUsername = "", assignSuccess = null)
    }

    fun updateAssignUsername(v: String) { _uiState.value = _uiState.value.copy(assignUsername = v) }

    fun assignByUsername() {
        viewModelScope.launch {
            val username = _uiState.value.assignUsername.trim()
            if (username.isEmpty()) return@launch
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = apiService.assignUserByUsername(AssignByUsernameRequest(username))
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        showAssignDialog = false,
                        assignUsername = "",
                        assignSuccess = "Użytkownik '$username' przypisany",
                        isLoading = false
                    )
                    loadAthletes()
                } else {
                    val msg = when (response.code()) {
                        404 -> "Nie znaleziono użytkownika '$username'"
                        400 -> "Ten użytkownik jest już na Twojej liście"
                        else -> "Błąd: ${response.code()}"
                    }
                    _uiState.value = _uiState.value.copy(error = msg, isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Błąd: ${e.message}", isLoading = false)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, assignSuccess = null)
    }

    // ── Admin: przypisz zawodnika do dowolnego trenera ────────────────────────

    fun showAdminAssignDialog() {
        _uiState.value = _uiState.value.copy(
            showAdminAssignDialog = true, adminAthleteUsername = "", adminCoachUsername = ""
        )
    }

    fun hideAdminAssignDialog() {
        _uiState.value = _uiState.value.copy(showAdminAssignDialog = false)
    }

    fun updateAdminAthleteUsername(v: String) { _uiState.value = _uiState.value.copy(adminAthleteUsername = v) }
    fun updateAdminCoachUsername(v: String) { _uiState.value = _uiState.value.copy(adminCoachUsername = v) }

    fun adminAssignToCoach() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.adminAthleteUsername.isBlank() || state.adminCoachUsername.isBlank()) return@launch
            _uiState.value = state.copy(isLoading = true)
            try {
                val response = apiService.adminAssignToCoach(
                    AdminAssignRequest(state.adminAthleteUsername.trim(), state.adminCoachUsername.trim())
                )
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        showAdminAssignDialog = false,
                        assignSuccess = "'${state.adminAthleteUsername}' przypisany do trenera '${state.adminCoachUsername}'",
                        isLoading = false
                    )
                    loadAthletes()
                } else {
                    val msg = when (response.code()) {
                        404 -> "Nie znaleziono użytkownika"
                        400 -> "Przypisanie już istnieje"
                        else -> "Błąd: ${response.code()}"
                    }
                    _uiState.value = _uiState.value.copy(error = msg, isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Błąd: ${e.message}", isLoading = false)
            }
        }
    }
}
