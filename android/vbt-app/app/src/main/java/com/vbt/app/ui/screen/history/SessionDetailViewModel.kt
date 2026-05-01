package com.vbt.app.ui.screen.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.remote.ApiService
import com.vbt.app.data.remote.RepResultDto
import com.vbt.app.data.remote.WorkoutSessionDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionDetailUiState(
    val session: WorkoutSessionDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val apiService: ApiService
) : ViewModel() {

    private val sessionId: Int = savedStateHandle.get<Int>("sessionId") ?: 0

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
        loadSession(sessionId)
    }

    fun loadSession(id: Int) {
        viewModelScope.launch {
            try {
                _uiState.value = SessionDetailUiState(isLoading = true)
                val response = apiService.getSession(id)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = SessionDetailUiState(session = response.body())
                } else {
                    _uiState.value = SessionDetailUiState(
                        error = "Błąd: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = SessionDetailUiState(
                    error = "Błąd ładowania szczegółów sesji: ${e.message}"
                )
            }
        }
    }
}
