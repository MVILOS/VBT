package com.vbt.app.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.remote.ApiService
import com.vbt.app.data.remote.CalendarEntryDto
import com.vbt.app.data.remote.DashboardStatsDto
import com.vbt.app.data.remote.RecentSessionDto
import com.vbt.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class HomeUiState(
    val username: String = "",
    val role: String = "athlete",
    val isBleConnected: Boolean = false,
    val todayEntries: List<CalendarEntryDto> = emptyList(),
    val dashboardStats: DashboardStatsDto? = null,
    val recentSessions: List<RecentSessionDto> = emptyList(),
    val isLoading: Boolean = false,
    // Ustawiane, gdy którekolwiek zapytanie do serwera (kalendarz/statystyki/
    // ostatnie sesje) nie powiodło się z powodu braku sieci - dashboard i tak
    // pokazuje to, co udało się wczytać wcześniej/lokalnie, ale użytkownik musi
    // wiedzieć, że dane mogą być nieaktualne zamiast po cichu widzieć puste karty.
    val offlineNotice: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val isCoach: Boolean
        get() = _uiState.value.role == "coach"

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                combine(
                    authRepository.getCurrentUsername(),
                    authRepository.getCurrentRole()
                ) { username, role ->
                    Pair(username ?: "", role ?: "athlete")
                }.collect { (username, role) ->
                    _uiState.value = _uiState.value.copy(
                        username = username,
                        role = role
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            var anyNetworkFailure = false

            val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
            try {
                val calendarResponse = apiService.getCalendarEntries(dateStart = today, dateEnd = today)
                if (calendarResponse.isSuccessful) {
                    _uiState.value = _uiState.value.copy(todayEntries = calendarResponse.body() ?: emptyList())
                } else {
                    anyNetworkFailure = true
                }
            } catch (e: Exception) {
                anyNetworkFailure = true
                e.printStackTrace()
            }

            try {
                val statsResponse = apiService.getDashboardStats()
                if (statsResponse.isSuccessful) {
                    _uiState.value = _uiState.value.copy(dashboardStats = statsResponse.body())
                } else {
                    anyNetworkFailure = true
                }
            } catch (e: Exception) {
                anyNetworkFailure = true
                e.printStackTrace()
            }

            try {
                val recentResponse = apiService.getRecentSessions(limit = 5)
                if (recentResponse.isSuccessful) {
                    _uiState.value = _uiState.value.copy(recentSessions = recentResponse.body() ?: emptyList())
                } else {
                    anyNetworkFailure = true
                }
            } catch (e: Exception) {
                anyNetworkFailure = true
                e.printStackTrace()
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                offlineNotice = if (anyNetworkFailure) "Brak połączenia z serwerem - dane mogą być nieaktualne" else null
            )
        }
    }

    fun updateBleStatus(connected: Boolean) {
        _uiState.value = _uiState.value.copy(isBleConnected = connected)
    }

    fun logout(onLogout: () -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.logout()
                onLogout()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
