package com.vbt.app.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, error = null)
    }

    fun register(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Wypełnij wszystkie pola")
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.value = state.copy(error = "Hasła nie są identyczne")
            return
        }
        if (state.password.length < 6) {
            _uiState.value = state.copy(error = "Hasło musi mieć co najmniej 6 znaków")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            val result = authRepository.register(state.username, state.password)
            result.fold(
                onSuccess = {
                    _uiState.value = RegisterUiState()
                    onSuccess()
                },
                onFailure = { exception ->
                    _uiState.value = state.copy(
                        isLoading = false,
                        error = exception.message ?: "Rejestracja nie powiodła się"
                    )
                }
            )
        }
    }
}
