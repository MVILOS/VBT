package com.vbt.app.ui.screen.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.remote.ApiService
import com.vbt.app.data.remote.RepResultDto
import com.vbt.app.data.remote.UpdateRepRequest
import com.vbt.app.data.remote.WorkoutSessionDto
import com.vbt.app.domain.usecase.Estimate1RMUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionDetailUiState(
    val session: WorkoutSessionDto? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val apiService: ApiService,
    private val estimate1RM: Estimate1RMUseCase
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

    /** Poprawia ciężar dla wszystkich powtórzeń danej serii (np. źle wpisany kg). */
    fun updateSetWeight(setNumber: Int, newLoadKg: Double) {
        val reps = _uiState.value.session?.reps?.filter { it.setNumber == setNumber } ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                reps.forEach { rep ->
                    val repId = rep.id ?: return@forEach
                    val new1rm = estimate1RM.estimateFromVelocity(newLoadKg.toFloat(), rep.meanVelocity.toFloat())
                    apiService.updateRep(
                        sessionId,
                        repId,
                        UpdateRepRequest(loadKg = newLoadKg, estimated1rm = new1rm)
                    )
                }
                refreshAfterEdit()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = "Błąd zapisu ciężaru: ${e.message}")
            }
        }
    }

    /** Usuwa pojedyncze błędne powtórzenie (np. fałszywy rep od pociągnięcia linki). */
    fun deleteRep(repId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                apiService.deleteRep(sessionId, repId)
                refreshAfterEdit()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = "Błąd usuwania powtórzenia: ${e.message}")
            }
        }
    }

    /**
     * Scala serię [setNumber] z poprzednią (np. gdy zapomniano nacisnąć "kolejna
     * seria" i dwie serie wylądowały jako jedna / a powinny być rozdzielone
     * inaczej) - przenumerowuje jej powtórzenia pod poprzednią serię, kontynuując
     * numerację rep_number.
     */
    fun mergeSetWithPrevious(setNumber: Int) {
        val session = _uiState.value.session ?: return
        val reps = session.reps ?: return
        if (setNumber <= 1) return
        val targetSet = setNumber - 1
        val repsToMove = reps.filter { it.setNumber == setNumber }.sortedBy { it.repNumber }
        if (repsToMove.isEmpty()) return
        val startingRepNumber = (reps.filter { it.setNumber == targetSet }.maxOfOrNull { it.repNumber } ?: 0) + 1

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                repsToMove.forEachIndexed { index, rep ->
                    val repId = rep.id ?: return@forEachIndexed
                    apiService.updateRep(
                        sessionId,
                        repId,
                        UpdateRepRequest(setNumber = targetSet, repNumber = startingRepNumber + index)
                    )
                }
                refreshAfterEdit()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = "Błąd scalania serii: ${e.message}")
            }
        }
    }

    /**
     * Rozdziela serię: powtórzenia [setNumber] od [fromRepNumber] w górę
     * przechodzą do nowej serii (kolejny wolny numer) z ciężarem [newLoadKg]
     * i numeracją od 1. Odwrotność mergeSetWithPrevious - np. gdy urządzenie
     * skleiło dwie fizyczne serie, bo zmieniono obciążenie bez naciśnięcia
     * "kolejna seria" (sesja 105: powt. 5-6 "serii 6" to była seria na 150 kg).
     * 1RM przeliczane z nowego ciężaru; moc przelicza serwer (m*g*v_peak).
     */
    fun splitSetFromRep(setNumber: Int, fromRepNumber: Int, newLoadKg: Double) {
        val reps = _uiState.value.session?.reps ?: return
        val repsToMove = reps.filter { it.setNumber == setNumber && it.repNumber >= fromRepNumber }
            .sortedBy { it.repNumber }
        if (repsToMove.isEmpty()) return
        val newSetNumber = (reps.maxOfOrNull { it.setNumber } ?: setNumber) + 1

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                repsToMove.forEachIndexed { index, rep ->
                    val repId = rep.id ?: return@forEachIndexed
                    apiService.updateRep(
                        sessionId,
                        repId,
                        UpdateRepRequest(
                            setNumber = newSetNumber,
                            repNumber = index + 1,
                            loadKg = newLoadKg,
                            estimated1rm = estimate1RM.estimateFromVelocity(
                                newLoadKg.toFloat(), rep.meanVelocity.toFloat()
                            )
                        )
                    )
                }
                refreshAfterEdit()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = "Błąd rozdzielania serii: ${e.message}")
            }
        }
    }

    private suspend fun refreshAfterEdit() {
        val response = apiService.getSession(sessionId)
        if (response.isSuccessful && response.body() != null) {
            _uiState.value = _uiState.value.copy(session = response.body(), isSaving = false, error = null)
        } else {
            _uiState.value = _uiState.value.copy(isSaving = false, error = "Błąd: ${response.code()}")
        }
    }
}
